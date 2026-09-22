"""Build a non-destructive clean SPORT-shell candidate from the populated reference.

This deliberately uses no generative model, blur, or rectangular paint-over.  The populated
reference remains the base image.  Only pixel masks for live text, colored track fills, and the
active tachometer sector are changed.  A neutral, previously approved shell is used solely as a
locally color-matched donor through those masks.
"""

from __future__ import annotations

from pathlib import Path
import sys

import numpy as np
from PIL import Image


PROJECT = Path(__file__).resolve().parents[2]
REFERENCE = Path(r"C:\Users\css_g\Downloads\Civic\background_sport.png")
NEUTRAL_DONOR = PROJECT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "background_sport.png"
OUTPUT = Path(__file__).with_name("background_sport_new_reference_clean_candidate_v20.png")
MASK_OUTPUT = Path(__file__).with_name("background_sport_new_reference_clean_candidate_v20_mask.png")


def dilate(mask: np.ndarray, radius: int) -> np.ndarray:
    """Chebyshev dilation used only to include antialiased glyph/fill edges."""
    height, width = mask.shape
    result = mask.copy()
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            if dx == 0 and dy == 0:
                continue
            src_x0, src_x1 = max(0, -dx), min(width, width - dx)
            src_y0, src_y1 = max(0, -dy), min(height, height - dy)
            dst_x0, dst_x1 = max(0, dx), min(width, width + dx)
            dst_y0, dst_y1 = max(0, dy), min(height, height + dy)
            result[dst_y0:dst_y1, dst_x0:dst_x1] |= mask[src_y0:src_y1, src_x0:src_x1]
    return result


def luminance(image: np.ndarray) -> np.ndarray:
    return image[..., 0] * 0.2126 + image[..., 1] * 0.7152 + image[..., 2] * 0.0722


def glyph_mask(base: np.ndarray, box: tuple[int, int, int, int], threshold: float = 55.0) -> np.ndarray:
    """Select bright live glyphs inside a deliberately tight bounding box."""
    x0, y0, x1, y1 = box
    lum = luminance(base)
    core = np.zeros(base.shape[:2], dtype=bool)
    core[y0:y1 + 1, x0:x1 + 1] = lum[y0:y1 + 1, x0:x1 + 1] > threshold
    hue, saturation = hue_saturation(base)
    area = np.zeros(base.shape[:2], dtype=bool)
    area[y0:y1 + 1, x0:x1 + 1] = True
    # Capture the blue/cyan anti-alias glow only where it touches a bright numeral.  This avoids
    # converting a whole card/value rectangle into a donor patch.
    cyan_glow = area & (saturation > 0.13) & (lum > 10.0) & (hue >= 165.0) & (hue <= 250.0)
    # Include antialiased edge/glow but keep the repair tied to the literal glyph silhouette.
    result = core | (cyan_glow & dilate(core, 5))
    return dilate(result, 3)


