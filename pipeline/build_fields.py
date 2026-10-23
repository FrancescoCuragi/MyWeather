"""Build the cloud, precipitation and wave textures the app's map layers read.

Layers written (each a folder of one PNG per forecast step, plus a shared meta.json):

    clouds-total, clouds-low, clouds-mid, clouds-high
        grayscale; 0..255 = 0..100 % cloud cover
    precip
        red   = rain rate, encoded sqrt(rate / 50 mm/h) * 255   (fine detail at light rain)
        green = snow fraction, 0..255 = 0..1  (1 where the model says precipitation falls as snow)
        full 0.25 degree resolution; the clouds are 0.5 degree
    waves-height, waves-swell   (GFS-Wave, 0.25 degree; land is empty)
        red = height, 0..255 = 0..16 m;  green/blue = east/north unit vector of the travel direction,
        0..255 = -1..1
    waves-period
        grayscale, 0..255 = 0..30 s
    rainacc-3h, rainacc-24h   (rain totals over the 3 / 24 hours ending at each step)
        grayscale, byte = sqrt(mm / max) * 255 with max 60 mm (3 h) and 150 mm (24 h); 0.5 degree
    cat-fl300, cat-fl340, cat-fl390   (clear-air turbulence, Ellrod index; see turbulence.py)
        grayscale, 0..255 = 0..20 (1e-7 s^-2); about 4 is light, 8 moderate, 12 severe

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
import turbulence

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


MAX_WAVE_M = 16.0
MAX_PERIOD_S = 30.0
MAX_TURBULENCE = 20.0   # Ellrod index (1e-7 s^-2) at the top of the texture range
TURBULENCE_DOWNSAMPLE = 2


def encode_turbulence(index: np.ndarray) -> Image.Image:
    index = index[::TURBULENCE_DOWNSAMPLE, ::TURBULENCE_DOWNSAMPLE]
    gray = np.clip(np.rint(index / MAX_TURBULENCE * 255.0), 0, 255).astype(np.uint8)
    return Image.fromarray(gray, "L")


def encode_wave(height: np.ndarray, direction_from: np.ndarray) -> Image.Image:
    """Wave height in red; the direction the waves travel TOWARD as an (east, north) unit vector in green/blue.

    Land (NaN) becomes height 0 and a zero vector (128, 128), which the app reads as "no data here".
    """
    sea = ~np.isnan(height)
    toward = np.radians((np.nan_to_num(direction_from) + 180.0) % 360.0)
    east = np.where(sea, np.sin(toward), 0.0)
    north = np.where(sea, np.cos(toward), 0.0)
    rgb = np.zeros((*height.shape, 3), dtype=np.uint8)
    rgb[..., 0] = np.clip(np.rint(np.nan_to_num(height) / MAX_WAVE_M * 255.0), 0, 255)
    # Direction only needs ~4 degrees of precision for flowing particles; 32 levels per axis compresses far better.
    for channel, component in ((1, east), (2, north)):
        level = np.rint((component * 0.5 + 0.5) * 31.0)  # 0..31
        rgb[..., channel] = np.where(sea, level * 8 + 4, 128).astype(np.uint8)
    return Image.fromarray(rgb, "RGB")


def encode_period(period: np.ndarray) -> Image.Image:
    gray = np.clip(np.rint(np.nan_to_num(period) / MAX_PERIOD_S * 255.0), 0, 255).astype(np.uint8)
    return Image.fromarray(gray, "L")


ACCUMULATIONS = {
    # layer name: (window in hours, mm at the top of the texture range)
    "rainacc-3h": (3, 60.0),
    "rainacc-24h": (24, 150.0),
}
ACCUMULATION_DOWNSAMPLE = 2


def encode_accumulation(mm: np.ndarray, max_mm: float) -> Image.Image:
    """Grayscale, sqrt-encoded so light totals keep detail: byte = sqrt(mm / max) * 255."""
    gray = np.clip(np.rint(np.sqrt(np.clip(mm, 0, None) / max_mm) * 255.0), 0, 255).astype(np.uint8)
    return Image.fromarray(gray, "L")


def cumulative_rain(run: dt.datetime, step: int, work_dir: Path) -> np.ndarray:
    """Total rain (mm) from the start of `run` to `step`, already thinned to the accumulation texture grid."""
    if step == 0:
        return np.zeros((361, 720), dtype=np.float32)  # nothing has fallen yet at the start of a run
    url = gfs.file_url(run, step)
    field = gfs.fetch_field(url, gfs.read_index(url), "APCP", "surface", work_dir / f"{run:%Y%m%d%H}_f{step:03d}.grib2",
                            record="cumulative")
    return np.nan_to_num(field)[::ACCUMULATION_DOWNSAMPLE, ::ACCUMULATION_DOWNSAMPLE]


def build_accumulations(run: dt.datetime, steps: list[int], out_dir: Path, work_dir: Path) -> int:
    """Rain totals over the 3 and 24 hours ending at every step.

    GFS only publishes running totals since the start of the run, so a window is a difference of two of them. For the
    first hours of a run the window reaches back before it started; those come from the run 24 hours earlier.
    """
    previous = run - dt.timedelta(hours=24)
    try:
        has_previous = gfs._session.head(gfs.file_url(previous, 45) + ".idx", timeout=30).status_code == 200
    except Exception:
        has_previous = False
    longest = max(window for window, _ in ACCUMULATIONS.values())

    needed = {(run, s) for s in steps} | {(run, s - w) for s in steps for w, _ in ACCUMULATIONS.values() if s - w >= 0}
    if has_previous:
        for s in steps:
            for w, _ in ACCUMULATIONS.values():
                if s - w < 0:  # the window began before this run: both ends come from the earlier run
                    needed |= {(previous, 24 + s), (previous, 24 + s - w)}
    with ThreadPoolExecutor(WORKERS) as pool:
        keys = sorted(needed)
        totals = dict(zip(keys, pool.map(lambda k: cumulative_rain(k[0], k[1], work_dir), keys)))

    written = 0
    for layer, (window, max_mm) in ACCUMULATIONS.items():
        for step in steps:
            start = step - window
            if start >= 0:
                mm = totals[(run, step)] - totals[(run, start)]
            elif has_previous:
                mm = totals[(previous, 24 + step)] - totals[(previous, 24 + start)]
            else:
                mm = totals[(run, step)]  # best effort: only the time since the run began
            target = out_dir / layer / f"f{step:03d}.png"
            target.parent.mkdir(parents=True, exist_ok=True)
            encode_accumulation(mm, max_mm).save(target, optimize=True)
            written += target.stat().st_size
    return written


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

    wave_file = gfs.wave_url(run, step)
    wave_index = gfs.read_index(wave_file)

    def wave_field(variable: str, level: str, name: str) -> np.ndarray:
        return gfs.fetch_field(wave_file, wave_index, variable, level, tmp / f"{name}.grib2")

    height = wave_field("HTSGW", "surface", "hs")
    save("waves-height", encode_wave(height, wave_field("DIRPW", "surface", "dir")))
    save("waves-period", encode_period(wave_field("PERPW", "surface", "period")))
    save("waves-swell", encode_wave(wave_field("SWELL", "1 in sequence", "swell"),
                                    wave_field("SWDIR", "1 in sequence", "swelldir")))

    winds = {
        pressure: (
            gfs.fetch_field(url, index, "UGRD", f"{pressure} mb", tmp / f"u{pressure}.grib2"),
            gfs.fetch_field(url, index, "VGRD", f"{pressure} mb", tmp / f"v{pressure}.grib2"),
        )
        for pressure in turbulence.PRESSURES
    }
    for flight_level in turbulence.FLIGHT_LEVELS:
        save(f"cat-{flight_level}", encode_turbulence(turbulence.ellrod(winds, flight_level)))

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
    acc_bytes = build_accumulations(run, args.steps, staging, args.work)
    print(f"{len(sizes)} steps, {(sum(sizes) + acc_bytes) / 1e6:.1f} MB total")

    layers = {name: {"kind": "cloud"} for name in CLOUDS}
    layers["precip"] = {"kind": "precip", "maxRate": MAX_RATE_MM_H}
    layers["waves-height"] = {"kind": "wave", "maxHeight": MAX_WAVE_M}
    layers["waves-swell"] = {"kind": "wave", "maxHeight": MAX_WAVE_M}
    layers["waves-period"] = {"kind": "period", "maxPeriod": MAX_PERIOD_S}
    for name, (window, max_mm) in ACCUMULATIONS.items():
        layers[name] = {"kind": "rainacc", "hours": window, "maxMm": max_mm}
    for flight_level in turbulence.FLIGHT_LEVELS:
        layers[f"cat-{flight_level}"] = {"kind": "turbulence", "maxIndex": MAX_TURBULENCE}
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
