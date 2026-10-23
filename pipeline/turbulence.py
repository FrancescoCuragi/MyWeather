"""Clear-air turbulence indicator from GFS winds: the Ellrod TI1 index.

    TI1 = VWS * DEF

VWS is the vertical wind shear across a layer centred on the flight level, DEF the horizontal deformation of the
wind at that level (stretching plus shearing of the flow). Where both are large the air is being pulled apart
and sheared at once, which is the classic setup for turbulence around jet streams.

It is a simple diagnostic on a coarse model, an indicator for curiosity and planning, NOT a flight-safety tool.
"""
from __future__ import annotations

import numpy as np

EARTH_RADIUS_M = 6_371_000.0
GRID_STEP_DEG = 0.25
# Flight level -> (pressure at the flight level, pressure just below, pressure just above), in hPa.
FLIGHT_LEVELS = {
    "fl300": (300, 350, 250),
    "fl340": (250, 300, 200),
    "fl390": (200, 250, 150),
}
PRESSURES = sorted({p for levels in FLIGHT_LEVELS.values() for p in levels})  # every level the winds are needed at

# Conventional Ellrod thresholds, in units of 1e-7 s^-2.
LIGHT, MODERATE, SEVERE = 4.0, 8.0, 12.0


def standard_height_m(pressure_hpa: float) -> float:
    """Height of a pressure level in the International Standard Atmosphere (good enough for layer thickness)."""
    return 44330.0 * (1.0 - (pressure_hpa / 1013.25) ** 0.1903)


def _smooth(a: np.ndarray) -> np.ndarray:
    """3x3 box blur: periodic in longitude, edge-clamped in latitude. Tames grid-scale noise before differencing."""
    padded = np.pad(a, ((1, 1), (0, 0)), mode="edge")
    rows = padded[:-2] + padded[1:-1] + padded[2:]
    return (np.roll(rows, 1, axis=1) + rows + np.roll(rows, -1, axis=1)) / 9.0


def deformation(u: np.ndarray, v: np.ndarray) -> np.ndarray:
    """Total horizontal deformation sqrt((du/dx - dv/dy)^2 + (dv/dx + du/dy)^2) in 1/s.

    Arrays are (lat, lon) with latitude running from +90 (row 0) to -90, longitude periodic.
    """
    step = np.radians(GRID_STEP_DEG)
    lat = np.radians(90.0 - GRID_STEP_DEG * np.arange(u.shape[0]))
    cos_lat = np.clip(np.cos(lat), 0.05, None)[:, None]  # avoid dividing by ~0 at the poles

    def ddx(a):
        return (np.roll(a, -1, axis=1) - np.roll(a, 1, axis=1)) / (2 * step * EARTH_RADIUS_M * cos_lat)

    def ddy(a):  # rows go southward, so the northward derivative is minus the row derivative
        return -np.gradient(a, axis=0) / (step * EARTH_RADIUS_M)

    stretching = ddx(u) - ddy(v)
    shearing = ddx(v) + ddy(u)
    return np.sqrt(stretching ** 2 + shearing ** 2)


def ellrod(winds: dict[int, tuple[np.ndarray, np.ndarray]], flight_level: str) -> np.ndarray:
    """Ellrod TI1 in 1e-7 s^-2 for one flight level; `winds` maps pressure (hPa) to (u, v) in m/s."""
    centre, below, above = FLIGHT_LEVELS[flight_level]
    thickness = standard_height_m(above) - standard_height_m(below)
    du = winds[above][0] - winds[below][0]
    dv = winds[above][1] - winds[below][1]
    vertical_shear = np.sqrt(du ** 2 + dv ** 2) / thickness  # 1/s
    u, v = winds[centre]
    index = _smooth(vertical_shear * deformation(u, v)) * 1e7
    return np.nan_to_num(index).astype(np.float32)
