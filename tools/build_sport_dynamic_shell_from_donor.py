"""Make a masked SPORT-shell candidate using the reviewed image-edit texture as a donor.

Only exact glyph-shaped masks are copied; the production source provides every other pixel.
This keeps the approved geometry, borders, tracks and tachometer bit-for-bit untouched.
"""

from pathlib import Path

import numpy as np
from PIL import Image

from build_sport_dynamic_shell import ROOT, SOURCE, glyph_mask


DONOR = ROOT / "artifacts" / "candidates" / "background_sport_image_edit_texture_donor.png"
OUTPUT = ROOT / "artifacts" / "candidates" / "background_sport_dynamic_shell_v3.png"
MASK_OUTPUT = ROOT / "artifacts" / "candidates" / "background_sport_dynamic_shell_v3_mask.png"

# Bounds deliberately exclude borders, dividers, rails and tick marks.  They cover all fixed
# icon/title/unit/scale-label glyphs, which will be drawn dynamically by DashboardView instead.
GROUPS = (
    (55, 145, 132, 225), (155, 148, 330, 184), (258, 196, 292, 227), (55, 272, 326, 296),
    (946, 154, 1025, 220), (1054, 148, 1158, 184), (1183, 196, 1218, 227), (940, 272, 1218, 296),
    (68, 356, 147, 436), (166, 355, 296, 392), (276, 403, 310, 438), (68, 480, 342, 505),
    (493, 470, 552, 526), (587, 468, 720, 505), (672, 504, 712, 530), (468, 555, 809, 579),
    (946, 366, 1032, 432), (1052, 355, 1203, 392), (1154, 403, 1190, 438), (935, 480, 1213, 505),
)


def main() -> None:
    source = np.asarray(Image.open(SOURCE).convert("RGB"), dtype=np.uint8)
    donor = np.asarray(Image.open(DONOR).convert("RGB").resize((1280, 720), Image.Resampling.LANCZOS), dtype=np.uint8)
    if source.shape != donor.shape or source.shape[:2] != (720, 1280):
        raise ValueError("Source and donor must resolve to 1280x720")
    mask = np.zeros(source.shape[:2], dtype=bool)
    for bounds in GROUPS:
        mask |= glyph_mask(source, bounds)
    out = source.copy()
    out[mask] = donor[mask]
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(out, "RGB").save(OUTPUT, optimize=True)
    Image.fromarray((mask.astype(np.uint8) * 255), "L").save(MASK_OUTPUT, optimize=True)
    print(f"candidate={OUTPUT}")
    print(f"changed_pixels={int(np.any(out != source, axis=2).sum())}")


if __name__ == "__main__":
    main()
