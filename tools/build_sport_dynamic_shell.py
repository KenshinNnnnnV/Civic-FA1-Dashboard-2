"""Create a review-only SPORT shell with all five sensor cards configurable.

The source PNG is never modified.  Each removal mask follows the literal bright glyphs in a
tight card-local area, then fills only those glyph-shaped holes with real, quiet pixels sampled
from the same card.  There are deliberately no rectangular fills, blur operations, or geometry
changes.  Inspect the generated candidate before promoting it to an Android resource.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "background_sport.png"
OUTPUT = ROOT / "artifacts" / "candidates" / "background_sport_dynamic_shell_v2.png"
MASK_OUTPUT = ROOT / "artifacts" / "candidates" / "background_sport_dynamic_shell_v2_mask.png"


def luminance(image: np.ndarray) -> np.ndarray:
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


def dilate(mask: np.ndarray, radius: int) -> np.ndarray:
    height, width = mask.shape
    out = mask.copy()
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            if not dx and not dy:
                continue
            sx0, sx1 = max(0, -dx), min(width, width - dx)
            sy0, sy1 = max(0, -dy), min(height, height - dy)
            dx0, dx1 = max(0, dx), min(width, width + dx)
            dy0, dy1 = max(0, dy), min(height, height + dy)
            out[dy0:dy1, dx0:dx1] |= mask[sy0:sy1, sx0:sx1]
    return out


def glyph_mask(image: np.ndarray, bounds: tuple[int, int, int, int]) -> np.ndarray:
    """Select a text/icon silhouette plus its antialiased glow, never the whole bounds rectangle."""
    x0, y0, x1, y1 = bounds
    lum = luminance(image)
    hue, saturation = hue_saturation(image)
    area = np.zeros(image.shape[:2], dtype=bool)
    area[y0:y1 + 1, x0:x1 + 1] = True
    core = area & (lum > 38.0)
    # Cool cyan/white glow attached to the actual glyph is part of the glyph, not card texture.
    cool_glow = area & (lum > 16.0) & (saturation > 0.13) & (hue >= 155.0) & (hue <= 245.0)
    # A six-pixel expansion still follows every actual glyph silhouette, while consuming the
    # low-energy halo that otherwise reads as a ghost icon on the head unit.
    return dilate(core | (cool_glow & dilate(core, 5)), 6)


def inpaint_from_nearest_card_texture(out: np.ndarray, source: np.ndarray, mask: np.ndarray) -> None:
    """Propagate actual neighbouring dark texture into a glyph-shaped hole.

    The propagation is restricted to literal mask pixels, so it cannot create a rectangular
    erase zone.  Bright lettering, rails and borders are never sources for the reconstruction.
    """
    ys, xs = np.where(mask)
    if not len(xs):
        return
    margin = 16
    x0, x1 = max(0, int(xs.min()) - margin), min(source.shape[1] - 1, int(xs.max()) + margin)
    y0, y1 = max(0, int(ys.min()) - margin), min(source.shape[0] - 1, int(ys.max()) + margin)
    local_mask = mask[y0:y1 + 1, x0:x1 + 1]
    local_source = source[y0:y1 + 1, x0:x1 + 1]
    local_luma = luminance(local_source)
    resolved = ~local_mask & (local_luma < 62.0)
    if not resolved.any():
        resolved = ~local_mask
    height, width = resolved.shape
    grid_y, grid_x = np.indices((height, width))
    source_y = np.full((height, width), -1, dtype=np.int32)
    source_x = np.full((height, width), -1, dtype=np.int32)
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
            take = unresolved[ty0:ty1, tx0:tx1] & resolved[sy0:sy1, sx0:sx1]
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
            raise RuntimeError("No neighbouring card texture was available for a glyph mask")
    local_out = out[y0:y1 + 1, x0:x1 + 1]
    sampled = local_source[source_y, source_x]
    local_out[local_mask] = sampled[local_mask]


def transplant_card_texture(
    out: np.ndarray,
    source: np.ndarray,
    mask: np.ndarray,
    card_bounds: tuple[int, int, int, int],
) -> None:
    """Fill only glyph pixels from a best-matching quiet texture location in the same card."""
    ys, xs = np.where(mask)
    if not len(xs):
        return

    left, top, right, bottom = card_bounds
    min_x, max_x = int(xs.min()), int(xs.max())
    min_y, max_y = int(ys.min()), int(ys.max())
    min_dx, max_dx = left - min_x, right - max_x
    min_dy, max_dy = top - min_y, bottom - max_y
    if min_dx > max_dx or min_dy > max_dy:
        raise ValueError("Mask cannot be translated inside its declared card")

    lum = luminance(source)
    horizontal = np.abs(np.diff(lum, axis=1, prepend=lum[:, :1]))
    vertical = np.abs(np.diff(lum, axis=0, prepend=lum[:1, :]))
    quiet = (lum < 72.0) & ((horizontal + vertical) < 34.0)
    ring = dilate(mask, 2) & ~mask
    ring_y, ring_x = np.where(ring)

    def score(dx: int, dy: int) -> float | None:
        sample_y, sample_x = ys + dy, xs + dx
        if quiet[sample_y, sample_x].mean() < 0.97:
            return None
        if not len(ring_x):
            return 0.0
        target_y, target_x = ring_y + dy, ring_x + dx
        valid = ((target_x >= 0) & (target_x < source.shape[1]) &
                 (target_y >= 0) & (target_y < source.shape[0]))
        if not valid.any():
            return None
        delta = np.abs(source[ring_y[valid], ring_x[valid]].astype(np.int16) -
                       source[target_y[valid], target_x[valid]].astype(np.int16))
        return float(np.median(delta) + np.mean(delta) * 0.2)

    best: tuple[float, int, int] | None = None
    for dy in range(min_dy, max_dy + 1, 2):
        for dx in range(min_dx, max_dx + 1, 2):
            candidate = score(dx, dy)
            if candidate is not None and (best is None or candidate < best[0]):
                best = candidate, dx, dy
    if best is not None:
        _, coarse_dx, coarse_dy = best
        for dy in range(max(min_dy, coarse_dy - 2), min(max_dy, coarse_dy + 2) + 1):
            for dx in range(max(min_dx, coarse_dx - 2), min(max_dx, coarse_dx + 2) + 1):
                candidate = score(dx, dy)
                if candidate is not None and candidate < best[0]:
                    best = candidate, dx, dy

    if best is None:
        # This fallback still copies an actual neighbouring dark card pixel through the glyph mask.
        local = lum[top:bottom + 1, left:right + 1]
        yy, xx = np.unravel_index(np.argmin(local), local.shape)
        out[mask] = source[top + yy, left + xx]
        return

    _, dx, dy = best
    out[mask] = source[ys + dy, xs + dx]


def main() -> None:
    source = np.asarray(Image.open(SOURCE).convert("RGB"), dtype=np.uint8)
    if source.shape[:2] != (720, 1280):
        raise ValueError(f"Expected a 1280x720 source, got {source.shape[1]}x{source.shape[0]}")

    # (literal-glyph bounds, enclosing card texture domain). Borders, dividers, tracks and ticks
    # are intentionally outside these groups and therefore remain byte-identical to the source.
    groups = [
        ((55, 145, 132, 225), (30, 135, 380, 295)),   # upper-left icon
        ((155, 148, 330, 184), (30, 135, 380, 295)),  # upper-left title
        ((258, 196, 292, 227), (30, 135, 380, 295)),  # upper-left unit
        ((55, 272, 326, 296), (30, 135, 380, 295)),   # upper-left scale labels
        ((946, 154, 1025, 220), (900, 135, 1245, 295)),
        ((1054, 148, 1158, 184), (900, 135, 1245, 295)),
        ((1183, 196, 1218, 227), (900, 135, 1245, 295)),
        ((940, 272, 1218, 296), (900, 135, 1245, 295)),
        ((68, 356, 147, 436), (50, 345, 375, 500)),
        ((166, 355, 296, 392), (50, 345, 375, 500)),
        ((276, 403, 310, 438), (50, 345, 375, 500)),
        ((68, 480, 342, 505), (50, 345, 375, 500)),
        ((493, 470, 552, 526), (420, 460, 850, 575)),
        ((587, 468, 706, 502), (420, 460, 850, 575)),
        ((672, 504, 704, 530), (420, 460, 850, 575)),
        ((468, 555, 809, 579), (420, 460, 850, 575)),
        ((946, 366, 1032, 432), (905, 345, 1225, 500)),
        ((1052, 355, 1203, 392), (905, 345, 1225, 500)),
        ((1154, 403, 1190, 438), (905, 345, 1225, 500)),
        ((935, 480, 1213, 505), (905, 345, 1225, 500)),
    ]

    out = source.copy()
    changed = np.zeros(source.shape[:2], dtype=bool)
    for glyph_bounds, card_bounds in groups:
        mask = glyph_mask(source, glyph_bounds)
        inpaint_from_nearest_card_texture(out, source, mask)
        changed |= mask

    actual = np.any(out != source, axis=2)
    if np.any(actual & ~changed):
        raise AssertionError("Candidate changed pixels outside the declared glyph masks")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(out, "RGB").save(OUTPUT, optimize=True)
    Image.fromarray((changed.astype(np.uint8) * 255), "L").save(MASK_OUTPUT, optimize=True)
    print(f"candidate={OUTPUT}")
    print(f"mask={MASK_OUTPUT}")
    print(f"changed_pixels={int(actual.sum())}")


if __name__ == "__main__":
    main()
