from __future__ import annotations

from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image

PROJECT_ROOT = Path(__file__).resolve().parents[1]
RES_DIR = PROJECT_ROOT / "app" / "src" / "main" / "res"
SOURCE_ICON = RES_DIR / "mipmap-xxxhdpi" / "ic_launcher.png"
BACKGROUND_COLOR = (0x1B, 0x1F, 0x2A, 0xFF)
LEGACY_CANVAS_SCALE = 0.96
FOREGROUND_CANVAS_SCALE = 0.74

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

FOREGROUND_DENSITIES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def outer_background_mask(image_array: np.ndarray) -> np.ndarray:
    height, width = image_array.shape[:2]
    rgb = image_array[:, :, :3]
    dark = np.all(rgb < 40, axis=2)
    visited = np.zeros((height, width), dtype=bool)
    queue: deque[tuple[int, int]] = deque()

    for x in range(width):
        if dark[0, x]:
            queue.append((x, 0))
        if dark[height - 1, x]:
            queue.append((x, height - 1))
    for y in range(height):
        if dark[y, 0]:
            queue.append((0, y))
        if dark[y, width - 1]:
            queue.append((width - 1, y))

    while queue:
        x, y = queue.popleft()
        if x < 0 or y < 0 or x >= width or y >= height:
            continue
        if visited[y, x] or not dark[y, x]:
            continue
        visited[y, x] = True
        queue.append((x - 1, y))
        queue.append((x + 1, y))
        queue.append((x, y - 1))
        queue.append((x, y + 1))

    return visited


def extract_emblem(source: Image.Image) -> Image.Image:
    rgba = np.array(source.convert("RGBA"))
    background = outer_background_mask(rgba)
    white = np.all(rgba[:, :, :3] > 230, axis=2)
    emblem_mask = (~background) & (~white)

    ys, xs = np.where(emblem_mask)
    if len(xs) == 0:
        raise RuntimeError("Could not detect emblem pixels in source icon")

    left, top, right, bottom = xs.min(), ys.min(), xs.max(), ys.max()
    emblem = Image.new("RGBA", source.size, (0, 0, 0, 0))
    emblem.putalpha(Image.fromarray(emblem_mask.astype(np.uint8) * 255, mode="L"))
    emblem = Image.composite(source.convert("RGBA"), emblem, emblem)
    return emblem.crop((left, top, right + 1, bottom + 1))


def render_icon(emblem: Image.Image, canvas_size: int, scale: float) -> Image.Image:
    canvas = Image.new("RGBA", (canvas_size, canvas_size), BACKGROUND_COLOR)
    target = int(min(canvas_size, canvas_size) * scale)
    resized = emblem.copy()
    resized.thumbnail((target, target), Image.Resampling.LANCZOS)
    offset = ((canvas_size - resized.width) // 2, (canvas_size - resized.height) // 2)
    canvas.alpha_composite(resized, offset)
    return canvas


def render_foreground(emblem: Image.Image, canvas_size: int, scale: float) -> Image.Image:
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    target = int(canvas_size * scale)
    resized = emblem.copy()
    resized.thumbnail((target, target), Image.Resampling.LANCZOS)
    offset = ((canvas_size - resized.width) // 2, (canvas_size - resized.height) // 2)
    canvas.alpha_composite(resized, offset)
    return canvas


def main() -> None:
    source = Image.open(SOURCE_ICON)
    emblem = extract_emblem(source)

    for folder, size in DENSITIES.items():
        output = RES_DIR / folder / "ic_launcher.png"
        icon = render_icon(emblem, size, LEGACY_CANVAS_SCALE)
        icon.save(output, format="PNG", optimize=True)
        print(f"Wrote {output} ({size}x{size})")

    drawable_output = RES_DIR / "drawable" / "ic_launcher.png"
    legacy_notification = render_icon(emblem, 144, LEGACY_CANVAS_SCALE)
    legacy_notification.save(drawable_output, format="PNG", optimize=True)
    print(f"Wrote {drawable_output} (144x144)")

    for folder, size in FOREGROUND_DENSITIES.items():
        output = RES_DIR / folder / "ic_launcher_foreground.png"
        foreground = render_foreground(emblem, size, FOREGROUND_CANVAS_SCALE)
        foreground.save(output, format="PNG", optimize=True)
        print(f"Wrote {output} ({size}x{size})")


if __name__ == "__main__":
    main()
