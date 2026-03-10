"""
Apex app icon generator — produces 512x512 play_store_icon.png
Requires: Pillow (pip install Pillow)

Design: mountain-A mark (peak catching ice light → deep steel base)
on a deep navy background with atmospheric peak glow.
"""

import math
from PIL import Image, ImageDraw, ImageFilter

SIZE = 512

def lerp(a, b, t):
    return a + (b - a) * t

def lerp_color(c1, c2, t):
    return tuple(int(lerp(a, b, t)) for a, b in zip(c1, c2))

def gradient_color(stops, t):
    """stops: [(pos, (r,g,b)), ...] sorted by pos"""
    t = max(0.0, min(1.0, t))
    for i in range(len(stops) - 1):
        p0, c0 = stops[i]
        p1, c1 = stops[i + 1]
        if t <= p1:
            local_t = (t - p0) / (p1 - p0) if p1 > p0 else 0
            return lerp_color(c0, c1, local_t)
    return stops[-1][1]

def hex_to_rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

# ── Mark gradient stops (ice-white at peak → deep steel at base) ──────────
MARK_STOPS = [
    (0.00, hex_to_rgb('D4EEFF')),  # ice white-blue at tip
    (0.18, hex_to_rgb('A0CFEE')),  # bright steel
    (0.50, hex_to_rgb('5B9BD5')),  # primary steel blue
    (0.85, hex_to_rgb('3A7CBF')),  # deeper steel
    (1.00, hex_to_rgb('2460A7')),  # richest at base
]

# ── Geometry ──────────────────────────────────────────────────────────────
PEAK  = (256, 100)
BL    = ( 92, 410)
BR    = (420, 410)
T     = 0.60           # crossbar at 60% of peak→base
CB_L  = (256 + T * (92  - 256), 100 + T * (410 - 100))   # (157.6, 286)
CB_R  = (256 + T * (420 - 256), 100 + T * (410 - 100))   # (354.4, 286)


def stroke_points(p1, p2, half_w):
    """Return a filled parallelogram for a thick line segment, as 4 corners."""
    dx, dy = p2[0] - p1[0], p2[1] - p1[1]
    length = math.hypot(dx, dy)
    nx, ny = -dy / length, dx / length   # normal
    return [
        (p1[0] + nx * half_w, p1[1] + ny * half_w),
        (p1[0] - nx * half_w, p1[1] - ny * half_w),
        (p2[0] - nx * half_w, p2[1] - ny * half_w),
        (p2[0] + nx * half_w, p2[1] + ny * half_w),
    ]


def draw_rounded_cap(draw, center, radius, color):
    cx, cy = center
    r = radius
    draw.ellipse([(cx - r, cy - r), (cx + r, cy + r)], fill=color)


def draw_thick_line(layer, p1, p2, half_w, color_fn, peak_y, base_y, alpha=255):
    """Draw a gradient thick line by sampling vertical strips."""
    draw = ImageDraw.Draw(layer)
    dx, dy = p2[0] - p1[0], p2[1] - p1[1]
    length = max(math.hypot(dx, dy), 1)
    nx, ny = -dy / length, dx / length

    # Sample in small steps along the line
    steps = max(int(length), 2)
    for i in range(steps + 1):
        t_line = i / steps
        cx = p1[0] + dx * t_line
        cy = p1[1] + dy * t_line

        # Gradient t based on vertical position
        t_grad = (cy - peak_y) / (base_y - peak_y) if base_y != peak_y else 0

        r, g, b = color_fn(t_grad)
        color = (r, g, b, alpha)

        # Draw a small circle at each point (cheaper than proper thick line)
        hw = half_w
        draw.ellipse(
            [(cx - hw, cy - hw), (cx + hw, cy + hw)],
            fill=color
        )


