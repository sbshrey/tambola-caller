"""Render the Google Play artwork. Requires Pillow; this is not an app build dependency."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


OUT = Path(__file__).resolve().parent
INK = "#292638"
PINK = "#A91E59"
GREEN = "#11794E"
PAPER = "#FBF8F3"
CREAM = "#FFF4CE"
MUTED = "#6C6875"
WHITE = "#FFFFFF"
FONT = "/System/Library/Fonts/Supplemental/Arial.ttf"
FONT_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT, size)


def icon():
    scale = 4
    image = Image.new("RGBA", (512 * scale, 512 * scale), PINK)
    draw = ImageDraw.Draw(image)
    points = [(8, 10), (40, 10), (40, 17), (28, 17), (28, 40), (20, 40), (20, 17), (8, 17)]
    draw.polygon([(x * 512 / 48 * scale, y * 512 / 48 * scale) for x, y in points], fill=CREAM)
    image.resize((512, 512), Image.Resampling.LANCZOS).save(OUT / "tambola-keyboard-icon-512.png", optimize=True)


def feature_graphic():
    image = Image.new("RGB", (1024, 500), PAPER)
    draw = ImageDraw.Draw(image)

    # Soft shapes frame the phone without competing with the product name.
    draw.ellipse((800, -145, 1110, 165), fill="#F7E6ED")
    draw.ellipse((850, 377, 1080, 607), fill="#E5F0E9")
    draw.ellipse((635, 27, 655, 47), fill="#E8B54D")
    draw.ellipse((970, 321, 990, 341), fill=PINK)

    # Value-first headline leaves the app icon to carry the brand mark.
    draw.text((64, 74), "TAMBOLA FOR YOUR GROUP", font=font(15, True), fill=PINK)
    draw.text((64, 132), "Keep the game", font=font(39, True), fill=INK)
    draw.text((64, 181), "moving together.", font=font(39, True), fill=INK)
    draw.text((64, 246), "Call numbers, check claims, and share", font=font(20), fill=MUTED)
    draw.text((64, 276), "results from one phone.", font=font(20), fill=MUTED)

    # Feature chips.
    def chip(x, y, width, label, fill, text_color):
        draw.rounded_rectangle((x, y, x + width, y + 43), radius=21, fill=fill)
        draw.text((x + 16, y + 12), label, font=font(14, True), fill=text_color)

    chip(64, 337, 178, "OFFLINE CALL VOICES", "#F7E6ED", PINK)
    chip(254, 337, 169, "WINNER TRACKING", "#E5F0E9", GREEN)
    chip(64, 393, 188, "YOU REVIEW AND SEND", "#EEEAF5", INK)

    # Illustrative in-app flow, not a WhatsApp screen or a product screenshot.
    draw.rounded_rectangle((659, 41, 965, 477), radius=42, fill=INK)
    draw.rounded_rectangle((672, 54, 952, 464), radius=31, fill=WHITE)
    draw.rounded_rectangle((770, 63, 854, 69), radius=3, fill="#B9B6BF")
    draw.ellipse((686, 91, 720, 125), fill=PINK)
    draw.text((696, 99), "T", font=font(20, True), fill=CREAM)
    draw.text((730, 91), "Tambola game", font=font(17, True), fill=INK)
    draw.text((730, 113), "Call 12 of 90", font=font(12), fill=MUTED)
    draw.line((686, 143, 938, 143), fill="#EEEAF0", width=2)

    draw.rounded_rectangle((690, 166, 930, 339), radius=23, fill="#FBF8F3")
    draw.text((711, 184), "LATEST NUMBER", font=font(12, True), fill=MUTED)
    draw.ellipse((748, 207, 872, 331), fill=PINK)
    draw.ellipse((760, 219, 860, 319), fill="#BD3A70")
    draw.text((775, 239), "47", font=font(48, True), fill=WHITE)
    draw.text((791, 294), "CALLED", font=font(11, True), fill=CREAM)

    draw.rounded_rectangle((690, 352, 930, 398), radius=16, fill=GREEN)
    draw.text((747, 366), "Next number", font=font(18, True), fill=WHITE)
    draw.rounded_rectangle((690, 407, 930, 447), radius=14, fill="#EEEAF5")
    draw.text((730, 419), "Review, then send", font=font(16, True), fill=INK)

    image.save(OUT / "tambola-keyboard-feature-1024x500.png", optimize=True)


if __name__ == "__main__":
    icon()
    feature_graphic()
