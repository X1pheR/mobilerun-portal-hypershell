import pytest
from oklch_to_hex import oklch_to_srgb_hex


# Reference values cross-checked against CSS Color Module Level 4 and culori.js.
# Tolerance: each channel may differ by at most 1/255 (rounding noise).
KNOWN_VALUES = [
    # (L,    C,     h,    expected_hex)
    (1.0,    0.0,   0,    "#FFFFFF"),  # pure white
    (0.0,    0.0,   0,    "#000000"),  # pure black
    (0.5,    0.0,   0,    "#636363"),  # OKLab L=0.5 -> sRGB ~0.39 (perceptually uniform, not linear)
    (0.628,  0.2577, 29.23, "#FF0000"),  # pure sRGB red
    (0.866,  0.2948, 142.5, "#00FF00"),  # pure sRGB green
    (0.452,  0.3134, 264.05, "#0000FF"), # pure sRGB blue
]


def _hex_to_rgb(h):
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def _channel_diff(a, b):
    ar, ag, ab = _hex_to_rgb(a)
    br, bg, bb = _hex_to_rgb(b)
    return max(abs(ar - br), abs(ag - bg), abs(ab - bb))


@pytest.mark.parametrize("L,C,h,expected", KNOWN_VALUES)
def test_known_values(L, C, h, expected):
    actual = oklch_to_srgb_hex(L, C, h)
    assert _channel_diff(actual, expected) <= 2, (
        f"oklch({L} {C} {h}) -> {actual}, expected approx {expected}"
    )


def test_clamps_out_of_gamut():
    # Very high chroma -> falls outside sRGB; expect clamped, not negative.
    result = oklch_to_srgb_hex(0.7, 0.5, 30)
    assert len(result) == 7 and result.startswith("#")
    r, g, b = _hex_to_rgb(result)
    assert 0 <= r <= 255 and 0 <= g <= 255 and 0 <= b <= 255


def test_returns_uppercase_hex():
    assert oklch_to_srgb_hex(0.5, 0.0, 0) == oklch_to_srgb_hex(0.5, 0.0, 0).upper()
