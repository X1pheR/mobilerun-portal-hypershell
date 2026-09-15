"""OKLCH -> sRGB hex converter.

Reference: CSS Color Module Level 4 and Bjorn Ottosson's OKLab definition.
"""
import math
import json
import sys


def oklch_to_srgb_hex(L: float, C: float, h: float) -> str:
    """Convert OKLCH (L 0..1, C >= 0, h in degrees) to gamma-encoded sRGB hex."""
    # OKLCh -> OKLab
    h_rad = math.radians(h)
    a = C * math.cos(h_rad)
    b = C * math.sin(h_rad)

    # OKLab -> LMS' (non-linear cone responses)
    l_ = L + 0.3963377774 * a + 0.2158037573 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b

    # LMS' -> linear LMS
    l = l_ * l_ * l_
    m = m_ * m_ * m_
    s = s_ * s_ * s_

    # Linear LMS -> linear sRGB
    r_lin = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g_lin = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    b_lin = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s

    def gamma_encode(x: float) -> float:
        x = max(0.0, min(1.0, x))
        if x <= 0.0031308:
            return 12.92 * x
        return 1.055 * (x ** (1.0 / 2.4)) - 0.055

    r = round(gamma_encode(r_lin) * 255)
    g = round(gamma_encode(g_lin) * 255)
    b_out = round(gamma_encode(b_lin) * 255)

    r = max(0, min(255, r))
    g = max(0, min(255, g))
    b_out = max(0, min(255, b_out))

    return f"#{r:02X}{g:02X}{b_out:02X}"


# Spec tokens from docs/superpowers/specs/2026-05-20-android-theme-redesign-design.md
LIGHT_TOKENS = {
    "background":                (0.985, 0.004, 85),
    "foreground":                (0.26,  0.008, 65),
    "card":                      (0.995, 0.005, 85),
    "card_foreground":           (0.26,  0.008, 65),
    "popover":                   (0.995, 0.005, 85),
    "popover_foreground":        (0.26,  0.008, 65),
    "primary":                   (0.55,  0.18,  268),
    "primary_foreground":        (0.98,  0.005, 268),
    "secondary":                 (0.955, 0.008, 85),
    "secondary_foreground":      (0.26,  0.008, 65),
    "muted":                     (0.955, 0.008, 85),
    "muted_foreground":          (0.55,  0.01,  65),
    "accent":                    (0.94,  0.04,  268),
    "accent_foreground":         (0.38,  0.12,  268),
    "destructive":               (0.58,  0.16,  28),
    "destructive_foreground":    (0.99,  0.003, 85),
    "border":                    (0.92,  0.006, 85),
    "input":                     (0.965, 0.006, 85),
    "ring":                      (0.55,  0.18,  268),
    "chart_1":                   (0.55,  0.18,  268),
    "chart_2":                   (0.65,  0.13,  220),
    "chart_3":                   (0.65,  0.16,  295),
    "chart_4":                   (0.70,  0.13,  75),
    "chart_5":                   (0.60,  0.14,  175),
    "sidebar":                   (0.97,  0.006, 85),
    "sidebar_foreground":        (0.30,  0.008, 65),
    "sidebar_primary":           (0.55,  0.18,  268),
    "sidebar_primary_foreground":(0.98,  0.005, 268),
    "sidebar_accent":            (0.94,  0.04,  268),
    "sidebar_accent_foreground": (0.38,  0.12,  268),
    "sidebar_border":            (0.92,  0.006, 85),
    "sidebar_ring":              (0.55,  0.18,  268),
}

DARK_TOKENS = {
    "background":                (0.16,  0.006, 60),
    "foreground":                (0.92,  0.005, 85),
    "card":                      (0.19,  0.006, 60),
    "card_foreground":           (0.92,  0.005, 85),
    "popover":                   (0.21,  0.006, 60),
    "popover_foreground":        (0.90,  0.005, 85),
    "primary":                   (0.72,  0.16,  265),
    "primary_foreground":        (0.18,  0.02,  268),
    "secondary":                 (0.22,  0.006, 60),
    "secondary_foreground":      (0.92,  0.005, 85),
    "muted":                     (0.21,  0.006, 60),
    "muted_foreground":          (0.68,  0.008, 65),
    "accent":                    (0.28,  0.06,  268),
    "accent_foreground":         (0.86,  0.08,  268),
    "destructive":               (0.55,  0.18,  28),
    "destructive_foreground":    (0.95,  0.005, 85),
    "border":                    (0.25,  0.006, 60),
    "input":                     (0.23,  0.006, 60),
    "ring":                      (0.72,  0.16,  265),
    "chart_1":                   (0.72,  0.16,  265),
    "chart_2":                   (0.74,  0.13,  220),
    "chart_3":                   (0.72,  0.15,  295),
    "chart_4":                   (0.78,  0.12,  75),
    "chart_5":                   (0.70,  0.12,  175),
    "sidebar":                   (0.18,  0.006, 60),
    "sidebar_foreground":        (0.88,  0.005, 85),
    "sidebar_primary":           (0.72,  0.16,  265),
    "sidebar_primary_foreground":(0.18,  0.02,  268),
    "sidebar_accent":            (0.28,  0.06,  268),
    "sidebar_accent_foreground": (0.86,  0.08,  268),
    "sidebar_border":            (0.25,  0.006, 60),
    "sidebar_ring":              (0.72,  0.16,  265),
}


def generate_table() -> dict:
    out = {"light": {}, "dark": {}}
    for name, (L, C, h) in LIGHT_TOKENS.items():
        out["light"][name] = {"oklch": [L, C, h], "hex": oklch_to_srgb_hex(L, C, h)}
    for name, (L, C, h) in DARK_TOKENS.items():
        out["dark"][name] = {"oklch": [L, C, h], "hex": oklch_to_srgb_hex(L, C, h)}
    return out


if __name__ == "__main__":
    table = generate_table()
    json.dump(table, sys.stdout, indent=2)
    sys.stdout.write("\n")