def make_icon(size=512):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    s = size / 512

    # ── Background ──────────────────────────────────────────────────────────
    bg = Image.new('RGBA', (size, size), (0, 0, 0, 255))
    bg_draw = ImageDraw.Draw(bg)

    # Radial gradient background: lighter navy at top-center, darkest at corners
    bg_stops = [
        (0.00, hex_to_rgb('1C2E4A')),
        (0.45, hex_to_rgb('111C30')),
        (1.00, hex_to_rgb('09101C')),
    ]
    cx_bg, cy_bg = size * 0.50, size * 0.32
    max_r = size * 0.72
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - cx_bg, y - cy_bg) / max_r
            r, g, b = gradient_color(bg_stops, d)
            bg_draw.point((x, y), fill=(r, g, b, 255))

    # Rounded square mask
    mask = Image.new('L', (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    radius = int(size * 0.234)
    mask_draw.rounded_rectangle([(0, 0), (size - 1, size - 1)], radius=radius, fill=255)
    bg.putalpha(mask)
    img = Image.alpha_composite(img, bg)

    # ── Atmospheric peak glow ───────────────────────────────────────────────
    glow_bg = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow_bg)
    gx, gy = int(size * 0.5), int(size * 0.25)
    gr = size * 0.42
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - gx, y - gy) / gr
            if d < 1.0:
                alpha = int(lerp(56, 0, d))  # 22% opacity max
                glow_draw.point((x, y), fill=(91, 155, 213, alpha))
    img = Image.alpha_composite(img, glow_bg)

    peak_y_abs  = PEAK[1] * s
    base_y_abs  = BL[1] * s

    # ── Glow layers (wide, semi-transparent) ────────────────────────────────
    for glow_hw, glow_alpha in [(64 * s, 46), (47 * s, 65)]:
        glow_layer = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        def glow_color(t):
            return (91, 155, 213)
        draw_thick_line(glow_layer, (PEAK[0]*s, PEAK[1]*s), (BL[0]*s, BL[1]*s),
                        glow_hw, glow_color, peak_y_abs, base_y_abs, alpha=glow_alpha)
        draw_thick_line(glow_layer, (PEAK[0]*s, PEAK[1]*s), (BR[0]*s, BR[1]*s),
                        glow_hw, glow_color, peak_y_abs, base_y_abs, alpha=glow_alpha)
        draw_thick_line(glow_layer, (CB_L[0]*s, CB_L[1]*s), (CB_R[0]*s, CB_R[1]*s),
                        glow_hw, glow_color, peak_y_abs, base_y_abs, alpha=glow_alpha)
        # Rounded caps on glow
        gd = ImageDraw.Draw(glow_layer)
        for pt in [PEAK, BL, BR]:
            r = int(glow_hw)
            cx, cy = int(pt[0]*s), int(pt[1]*s)
            gd.ellipse([(cx-r, cy-r), (cx+r, cy+r)], fill=(91, 155, 213, glow_alpha))
        glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(radius=int(12 * s)))
        img = Image.alpha_composite(img, glow_layer)

    # ── Core mark with gradient ──────────────────────────────────────────────
    mark_layer = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    half_w = 37 * s

    def mark_color(t):
        return gradient_color(MARK_STOPS, t)

    draw_thick_line(mark_layer, (PEAK[0]*s, PEAK[1]*s), (BL[0]*s, BL[1]*s),
                    half_w, mark_color, peak_y_abs, base_y_abs, alpha=255)
    draw_thick_line(mark_layer, (PEAK[0]*s, PEAK[1]*s), (BR[0]*s, BR[1]*s),
                    half_w, mark_color, peak_y_abs, base_y_abs, alpha=255)
    draw_thick_line(mark_layer, (CB_L[0]*s, CB_L[1]*s), (CB_R[0]*s, CB_R[1]*s),
                    half_w, mark_color, peak_y_abs, base_y_abs, alpha=255)

    # Rounded caps on mark
    md = ImageDraw.Draw(mark_layer)
    for pt in [PEAK, BL, BR]:
        t_grad = (pt[1]*s - peak_y_abs) / (base_y_abs - peak_y_abs)
        r_col, g_col, b_col = gradient_color(MARK_STOPS, t_grad)
        r = int(half_w)
        cx, cy = int(pt[0]*s), int(pt[1]*s)
        md.ellipse([(cx-r, cy-r), (cx+r, cy+r)], fill=(r_col, g_col, b_col, 255))
    # Crossbar caps
    for pt in [CB_L, CB_R]:
        t_grad = (pt[1]*s - peak_y_abs) / (base_y_abs - peak_y_abs)
        r_col, g_col, b_col = gradient_color(MARK_STOPS, t_grad)
        r = int(half_w)
        cx, cy = int(pt[0]*s), int(pt[1]*s)
        md.ellipse([(cx-r, cy-r), (cx+r, cy+r)], fill=(r_col, g_col, b_col, 255))

    img = Image.alpha_composite(img, mark_layer)

    # ── Peak highlight (bright white tip) ────────────────────────────────────
    hi_layer = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    hi_half_w = 9 * s
    hi_fade_y = PEAK[1]*s + 110*s  # fade to transparent by here

    def hi_color_fn(t):
        # Only relevant at top portion
        return (220, 240, 255)

    # Just the upper portions of the legs for the highlight
    hi_stops = [
        (0.00, (220, 240, 255)),
        (1.00, (220, 240, 255)),
    ]
    # Midpoint of legs for highlight endpoint
    hi_bl = (PEAK[0]*s + 0.30*(BL[0] - PEAK[0])*s, PEAK[1]*s + 0.30*(BL[1] - PEAK[1])*s)
    hi_br = (PEAK[0]*s + 0.30*(BR[0] - PEAK[0])*s, PEAK[1]*s + 0.30*(BR[1] - PEAK[1])*s)

    hid = ImageDraw.Draw(hi_layer)
    # Draw highlight as a tapered line — use variable alpha
    steps = 60
    for i in range(steps):
        t_line = i / steps
        t_next = (i + 1) / steps
        alpha = int(lerp(170, 0, t_line))  # fade from 67% to 0%
        # Left leg
        lx = lerp(PEAK[0]*s, hi_bl[0], t_line)
        ly = lerp(PEAK[1]*s, hi_bl[1], t_line)
        hw = lerp(hi_half_w, 0, t_line)
        if hw > 0.5:
            hid.ellipse([(lx - hw, ly - hw), (lx + hw, ly + hw)],
                       fill=(220, 240, 255, alpha))
        # Right leg
        rx = lerp(PEAK[0]*s, hi_br[0], t_line)
        ry = lerp(PEAK[1]*s, hi_br[1], t_line)
        if hw > 0.5:
            hid.ellipse([(rx - hw, ry - hw), (rx + hw, ry + hw)],
                       fill=(220, 240, 255, alpha))

    img = Image.alpha_composite(img, hi_layer)

    # Apply rounded square mask to final composite
    final = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    final.paste(img, mask=mask)
    return final


if __name__ == '__main__':
    import os
    out_dir = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res', 'mipmap-xxxhdpi')
    os.makedirs(out_dir, exist_ok=True)

    print("Generating 512x512 Play Store icon...")
    icon = make_icon(512)
    play_store_path = os.path.join(os.path.dirname(__file__), 'play_store_icon.png')
    icon.save(play_store_path)
    print(f"Saved: {play_store_path}")

    # Also save smaller preview sizes
    for sz in [192, 96, 48]:
        small = make_icon(sz)
        path = os.path.join(os.path.dirname(__file__), f'icon_{sz}.png')
        small.save(path)
        print(f"Saved: {path}")

    print("Done.")