def hue_saturation(image: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    rgb = image.astype(np.float32) / 255.0
    maximum = rgb.max(axis=2)
    minimum = rgb.min(axis=2)
    delta = maximum - minimum
    hue = np.zeros_like(maximum)
    nonzero = delta > 1e-6
    red = nonzero & (maximum == rgb[..., 0])
    green = nonzero & (maximum == rgb[..., 1])
    blue = nonzero & (maximum == rgb[..., 2])
    hue[red] = ((rgb[..., 1][red] - rgb[..., 2][red]) / delta[red]) % 6.0
    hue[green] = (rgb[..., 2][green] - rgb[..., 0][green]) / delta[green] + 2.0
    hue[blue] = (rgb[..., 0][blue] - rgb[..., 1][blue]) / delta[blue] + 4.0
    hue *= 60.0
    saturation = np.zeros_like(maximum)
    saturation[maximum > 1e-6] = delta[maximum > 1e-6] / maximum[maximum > 1e-6]
    return hue, saturation


def shifted_donor(donor: np.ndarray, dx: int, dy: int) -> np.ndarray:
    """Return donor pixels at (x + dx, y + dy), clamped safely at image boundaries."""
    height, width = donor.shape[:2]
    ys = np.clip(np.arange(height) + dy, 0, height - 1)
    xs = np.clip(np.arange(width) + dx, 0, width - 1)
    return donor[ys[:, None], xs[None, :]]


def transplant_with_perimeter_match(
    out: np.ndarray,
    base: np.ndarray,
    donor: np.ndarray,
    mask: np.ndarray,
    dx: int,
    dy: int,
) -> None:
    """Copy a textured donor only through a glyph-shaped mask, color-matched at its perimeter."""
    if not mask.any():
        return
    mapped = shifted_donor(donor, dx, dy).astype(np.float32)
    perimeter = dilate(mask, 3) & ~mask
    if perimeter.any():
        shift = np.median(base[perimeter].astype(np.float32) - mapped[perimeter], axis=0)
    else:
        shift = np.zeros(3, dtype=np.float32)
    out[mask] = np.clip(mapped[mask] + shift, 0, 255).astype(np.uint8)


def inpaint_glyph_mask_from_local_texture(out: np.ndarray, base: np.ndarray, mask: np.ndarray) -> None:
    """Fill a glyph-shaped hole from the nearest dark pixels in its own local card texture.

    This is deterministic nearest-texture propagation, not blur or a painted rectangle.  Bright
    text/icon pixels are excluded from seed candidates, so a unit/label cannot be copied into the
    removed live value.  Each repaired pixel retains an actual neighbouring source pixel.
    """
    ys, xs = np.where(mask)
    if not len(xs):
        return
    margin = 14
    x0, x1 = max(0, int(xs.min()) - margin), min(base.shape[1] - 1, int(xs.max()) + margin)
    y0, y1 = max(0, int(ys.min()) - margin), min(base.shape[0] - 1, int(ys.max()) + margin)
    local_mask = mask[y0:y1 + 1, x0:x1 + 1]
    local_base = base[y0:y1 + 1, x0:x1 + 1]
    local_lum = luminance(local_base)
    # Only real dark card/background material starts a propagation path.  If a tight group has
    # none (not expected here), fall back to all unmasked local pixels rather than failing.
    resolved = ~local_mask & (local_lum < 68.0)
    if not resolved.any():
        resolved = ~local_mask
    height, width = resolved.shape
    source_y = np.full((height, width), -1, dtype=np.int32)
    source_x = np.full((height, width), -1, dtype=np.int32)
    grid_y, grid_x = np.indices((height, width))
    source_y[resolved] = grid_y[resolved]
    source_x[resolved] = grid_x[resolved]
    unresolved = local_mask.copy()
    directions = ((0, -1), (0, 1), (-1, 0), (1, 0), (-1, -1), (-1, 1), (1, -1), (1, 1))
    while unresolved.any():
        advanced = False
        for dy, dx in directions:
            ty0, ty1 = max(0, dy), min(height, height + dy)
            tx0, tx1 = max(0, dx), min(width, width + dx)
            sy0, sy1 = max(0, -dy), min(height, height - dy)
            sx0, sx1 = max(0, -dx), min(width, width - dx)
            target_unresolved = unresolved[ty0:ty1, tx0:tx1]
            source_resolved = resolved[sy0:sy1, sx0:sx1]
            take = target_unresolved & source_resolved
            if not take.any():
                continue
            target_y, target_x = np.where(take)
            target_y += ty0
            target_x += tx0
            source_y[target_y, target_x] = source_y[target_y - dy, target_x - dx]
            source_x[target_y, target_x] = source_x[target_y - dy, target_x - dx]
            resolved[target_y, target_x] = True
            unresolved[target_y, target_x] = False
            advanced = True
        if not advanced:
            # This cannot normally occur because there is a margin of known pixels, but avoids
            # an infinite loop if a malformed mask occupies its entire local crop.
            source_y[unresolved] = grid_y[unresolved]
            source_x[unresolved] = grid_x[unresolved]
            break
    sampled = local_base[source_y, source_x]
    local_out = out[y0:y1 + 1, x0:x1 + 1]
    local_out[local_mask] = sampled[local_mask]


def transplant_best_local_card_texture(
    out: np.ndarray,
    base: np.ndarray,
    mask: np.ndarray,
    card_bounds: tuple[int, int, int, int],
) -> None:
    """Copy a best-matching, texture-only source from the same card through a glyph mask.

    Candidate source positions are constrained to the card and rejected if they contain bright
    text, icons or hard edges.  The selected patch is scored against the glyph's 2px perimeter;
    no rectangular source area is ever composited onto the dashboard.
    """
    ys, xs = np.where(mask)
    if not len(xs):
        return
    card_left, card_top, card_right, card_bottom = card_bounds
    min_x, max_x = int(xs.min()), int(xs.max())
    min_y, max_y = int(ys.min()), int(ys.max())
    # A translated glyph must remain fully inside its own card/background domain.
    min_dx, max_dx = card_left - min_x, card_right - max_x
    min_dy, max_dy = card_top - min_y, card_bottom - max_y
    if min_dx > max_dx or min_dy > max_dy:
        return

    lum = luminance(base)
    horizontal = np.abs(np.diff(lum, axis=1, prepend=lum[:, :1]))
    vertical = np.abs(np.diff(lum, axis=0, prepend=lum[:1, :]))
    texture_safe = (lum < 64.0) & ((horizontal + vertical) < 28.0)
    ring = dilate(mask, 2) & ~mask
    ring_y, ring_x = np.where(ring)

    def score(dx: int, dy: int) -> float | None:
        source_y = ys + dy
        source_x = xs + dx
        # Requiring almost all source pixels to be quiet avoids pulling any surviving letter
        # stroke into the erased value field while allowing naturally noisy dark grain.
        if texture_safe[source_y, source_x].mean() < 0.985:
            return None
        source_ring_y = ring_y + dy
        source_ring_x = ring_x + dx
        if not len(source_ring_x):
            return 0.0
        # Card bounds were calculated from the full glyph, but the perimeter can extend slightly.
        valid = ((source_ring_x >= 0) & (source_ring_x < base.shape[1])
                 & (source_ring_y >= 0) & (source_ring_y < base.shape[0]))
        if not valid.any():
            return None
        delta = np.abs(base[ring_y[valid], ring_x[valid]].astype(np.int16)
                       - base[source_ring_y[valid], source_ring_x[valid]].astype(np.int16))
        return float(np.median(delta) + np.mean(delta) * 0.15)

    # Coarse scan first, then a pixel-accurate refinement around the best texture patch.
    best: tuple[float, int, int] | None = None
    for dy in range(min_dy, max_dy + 1, 3):
        for dx in range(min_dx, max_dx + 1, 3):
            candidate = score(dx, dy)
            if candidate is not None and (best is None or candidate < best[0]):
                best = (candidate, dx, dy)
    if best is not None:
        _, coarse_dx, coarse_dy = best
        for dy in range(max(min_dy, coarse_dy - 3), min(max_dy, coarse_dy + 3) + 1):
            for dx in range(max(min_dx, coarse_dx - 3), min(max_dx, coarse_dx + 3) + 1):
                candidate = score(dx, dy)
                if candidate is not None and candidate < best[0]:
                    best = (candidate, dx, dy)
    if best is None:
        # A fully textured card is unusual; use its darkest non-glyph pixel as a small fallback.
        card_lum = lum[card_top:card_bottom + 1, card_left:card_right + 1]
        yy, xx = np.unravel_index(np.argmin(card_lum), card_lum.shape)
        out[mask] = base[card_top + yy, card_left + xx]
        return
    _, dx, dy = best
    out[mask] = base[ys + dy, xs + dx]


def colored_track_mask(
    base: np.ndarray,
    seed_rect: tuple[int, int, int, int],
    body_rect: tuple[int, int, int, int],
) -> np.ndarray:
    """Mask the live fill as the actual full rail capsule, never as a rectangular patch."""
    x0, y0, x1, y1 = seed_rect
    hue, saturation = hue_saturation(base)
    lum = luminance(base)
    cyan_or_green = (hue >= 65.0) & (hue <= 230.0)
    yellow = (hue >= 35.0) & (hue <= 70.0)
    colored = (saturation > 0.26) & (lum > 50.0) & (cyan_or_green | yellow)
    area = np.zeros(base.shape[:2], dtype=bool)
    area[y0:y1 + 1, x0:x1 + 1] = True
    colored &= area
    # The fill has a nearly-white leading edge whose saturation is low.  Determine the live-fill
    # end from chromatic pixels, then include every interior pixel before that edge.
    columns = np.where(colored.any(axis=0))[0]
    if not len(columns):
        return np.zeros(base.shape[:2], dtype=bool)
    fill_end = min(x1, int(columns.max()) + 3)
    body_x0, body_y0, body_x1, body_y1 = body_rect
    return capsule_mask(base.shape[:2], (body_x0, body_y0, min(body_x1, fill_end), body_y1))


def capsule_mask(shape: tuple[int, int], rect: tuple[int, int, int, int]) -> np.ndarray:
    """Return the exact pill/capsule silhouette inside an axis-aligned bounds rectangle."""
    height, width = shape
    x0, y0, x1, y1 = rect
    ys, xs = np.indices((height, width))
    radius = max(0.5, (y1 - y0 + 1) * 0.5)
    cy = (y0 + y1) * 0.5
    left_cx, right_cx = x0 + radius, x1 - radius
    middle = (xs >= left_cx) & (xs <= right_cx) & (np.abs(ys - cy) <= radius)
    left = (xs - left_cx) ** 2 + (ys - cy) ** 2 <= radius ** 2
    right = (xs - right_cx) ** 2 + (ys - cy) ** 2 <= radius ** 2
    return (middle | left | right) & (xs >= x0) & (xs <= x1) & (ys >= y0) & (ys <= y1)


def neutralize_static_track_chroma(
    out: np.ndarray,
    base: np.ndarray,
    envelope: tuple[int, int, int, int],
    live_fill: np.ndarray,
) -> np.ndarray:
    """Desaturate only residual coloured rail pixels outside the live-fill capsule.

    The shape comes from actual coloured source pixels constrained to the track's pill envelope;
    this preserves every surrounding card pixel and avoids a painted erase rectangle.  Brightness
    is restrained so an old baked fill cannot read as a grey "progress" state when disconnected.
    """
    hue, saturation = hue_saturation(base)
    lum = luminance(base)
    envelope_mask = capsule_mask(base.shape[:2], envelope)
    # Red antialias pixels have hue close to 360°, while cyan/green/yellow occupy 0°..230°.
    cyan_green_yellow_red = (hue <= 230.0) | (hue >= 340.0)
    chroma = envelope_mask & ~live_fill & (saturation > 0.18) & (lum > 15.0) & cyan_green_yellow_red
    if not chroma.any():
        return chroma
    # Preserve the rail's local light/shadow detail, but remove both hue and "active" intensity.
    # It intentionally leaves neutral white/grey outline pixels untouched.
    neutral = np.clip(lum[chroma] * 0.48, 0, 178).astype(np.uint8)
    out[chroma] = np.stack((neutral, neutral, neutral), axis=1)
    return chroma


def copy_track_fill_from_neutral(
    out: np.ndarray,
    base: np.ndarray,
    donor: np.ndarray,
    mask: np.ndarray,
    source_rect: tuple[int, int, int, int],
    target_rect: tuple[int, int, int, int],
) -> None:
    """Resample only a neutral track interior, then copy it through the colored-fill mask."""
    if not mask.any():
        return
    sx0, sy0, sx1, sy1 = source_rect
    tx0, ty0, tx1, ty1 = target_rect
    source = Image.fromarray(donor[sy0:sy1 + 1, sx0:sx1 + 1], "RGB")
    target_size = (tx1 - tx0 + 1, ty1 - ty0 + 1)
    resampled = np.asarray(source.resize(target_size, Image.Resampling.BICUBIC), dtype=np.float32)
    mapped = np.zeros_like(base, dtype=np.float32)
    mapped[ty0:ty1 + 1, tx0:tx1 + 1] = resampled
    local_area = np.zeros(base.shape[:2], dtype=bool)
    local_area[ty0:ty1 + 1, tx0:tx1 + 1] = True
    perimeter = dilate(mask, 2) & ~mask & local_area
    # Do not infer a colour shift from the old active fill's halo: that would tint an otherwise
    # neutral donor yellow/blue/green again.  Only locally neutral, low-energy pixels may match
    # the donor; an empty neutral perimeter deliberately means zero shift.
    _, base_saturation = hue_saturation(base)
    neutral_perimeter = perimeter & (base_saturation < 0.12) & (luminance(base) < 80.0)
    shift = (np.median(base[neutral_perimeter].astype(np.float32) - mapped[neutral_perimeter], axis=0)
             if neutral_perimeter.any() else 0.0)
    out[mask] = np.clip(mapped[mask] + shift, 0, 255).astype(np.uint8)


def reconstruct_track_from_its_neutral_span(
    out: np.ndarray,
    base: np.ndarray,
    mask: np.ndarray,
    neutral_span: tuple[int, int],
    phase_origin: int,
) -> None:
    """Rebuild a live-fill capsule from the unfilled portion of the *same* rail.

    This is deliberately texture sampling, not a black paint-over or a blurred clone: every
    replacement pixel comes from the corresponding y-row of the source track's existing neutral
    segment.  Cycling a short neutral span keeps its rail highlights and grain continuous while
    the capsule mask preserves the original rounded end and geometry.
    """
    if not mask.any():
        return
    source_left, source_right = neutral_span
    if source_right < source_left:
        raise ValueError("neutral track source span must not be empty")
    height, width = base.shape[:2]
    ys, xs = np.indices((height, width))
    span = source_right - source_left + 1
    source_xs = source_left + ((xs - phase_origin) % span)
    sampled = base[ys, source_xs]
    out[mask] = sampled[mask]


def reconstruct_full_neutral_track(
    out: np.ndarray,
    donor: np.ndarray,
    mask: np.ndarray,
    source_rect: tuple[int, int, int, int],
    target_rect: tuple[int, int, int, int],
) -> None:
    """Scale the approved neutral rail itself into the matching source rail silhouette.

    The donor is used only inside the exact capsule mask: card grain, geometry and all pixels
    outside it continue to come from the new populated reference.  Unlike a dark overwrite this
    restores the neutral rim, centre shading and rounded endpoints as a complete rail.
    """
    if not mask.any():
        return
    sx0, sy0, sx1, sy1 = source_rect
    tx0, ty0, tx1, ty1 = target_rect
    source = Image.fromarray(donor[sy0:sy1 + 1, sx0:sx1 + 1], "RGB")
    target_size = (tx1 - tx0 + 1, ty1 - ty0 + 1)
    resampled = np.asarray(source.resize(target_size, Image.Resampling.BICUBIC), dtype=np.uint8)
    mapped = np.zeros_like(out)
    mapped[ty0:ty1 + 1, tx0:tx1 + 1] = resampled
    out[mask] = mapped[mask]


def tach_active_mask(base: np.ndarray) -> np.ndarray:
    """Select only the green/yellow active tach pixels in the elliptical ring sector."""
    height, width = base.shape[:2]
    ys, xs = np.indices((height, width))
    cx, cy, rx, ry = 640.0, 270.0, 219.0, 174.0
    angle = (np.degrees(np.arctan2(ys - cy, xs - cx)) + 360.0) % 360.0
    radius = np.sqrt(((xs - cx) / rx) ** 2 + ((ys - cy) / ry) ** 2)
    # Include the antialiased endpoints just beyond the nominal 145°..286° active segment.
    sector = (angle >= 132.0) & (angle <= 305.0) & (radius >= 0.60) & (radius <= 1.25)
    hue, saturation = hue_saturation(base)
    lum = luminance(base)
    green = (hue >= 65.0) & (hue <= 200.0)
    yellow = (hue >= 35.0) & (hue <= 70.0)
    # The outer glow falls to very low luminance; keep hue/saturation as the discriminator so
    # it is removed together with the bright arc core.  No dilation: static white tick edges
    # must remain in their original positions.
    chroma = (saturation > 0.04) & (lum > 3.0) & (green | yellow)
    corridor = (angle >= 128.0) & (angle <= 309.0) & (radius >= 0.56) & (radius <= 1.30)
    return sector & chroma & corridor


def neutralize_active_tach_pixels(base: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """Turn active tach colour into a subdued, local neutral-metal level.

    Only coloured source pixels are changed.  White numerals/ticks and all geometric detail stay
    untouched, so the shell cannot acquire copied or displaced tick marks.
    """
    result = base.copy()
    values = base[mask].astype(np.float32)
    luma = values[:, 0] * 0.2126 + values[:, 1] * 0.7152 + values[:, 2] * 0.0722
    # Compress former green/yellow illumination into the same restrained neutral range rather
    # than retaining a visibly active bright sector.  It is per-pixel, never a painted arc/patch.
    neutral = np.clip(20.0 + luma * 0.22, 0, 92).astype(np.uint8)
    result[mask] = np.stack((neutral, neutral, neutral), axis=1)
    return result


def main() -> None:
    if not REFERENCE.exists() or not NEUTRAL_DONOR.exists():
        raise FileNotFoundError("Expected reference and neutral donor SPORT PNGs were not found.")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    base = np.asarray(Image.open(REFERENCE).convert("RGB"), dtype=np.uint8)
    donor = np.asarray(Image.open(NEUTRAL_DONOR).convert("RGB"), dtype=np.uint8)
    if base.shape != donor.shape or base.shape[:2] != (720, 1280):
        raise ValueError("Both SPORT images must be 1280x720 RGB images.")

    out = base.copy()
    changed = np.zeros(base.shape[:2], dtype=bool)

    # Exact dynamic glyph bounds. Units, labels, icons, borders, scales and navigation remain untouched.
    glyph_groups = [
        # Header: state, dynamic subtitle, clock and date.
        ((1012, 36, 1142, 64), (900, 8, 1264, 95)),
        ((970, 61, 1140, 85), (900, 8, 1264, 95)),
        ((1148, 33, 1230, 65), (900, 8, 1264, 95)),
        ((1145, 64, 1252, 92), (900, 8, 1264, 95)),
        # Five fixed SPORT value fields; baked units are intentionally outside these masks.
        ((190, 177, 267, 220), (26, 126, 398, 299)),
        ((1058, 177, 1168, 220), (882, 126, 1254, 299)),
        ((202, 378, 273, 422), (46, 332, 391, 504)),
        ((611, 480, 670, 522), (412, 448, 866, 572)),
        ((1064, 378, 1138, 422), (889, 332, 1234, 504)),
        # Tachometer and speed live values.
        ((539, 267, 741, 323), (420, 100, 860, 438)),
        ((603, 324, 680, 353), (525, 362, 757, 450)),
        ((592, 393, 674, 438), (525, 362, 757, 450)),
    ]
    for box, card_bounds in glyph_groups:
        mask = glyph_mask(base, box)
        transplant_best_local_card_texture(out, base, mask, card_bounds)
        changed |= mask

    # Reconstruct only the active part of each neutral track. The target's outline/ticks/endcaps stay pixel-identical.
    tracks = [
        # (target rail capsule, equivalent full neutral rail in the approved shell, colour envelope).
        ((75, 236, 336, 257), (62, 240, 329, 263), (70, 228, 340, 260)),
        ((940, 236, 1203, 257), (938, 240, 1211, 263), (935, 228, 1204, 260)),
        ((84, 438, 344, 459), (70, 447, 338, 470), (80, 430, 346, 465)),
        ((475, 521, 803, 543), (470, 532, 804, 555), (470, 512, 805, 547)),
        ((935, 438, 1200, 459), (937, 447, 1204, 470), (930, 430, 1200, 465)),
    ]
    for body_rect, source_rect, envelope in tracks:
        mask = capsule_mask(base.shape[:2], body_rect)
        reconstruct_full_neutral_track(out, donor, mask, source_rect, body_rect)
        changed |= mask
        residual_chroma = neutralize_static_track_chroma(out, base, envelope, mask)
        changed |= residual_chroma

    # The difficult non-rectangular part: neutralize only chromatic active tach pixels.
    arc_mask = tach_active_mask(base)
    neutral_tach = neutralize_active_tach_pixels(base, arc_mask)
    out[arc_mask] = neutral_tach[arc_mask]
    changed |= arc_mask

    # Explicitly restore fixed glyphs where antialiased live masks can touch their edge pixels.
    # This keeps labels/units/tach numerals pixel-identical to the populated reference.
    protected_static = (
        (267, 193, 301, 225),    # coolant unit
        (1170, 193, 1215, 225),  # voltage unit
        (278, 395, 312, 428),    # throttle unit
        (673, 493, 704, 524),    # load unit
        (1143, 395, 1195, 428),  # intake unit
        (602, 373, 677, 392),    # SPEED
        (682, 413, 752, 440),    # km/h
        (968, 38, 1012, 65),     # literal OBD:
        (1143, 26, 1150, 93),    # header divider
    )
    for x0, y0, x1, y1 in protected_static:
        out[y0:y1 + 1, x0:x1 + 1] = base[y0:y1 + 1, x0:x1 + 1]
        changed[y0:y1 + 1, x0:x1 + 1] = False

    actual_diff = np.any(out != base, axis=2)
    if np.any(actual_diff & ~changed):
        raise AssertionError("Candidate changed pixels outside the declared dynamic masks.")

    Image.fromarray(out, "RGB").save(OUTPUT, optimize=True)
    Image.fromarray((changed.astype(np.uint8) * 255), "L").save(MASK_OUTPUT, optimize=True)
    print(f"candidate={OUTPUT}")
    print(f"mask={MASK_OUTPUT}")
    print(f"declared-mask-pixels={int(changed.sum())}")
    print(f"changed-pixels={int(actual_diff.sum())}")


if __name__ == "__main__":
    main()
