"""Build a source-only clean SPORT shell from the populated approved reference.

Every repaired text pixel is copied directly from a texture-only patch in the same reference
card.  No generative image model, blur, colour correction or rectangular paint-over is used.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image


PROJECT = Path(__file__).resolve().parents[2]
REFERENCE = Path(r"C:\Users\css_g\Downloads\Civic\background_sport.png")
NEUTRAL_RAILS = PROJECT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "background_sport.png"
OUTPUT = Path(__file__).with_name("background_sport_new_reference_clean_candidate_v21.png")
MASK_OUTPUT = Path(__file__).with_name("background_sport_new_reference_clean_candidate_v21_mask.png")


def dilate(mask: np.ndarray, radius: int) -> np.ndarray:
    height, width = mask.shape
    result = mask.copy()
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            if dx == 0 and dy == 0:
                continue
            sy0, sy1 = max(0, -dy), min(height, height - dy)
            sx0, sx1 = max(0, -dx), min(width, width - dx)
            ty0, ty1 = max(0, dy), min(height, height + dy)
            tx0, tx1 = max(0, dx), min(width, width + dx)
            result[ty0:ty1, tx0:tx1] |= mask[sy0:sy1, sx0:sx1]
    return result


def luminance(image: np.ndarray) -> np.ndarray:
    image = image.astype(np.float32)
    return image[..., 0] * 0.2126 + image[..., 1] * 0.7152 + image[..., 2] * 0.0722


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


def area_mask(shape: tuple[int, int], box: tuple[int, int, int, int]) -> np.ndarray:
    x0, y0, x1, y1 = box
    result = np.zeros(shape, dtype=bool)
    result[y0:y1 + 1, x0:x1 + 1] = True
    return result


def dynamic_glyph_mask(base: np.ndarray, box: tuple[int, int, int, int]) -> np.ndarray:
    """Tight glyph/antialias mask limited to a known dynamic-text core box."""
    lum = luminance(base)
    hue, saturation = hue_saturation(base)
    area = area_mask(base.shape[:2], box)
    bright = lum > 42.0
    cyan_or_white_glow = (saturation > 0.13) & (lum > 8.0) & (hue >= 150.0) & (hue <= 250.0)
    core = area & (bright | cyan_or_white_glow)
    # The mask expands around literal glyph pixels, not around the containing rectangle.
    return dilate(core, 2)


def capsule_mask(shape: tuple[int, int], rect: tuple[int, int, int, int]) -> np.ndarray:
    height, width = shape
    x0, y0, x1, y1 = rect
    ys, xs = np.indices((height, width))
    radius = max(.5, (y1 - y0 + 1) * .5)
    cy = (y0 + y1) * .5
    left_cx, right_cx = x0 + radius, x1 - radius
    middle = (xs >= left_cx) & (xs <= right_cx) & (np.abs(ys - cy) <= radius)
    left = (xs - left_cx) ** 2 + (ys - cy) ** 2 <= radius ** 2
    right = (xs - right_cx) ** 2 + (ys - cy) ** 2 <= radius ** 2
    return (middle | left | right) & (xs >= x0) & (xs <= x1) & (ys >= y0) & (ys <= y1)


def copy_neutral_rail(
    out: np.ndarray,
    donor: np.ndarray,
    mask: np.ndarray,
    source_rect: tuple[int, int, int, int],
    target_rect: tuple[int, int, int, int],
) -> None:
    """Restore the full static rail through its exact pill silhouette only."""
    sx0, sy0, sx1, sy1 = source_rect
    tx0, ty0, tx1, ty1 = target_rect
    source = Image.fromarray(donor[sy0:sy1 + 1, sx0:sx1 + 1], "RGB")
    mapped = np.asarray(source.resize((tx1 - tx0 + 1, ty1 - ty0 + 1), Image.Resampling.BICUBIC), dtype=np.uint8)
    canvas = np.zeros_like(out)
    canvas[ty0:ty1 + 1, tx0:tx1 + 1] = mapped
    out[mask] = canvas[mask]


def residual_track_chroma(base: np.ndarray, rail_mask: np.ndarray, box: tuple[int, int, int, int]) -> np.ndarray:
    """Select only residual active-colour glow around a repaired rail."""
    hue, saturation = hue_saturation(base)
    lum = luminance(base)
    red_or_cyan_to_yellow = (hue <= 230.0) | (hue >= 340.0)
    return area_mask(base.shape[:2], box) & ~rail_mask & (saturation > .18) & (lum > 15.0) & red_or_cyan_to_yellow


def tach_active_mask(base: np.ndarray) -> np.ndarray:
    height, width = base.shape[:2]
    ys, xs = np.indices((height, width))
    cx, cy, rx, ry = 640.0, 270.0, 219.0, 174.0
    angle = (np.degrees(np.arctan2(ys - cy, xs - cx)) + 360.0) % 360.0
    radius = np.sqrt(((xs - cx) / rx) ** 2 + ((ys - cy) / ry) ** 2)
    hue, saturation = hue_saturation(base)
    lum = luminance(base)
    hue_active = ((hue >= 35.0) & (hue <= 200.0))
    return ((angle >= 128.0) & (angle <= 309.0) & (radius >= .56) & (radius <= 1.30)
            & hue_active & (saturation > .04) & (lum > 3.0))


def neutralize_tach_chroma(out: np.ndarray, base: np.ndarray, mask: np.ndarray) -> None:
    """Leave neutral metal instead of active green/yellow, without touching white tick ink."""
    values = base[mask].astype(np.float32)
    local_luma = values[:, 0] * .2126 + values[:, 1] * .7152 + values[:, 2] * .0722
    neutral = np.clip(20.0 + local_luma * .22, 0, 92).astype(np.uint8)
    out[mask] = np.stack((neutral, neutral, neutral), axis=1)


def candidates_for_roi(forbidden: np.ndarray, roi: tuple[int, int, int, int], radius: int) -> np.ndarray:
    """Find sparse source-only texture patch centres which do not touch forbidden ink/hole pixels."""
    left, top, right, bottom = roi
    centres: list[tuple[int, int]] = []
    # Step two gives a broad texture library without needlessly duplicating near-identical grain.
    for y in range(top + radius, bottom - radius + 1, 2):
        for x in range(left + radius, right - radius + 1, 2):
            if not forbidden[y - radius:y + radius + 1, x - radius:x + radius + 1].any():
                centres.append((y, x))
    return np.asarray(centres, dtype=np.int32)


def quilt_hole_from_card_texture(
    out: np.ndarray,
    base: np.ndarray,
    hole: np.ndarray,
    forbidden: np.ndarray,
    roi: tuple[int, int, int, int],
    radius: int,
) -> None:
    """Exemplar-based, source-only patch quilting inside one dynamic glyph hole."""
    centres = candidates_for_roi(forbidden, roi, radius)
    if not len(centres):
        raise RuntimeError(f"No clean source texture patches available for ROI {roi}")
    work = hole.copy()
    height, width = work.shape
    offsets_y, offsets_x = np.indices((radius * 2 + 1, radius * 2 + 1))
    offsets_y = offsets_y.ravel() - radius
    offsets_x = offsets_x.ravel() - radius

    while work.any():
        # Pick a hole pixel whose local patch has the greatest known boundary to match.
        candidate_targets = np.argwhere(work)
        best_target: tuple[int, int, int] | None = None
        for y, x in candidate_targets:
            y0, y1 = max(0, y - radius), min(height, y + radius + 1)
            x0, x1 = max(0, x - radius), min(width, x + radius + 1)
            known_count = int((~work[y0:y1, x0:x1]).sum())
            if best_target is None or known_count > best_target[0]:
                best_target = (known_count, int(y), int(x))
        assert best_target is not None
        _, target_y, target_x = best_target
        patch_y = target_y + offsets_y
        patch_x = target_x + offsets_x
        inside = ((patch_y >= 0) & (patch_y < height) & (patch_x >= 0) & (patch_x < width))
        patch_y, patch_x = patch_y[inside], patch_x[inside]
        unknown = work[patch_y, patch_x]
        known = ~unknown
        if not unknown.any():
            work[target_y, target_x] = False
            continue
        source_y = centres[:, 0, None] + (patch_y[None, :] - target_y)
        source_x = centres[:, 1, None] + (patch_x[None, :] - target_x)
        if known.any():
            source_values = base[source_y[:, known], source_x[:, known]].astype(np.int16)
            target_values = out[patch_y[known], patch_x[known]].astype(np.int16)
            diff = source_values - target_values[None, :, :]
            costs = np.mean(diff.astype(np.float32) ** 2, axis=(1, 2))
        else:
            # A deterministic preference for the earliest source candidate is sufficient for
            # a completely surrounded first patch; later patches receive a real boundary score.
            costs = np.arange(len(centres), dtype=np.float32) * 1e-6
        best_source = int(np.argmin(costs))
        copied = base[source_y[best_source, unknown], source_x[best_source, unknown]]
        out[patch_y[unknown], patch_x[unknown]] = copied
        work[patch_y[unknown], patch_x[unknown]] = False


def main() -> None:
    if not REFERENCE.exists() or not NEUTRAL_RAILS.exists():
        raise FileNotFoundError("SPORT reference or approved neutral rail donor is missing")
    base = np.asarray(Image.open(REFERENCE).convert("RGB"), dtype=np.uint8)
    donor = np.asarray(Image.open(NEUTRAL_RAILS).convert("RGB"), dtype=np.uint8)
    if base.shape != (720, 1280, 3) or donor.shape != base.shape:
        raise ValueError("Both SPORT inputs must be RGB 1280x720 PNGs")
    out = base.copy()
    changed = np.zeros(base.shape[:2], dtype=bool)

    # Static rails become neutral capsules before any dynamic data is painted at runtime.
    rail_specs = [
        ((75, 236, 336, 257), (62, 240, 329, 263), (70, 228, 340, 260)),
        ((940, 236, 1203, 257), (938, 240, 1211, 263), (935, 228, 1204, 260)),
        ((84, 438, 344, 459), (70, 447, 338, 470), (80, 430, 346, 465)),
        ((475, 521, 803, 543), (470, 532, 804, 555), (470, 512, 805, 547)),
        ((935, 438, 1200, 459), (937, 447, 1204, 470), (930, 430, 1200, 465)),
    ]
    rail_masks: list[np.ndarray] = []
    for target, source, envelope in rail_specs:
        rail = capsule_mask(base.shape[:2], target)
        copy_neutral_rail(out, donor, rail, source, target)
        residual = residual_track_chroma(base, rail, envelope)
        residual_luma = luminance(base)[residual]
        residual_gray = np.clip(residual_luma * .48, 0, 178).astype(np.uint8)
        out[residual] = np.stack((residual_gray, residual_gray, residual_gray), axis=1)
        changed |= rail | residual
        rail_masks.append(rail | residual)

    tach = tach_active_mask(base)
    neutralize_tach_chroma(out, base, tach)
    changed |= tach

    # Exact dynamic glyph cores, their source-only texture ROIs, and small quilting patches.
    glyph_specs = [
        ((193, 181, 258, 217), (40, 142, 380, 231), 4),       # coolant 88
        ((1064, 181, 1162, 217), (900, 142, 1240, 231), 4),   # voltage 13.8
        ((202, 382, 272, 418), (55, 344, 375, 430), 4),       # throttle 62
        ((614, 486, 666, 512), (430, 452, 850, 519), 4),      # load 48
        ((1070, 382, 1137, 419), (900, 344, 1225, 430), 4),   # intake 32
        ((545, 271, 733, 316), (505, 198, 775, 352), 3),      # RPM 4500
        ((597, 397, 672, 434), (548, 397, 675, 441), 3),      # speed 86
        ((1018, 44, 1109, 56), (900, 10, 1264, 92), 3),       # header state
        ((974, 67, 1132, 77), (900, 62, 1264, 92), 3),        # header subtitle
        ((1164, 39, 1223, 57), (900, 30, 1264, 63), 3),       # clock
        ((1164, 68, 1249, 77), (900, 64, 1264, 92), 3),       # date
    ]
    glyph_masks = [dynamic_glyph_mask(base, box) for box, _, _ in glyph_specs]
    glyph_union = np.logical_or.reduce(glyph_masks)

    lum = luminance(base)
    _, saturation = hue_saturation(base)
    # Static ink cannot serve as a source patch.  The dilation protects antialiased borders,
    # labels, icons, units, scale marks and the existing rail/tach artwork.
    # Do not classify the dashboard's low-energy red/blue grain as "ink"; it is precisely the
    # texture we want to quilt back into a removed value.  Bright labels/borders still remain
    # excluded with a generous antialias margin below.
    static_ink = (lum > 112.0) | ((saturation > .48) & (lum > 82.0))
    forbidden = dilate(static_ink | glyph_union | tach | np.logical_or.reduce(rail_masks), 2)
    for mask, (_, roi, radius) in zip(glyph_masks, glyph_specs):
        quilt_hole_from_card_texture(out, base, mask, forbidden, roi, radius)
        changed |= mask

    # Restore all locked static glyphs that touch a dynamic mask's antialias edge.
    protected_static = (
        (267, 193, 301, 225), (1170, 193, 1215, 225),
        (278, 395, 312, 428), (673, 493, 704, 524), (1143, 395, 1195, 428),
        (600, 372, 680, 396), (682, 413, 752, 440),
        (968, 38, 1012, 65), (1143, 26, 1150, 93),
    )
    for x0, y0, x1, y1 in protected_static:
        out[y0:y1 + 1, x0:x1 + 1] = base[y0:y1 + 1, x0:x1 + 1]

    actual_diff = np.any(out != base, axis=2)
    if np.any(actual_diff & ~changed):
        raise AssertionError("Candidate changed pixels outside declared dynamic masks")
    Image.fromarray(out, "RGB").save(OUTPUT, optimize=True)
    Image.fromarray((changed.astype(np.uint8) * 255), "L").save(MASK_OUTPUT, optimize=True)
    print(f"candidate={OUTPUT}")
    print(f"mask={MASK_OUTPUT}")
    print(f"declared-mask-pixels={int(changed.sum())}")
    print(f"changed-pixels={int(actual_diff.sum())}")


if __name__ == "__main__":
    main()
