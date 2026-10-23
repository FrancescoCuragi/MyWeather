"""Build the cloud and precipitation textures the app's map layers read.

Layers written (each a folder of one PNG per forecast step, plus a shared meta.json):

    clouds-total, clouds-low, clouds-mid, clouds-high
        grayscale; 0..255 = 0..100 % cloud cover
    precip
        red   = rain rate, encoded sqrt(rate / 50 mm/h) * 255   (fine detail at light rain)
        green = snow fraction, 0..255 = 0..1  (1 where the model says precipitation falls as snow)
        full 0.25 degree resolution; the others are 0.5 degree

Output layout (published as-is to GitHub Pages):  out/fields/meta.json, out/fields/<layer>/f<step>.png
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

DEFAULT_STEPS = list(range(0, 121, 3))
WORKERS = 8
MAX_RATE_MM_H = 50.0

# layer -> (GRIB variable, GRIB level, downsample factor)
CLOUDS = {
    "clouds-total": ("TCDC", "entire atmosphere"),
    "clouds-low": ("LCDC", "low cloud layer"),
    "clouds-mid": ("MCDC", "middle cloud layer"),
    "clouds-high": ("HCDC", "high cloud layer"),
}
CLOUD_DOWNSAMPLE = 2


def encode_cloud(percent: np.ndarray) -> Image.Image:
    percent = percent[::CLOUD_DOWNSAMPLE, ::CLOUD_DOWNSAMPLE]
    gray = np.clip(np.rint(percent / 100.0 * 255.0), 0, 255).astype(np.uint8)
    return Image.fromarray(gray, "L")


def encode_precip(prate: np.ndarray, snow: np.ndarray) -> Image.Image:
    rate_mm_h = np.maximum(prate, 0.0) * 3600.0  # PRATE is kg/m^2/s, which equals mm/s
    rgb = np.zeros((*prate.shape, 3), dtype=np.uint8)
    rgb[..., 0] = np.clip(np.rint(np.sqrt(rate_mm_h / MAX_RATE_MM_H) * 255.0), 0, 255)
    rgb[..., 1] = np.clip(np.rint(snow * 255.0), 0, 255)
    return Image.fromarray(rgb, "RGB")


def build_step(run: dt.datetime, step: int, out_dir: Path, work_dir: Path) -> int:
    """Download every field for one forecast step and write all of that step's PNGs."""
    url = gfs.file_url(run, step)
    index = gfs.read_index(url)
    tmp = work_dir / f"f{step:03d}"
    total = 0

    def save(layer: str, image: Image.Image) -> None:
        nonlocal total
        target = out_dir / layer / f"f{step:03d}.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        image.save(target, optimize=True)
        total += target.stat().st_size

    for layer, (variable, level) in CLOUDS.items():
        save(layer, encode_cloud(gfs.fetch_field(url, index, variable, level, tmp / f"{layer}.grib2")))

    prate = gfs.fetch_field(url, index, "PRATE", "surface", tmp / "prate.grib2")
    snow = gfs.fetch_field(url, index, "CSNOW", "surface", tmp / "csnow.grib2")
    save("precip", encode_precip(prate, snow))

    shutil.rmtree(tmp, ignore_errors=True)
    return total


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=Path("out/fields"))
    parser.add_argument("--work", type=Path, default=Path("work/fields"))
    parser.add_argument("--steps", type=int, nargs="*", default=DEFAULT_STEPS)
    parser.add_argument("--run", help="Run time as YYYYMMDDHH (default: newest complete run)")
    args = parser.parse_args()

    run = (
        dt.datetime.strptime(args.run, "%Y%m%d%H")
        if args.run
        else gfs.latest_run(last_step=max(args.steps))
    )
    print(f"Building cloud and precipitation textures for GFS run {run:%Y-%m-%d %H}z, {len(args.steps)} steps")

    # Build into a fresh directory first so a failed run never leaves a half-written tree behind.
    staging = args.out.with_name(args.out.name + ".tmp")
    shutil.rmtree(staging, ignore_errors=True)
    with ThreadPoolExecutor(WORKERS) as pool:
        sizes = list(pool.map(lambda s: build_step(run, s, staging, args.work), args.steps))
    print(f"{len(sizes)} steps, {sum(sizes) / 1e6:.1f} MB total")

    layers = {name: {"kind": "cloud"} for name in CLOUDS}
    layers["precip"] = {"kind": "precip", "maxRate": MAX_RATE_MM_H}
    meta = {
        "run": run.strftime("%Y-%m-%dT%H:00:00Z"),
        "generated": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "steps": args.steps,
        "levels": layers,
    }
    (staging / "meta.json").write_text(json.dumps(meta, indent=2))

    shutil.rmtree(args.out, ignore_errors=True)
    staging.rename(args.out)
    print(f"Wrote {args.out}")


if __name__ == "__main__":
    main()
