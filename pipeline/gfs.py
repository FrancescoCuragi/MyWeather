"""Download selected fields from NOAA's public GFS 0.25 degree dataset.

NOAA publishes each forecast step as one ~500 MB GRIB2 file plus a small `.idx` index listing the byte range of
every field. We read the index, then fetch only the byte ranges we need (about 1 MB per field).
"""
from __future__ import annotations

import datetime as dt
import re
import threading
import time
from pathlib import Path

import cfgrib
import numpy as np
import requests

BUCKET = "https://noaa-gfs-bdp-pds.s3.amazonaws.com"
RUN_HOURS = (0, 6, 12, 18)
# A run is complete (all forecast steps published) roughly this long after its nominal time.
PUBLISH_DELAY = dt.timedelta(hours=5)

_session = requests.Session()
# The native GRIB library (eccodes) is not thread-safe: downloads run in parallel, decoding takes turns.
_decode_lock = threading.Lock()


def file_url(run: dt.datetime, step: int) -> str:
    return (
        f"{BUCKET}/gfs.{run:%Y%m%d}/{run:%H}/atmos/"
        f"gfs.t{run:%H}z.pgrb2.0p25.f{step:03d}"
    )


def wave_url(run: dt.datetime, step: int) -> str:
    """The GFS-Wave (WAVEWATCH III) global 0.25 degree file; it is small, and NaN over land."""
    return (
        f"{BUCKET}/gfs.{run:%Y%m%d}/{run:%H}/wave/gridded/"
        f"gfswave.t{run:%H}z.global.0p25.f{step:03d}.grib2"
    )


def latest_run(now: dt.datetime | None = None, last_step: int = 120) -> dt.datetime:
    """Newest run whose last needed step is already published."""
    now = now or dt.datetime.now(dt.timezone.utc).replace(tzinfo=None)
    candidate = (now - PUBLISH_DELAY).replace(minute=0, second=0, microsecond=0)
    candidate = candidate.replace(hour=max(h for h in RUN_HOURS if h <= candidate.hour))
    for _ in range(8):  # walk back up to two days
        url = file_url(candidate, last_step) + ".idx"
        if _session.head(url, timeout=30).status_code == 200:
            return candidate
        candidate -= dt.timedelta(hours=6)
    raise RuntimeError("No complete GFS run found in the last two days")


def read_index(url: str) -> list[tuple[str, int, int | None]]:
    """Parse an .idx file into (record_key, start_byte, end_byte_or_None) triples.

    record_key looks like "UGRD:10 m above ground:3 hour fcst".
    """
    text = _get(url + ".idx").decode()
    entries = []
    for line in text.strip().splitlines():
        _, start, _, variable, level, forecast, *_ = line.split(":")
        entries.append((f"{variable}:{level}:{forecast}", int(start)))
    return [
        (key, start, entries[i + 1][1] - 1 if i + 1 < len(entries) else None)
        for i, (key, start) in enumerate(entries)
    ]


def fetch_field(url: str, index, variable: str, level: str, dest: Path) -> np.ndarray:
    """Download one field and return it as a 2-D float32 array (lat 90 -> -90, lon 0 -> 360)."""
    pattern = re.compile(rf"^{re.escape(variable)}:{re.escape(level)}:")
    matches = [(key, s, e) for key, s, e in index if pattern.match(key)]
    if len(matches) > 1:
        # Fields like cloud cover and precipitation also come as 3-hour averages and accumulations;
        # we want the value valid at the step itself ("anl" for step 0, "N hour fcst" otherwise).
        matches = [m for m in matches if re.fullmatch(r"anl|\d+ hour fcst", m[0].split(":", 2)[2])]
    if len(matches) != 1:
        raise LookupError(f"{variable} at {level}: expected 1 record, found {len(matches)}")
    _, start, end = matches[0]
    data = _get(url, byte_range=(start, end))
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)

    with _decode_lock:
        datasets = cfgrib.open_datasets(str(dest), backend_kwargs={"indexpath": ""})
        array = next(iter(datasets[0].data_vars.values()))
        values = np.asarray(array.values, dtype=np.float32)
        flip = array.latitude.values[0] < array.latitude.values[-1]
        for dataset in datasets:
            dataset.close()
    return values[::-1] if flip else values  # north is the first row


def _get(url: str, byte_range: tuple[int, int | None] | None = None, attempts: int = 4) -> bytes:
    headers = {}
    if byte_range:
        start, end = byte_range
        headers["Range"] = f"bytes={start}-{'' if end is None else end}"
    for attempt in range(attempts):
        try:
            response = _session.get(url, headers=headers, timeout=120)
            response.raise_for_status()
            return response.content
        except requests.RequestException:
            if attempt == attempts - 1:
                raise
            time.sleep(2 ** attempt)
    raise AssertionError("unreachable")
