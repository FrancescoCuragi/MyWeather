"""Build the wind textures the app's map layer reads.

For every forecast step and wind level we write a PNG where red = eastward wind (u) and green = northward wind
(v), each scaled linearly from [-max, +max] m/s to 0..255, plus a meta.json describing the run and the scaling.
Output layout (published as-is to GitHub Pages):

    out/wind/meta.json
    out/wind/<level>/f<step>.png      level is "surface", "850mb" or "250mb"
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import shutil
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import numpy as np
from PIL import Image

import gfs

# level name -> (GRIB level string, max |wind| in m/s that the 8-bit scale covers)
LEVELS = {
    "surface": ("10 m above ground", 40.0),
    "850mb": ("850 mb", 60.0),
    "250mb": ("250 mb", 110.0),
}
DEFAULT_STEPS = list(range(0, 121, 3))
DOWNSAMPLE = 2  # 0.25 degree source -> 0.5 degree texture
WORKERS = 8


def encode(u: np.ndarray, v: np.ndarray, max_speed: float) -> Image.Image:
    """Pack two wind components into the red and green channels of an RGB image."""
    u = u[::DOWNSAMPLE, ::DOWNSAMPLE]
    v = v[::DOWNSAMPLE, ::DOWNSAMPLE]
    scale = 255.0 / (2 * max_speed)
    rgb = np.zeros((*u.shape, 3), dtype=np.uint8)
    rgb[..., 0] = np.clip(np.rint((u + max_speed) * scale), 0, 255)
    rgb[..., 1] = np.clip(np.rint((v + max_speed) * scale), 0, 255)
    return Image.fromarray(rgb, "RGB")


def build_frame(run: dt.datetime, step: int, level: str, out_dir: Path, work_dir: Path) -> int:
    grib_level, max_speed = LEVELS[level]
    url = gfs.file_url(run, step)
    index = gfs.read_index(url)
    tmp = work_dir / f"f{step:03d}_{level}"
    u = gfs.fetch_field(url, index, "UGRD", grib_level, tmp / "u.grib2")
    v = gfs.fetch_field(url, index, "VGRD", grib_level, tmp / "v.grib2")
    target = out_dir / level / f"f{step:03d}.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    encode(u, v, max_speed).save(target, optimize=True)
    shutil.rmtree(tmp, ignore_errors=True)
    return target.stat().st_size


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=Path("out/wind"))
    parser.add_argument("--work", type=Path, default=Path("work"))
    parser.add_argument("--steps", type=int, nargs="*", default=DEFAULT_STEPS)
    parser.add_argument("--levels", nargs="*", default=list(LEVELS), choices=list(LEVELS))
    parser.add_argument("--run", help="Run time as YYYYMMDDHH (default: newest complete run)")
    args = parser.parse_args()

    run = (
        dt.datetime.strptime(args.run, "%Y%m%d%H")
        if args.run
        else gfs.latest_run(last_step=max(args.steps))
    )
    print(f"Building wind textures for GFS run {run:%Y-%m-%d %H}z, {len(args.steps)} steps, {args.levels}")

    # Build into a fresh directory first so a failed run never leaves a half-written tree behind.
    staging = args.out.with_name(args.out.name + ".tmp")
    shutil.rmtree(staging, ignore_errors=True)
    jobs = [(step, level) for level in args.levels for step in args.steps]
    with ThreadPoolExecutor(WORKERS) as pool:
        sizes = list(pool.map(lambda j: build_frame(run, j[0], j[1], staging, args.work), jobs))
    print(f"{len(sizes)} frames, {sum(sizes) / 1e6:.1f} MB total, largest {max(sizes) / 1e3:.0f} KB")

    meta = {
        "run": run.strftime("%Y-%m-%dT%H:00:00Z"),
        "generated": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "steps": args.steps,
        "width": 1440 // DOWNSAMPLE,
        "height": 721 // DOWNSAMPLE,
        "levels": {name: {"maxSpeed": LEVELS[name][1]} for name in args.levels},
    }
    (staging / "meta.json").write_text(json.dumps(meta, indent=2))

    shutil.rmtree(args.out, ignore_errors=True)
    staging.rename(args.out)
    print(f"Wrote {args.out}")


if __name__ == "__main__":
    main()
