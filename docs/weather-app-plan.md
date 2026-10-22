# Personal Weather App — Project Plan

> A modern, ad-free, Windy-style weather app for Android, built and run for **€0**, for personal use only.

*Plan date: 22 October 2026 · Data-source terms checked the same day (they change — re-verify before building).*

---

## 1. Goals & Constraints

| | |
|---|---|
| **Users** | Just me (one Android phone — OnePlus 12, Android 16) |
| **Developer** | Beginner, prefers **Kotlin** → build with AI help (Claude / Claude Code), small steps |
| **Coverage** | **Worldwide** — forecasts and map layers for the whole globe |
| **Backend** | Personal **GitHub account** runs a scheduled data pipeline (confirmed) |
| **Budget** | €0 — no paid APIs, no paid hosting, no store fees |
| **Distribution** | Sideloaded APK (no Google Play account needed → saves the $25 fee) |
| **Must feel** | Fast, smooth, modern, no ads, no clutter |
| **Benchmark** | Windy: animated map layers, model comparison, timeline scrubbing, detailed point forecast |

**Why personal use matters:** almost every good free weather source is free *only* for non-commercial / personal use. That's exactly this project, so it unlocks sources a commercial app couldn't use.

**Non-goals:** iOS, multi-user accounts, monetization, publishing to a store.

---

## 2. Free Data Sources (the foundation)

| Need | Source | Free terms | Notes |
|---|---|---|---|
| Point forecasts (hourly/daily, 16 days) | **Open-Meteo** | No key, non-commercial; ~10,000 calls/day, 5,000/hour, 600/min | Many models: ECMWF, ICON, GFS, AROME, ARPEGE, UKMO… Also air quality, marine, historical, geocoding |
| City search | **Open-Meteo Geocoding** | Free, no key | Name → lat/lon |
| Radar (past) | **RainViewer** | Free for personal use, attribution required | Since Jan 2026: past radar only, **max zoom 7**, one colour scheme, no satellite, no nowcast |
| Radar (better, optional) | **LibreWXR** (self-hosted) | Open source | Drop-in RainViewer-compatible replacement restoring full zoom/colours. Needs somewhere to run |
| Gridded model data (for map layers) | **NOAA GFS**, **DWD ICON** open data, **ECMWF open data** | Free / open licences | Raw GRIB2 files → processed into map textures (see §5) |
| Satellite imagery | **NASA GIBS** (WMTS tiles), EUMETSAT/EUMETView | Free | Cloud imagery, IR, true colour |
| Weather warnings (Europe) | **MeteoAlarm** feeds | Free | Covers Belgium (KMI/IRM) |
| Base map | **OpenFreeMap** or **Protomaps** + **MapLibre** | Free, no key | Vector tiles, dark/light styles |

### ⚠️ Sources that *don't* fit
- **Windy Map Forecast API** — the free tier is "testing" only, explicitly not allowed in production (even personal/internal), and limited to a few models/layers. Professional costs ~€990/year. → **We rebuild the Windy experience ourselves** instead of embedding Windy.
- **OpenWeatherMap / Tomorrow.io** — need keys, tighter free limits; not needed given Open-Meteo.

---

## 3. Feature Scope

### MVP (v1) — "Better than a stock weather app"
- [ ] **Home screen**: current conditions, feels-like, hi/lo, short text summary
- [ ] **Hourly strip** (48 h): temp, precip chance + amount, wind, icons
- [ ] **Daily forecast** (14–16 days) with expandable detail
- [ ] **Saved locations**: search, add, reorder, swipe between them
- [ ] **GPS location** ("my location" as first card)
- [ ] **Radar map**: animated past radar loop (RainViewer) on MapLibre
- [ ] **Details panel**: UV, humidity, pressure, visibility, dew point, sunrise/sunset, moon phase, air quality
- [ ] **Units & settings**: °C/°F, km/h / m/s / kt / Bft, mm/in, 24 h
- [ ] **Dynamic theming**: Material You colours, dark mode, background reflecting conditions
- [ ] **Offline cache**: last forecast shown instantly on launch

### v2 — "The Windy part"
- [ ] **Full-screen weather map** with layer picker:
  - Wind (animated particles 🌬️), gusts
  - Temperature, feels-like
  - Precipitation / rain & snow accumulation
  - Clouds (low/mid/high/total), satellite
  - Pressure (isobars), humidity, CAPE/thunderstorm risk
  - Waves & swell (Open-Meteo Marine), snow depth, freezing level
  - Air quality (PM2.5, ozone)
  - Clear-air turbulence at cruise flight levels + jet stream ✈️
- [ ] **Timeline scrubber** (play/pause through forecast hours)
- [ ] **Tap anywhere** on the map → mini point forecast
- [ ] **Model switcher**: ECMWF / ICON / GFS / AROME
- [ ] **Model comparison** view (multiple models side by side for one location)
- [ ] **Meteogram** chart (temp, precip, wind, pressure over time)

### v3 — Nice-to-haves
- [ ] Home-screen **widgets** (Glance)
- [ ] **Notifications**: rain starting soon, severe-weather warnings (MeteoAlarm), daily morning summary
- [ ] Wind/sounding/altitude views (pressure-level winds, temperature by altitude)
- [ ] Favourite-spot "activity scores" (e.g. good for cycling / running)
- [ ] Historical "this day last year" comparison

---

## 4. Tech Stack (all free)

### Decision: Kotlin app + web-based map screen (hybrid)

Given little coding experience, the best trade-off is:
- **Everything except the map** in **Kotlin + Jetpack Compose** (native, smooth, Material You, widgets).
- **The map screen** as a full-screen **WebView running MapLibre GL JS**. Windy-style wind particles and colour heatmaps already exist as open-source WebGL code in JavaScript; writing them natively in OpenGL ES is one of the hardest parts of the whole project and not worth it for a beginner. On a OnePlus 12 a WebGL map runs smoothly.
- Kotlin ↔ map talk through a small JavaScript bridge (e.g. "show layer = wind, time = +12 h", "user tapped lat/lon").

You'll mostly write Kotlin; the map's JavaScript is a few self-contained files that Claude can generate and maintain.

| Layer | Choice | Why |
|---|---|---|
| Language/UI | **Kotlin + Jetpack Compose**, Material 3 | Your preference; modern native Android |
| Architecture | Single-module app, MVVM (ViewModel + StateFlow) | Simple, well documented, easy to get help with |
| Maps | **MapLibre GL JS** in a WebView | Open source, no key, WebGL custom layers |
| Wind particles | WebGL particle layer (based on open-source "webgl-wind" approach) | Proven technique used by Windy-like maps |
| Heatmaps | WebGL raster layer colouring the data textures | Temperature, rain, clouds, etc. |
| Networking | Retrofit + kotlinx.serialization | Most tutorials and examples use it |
| Storage | Room (locations, cache) + DataStore (settings) | Standard Android choices |
| Charts | Vico (Compose charts) | Free, modern |
| Background | WorkManager | Refresh + notifications |
| IDE | Android Studio | Free |
| Build/CI + data pipeline | GitHub Actions | Free on a public repo |

### How to build it as a beginner
- Use **Claude Code** (or Claude chat) to generate code step by step; you run it in Android Studio on your phone via USB.
- One feature per step, run it, commit to GitHub, then the next — never big leaps.
- Learn the basics alongside: the official free *Android Basics with Compose* course is the right level.

---

## 5. Architecture

```
┌──────────────────────────── Android app ────────────────────────────┐
│  UI (Compose)  →  ViewModels  →  Repository  →  Cache (Room)        │
│                                       │                              │
└───────────────────────────────────────┼──────────────────────────────┘
                                        │ HTTPS
        ┌───────────────┬───────────────┼──────────────────┬──────────────┐
        ▼               ▼               ▼                  ▼              ▼
   Open-Meteo      RainViewer      NASA GIBS          MeteoAlarm    Your layer files
  (forecasts,      (radar tiles)  (satellite)         (warnings)    (wind/temp/etc. textures)
   geocoding)                                                              ▲
                                                                           │
                                         ┌─────────────────────────────────┘
                                         │  (v2) Free "data pipeline"
                                  GitHub Actions cron (every 6 h)
                                  1. Download GFS / ICON / ECMWF GRIB2
                                  2. Convert global grid → PNG-encoded
                                     textures (u/v wind, temp, precip…)
                                  3. Publish to GitHub Pages
```

### The map-layer challenge (the hard part)
Open-Meteo gives **point** forecasts, but Windy-style maps need **gridded** data. Two options:

| Option | How | Pros | Cons |
|---|---|---|---|
| **A. Sample Open-Meteo on a grid** | Request a coarse grid of points for the visible area (multi-location requests) and interpolate on device | No server, quick to build | Burns API calls, coarse, slower on zoom/pan |
| **B. Own pipeline ✅ chosen** | GitHub Actions downloads open GRIB data on a schedule, converts to compact image textures, hosts them free | Smooth, fast, global or Europe-wide, works like Windy/earth.nullschool | One-off setup effort (Python: `xarray`, `cfgrib`, `Pillow`) |

**Decision: Option B, worldwide.** Option A doesn't scale to a whole-globe view (thousands of points per frame), so the GitHub pipeline is core to the project, not optional.

### Worldwide pipeline design

| Item | Choice |
|---|---|
| Runner | GitHub Actions, **public repo** (unlimited free minutes) |
| Schedule | Every 6 h, ~5 h after each model run (00/06/12/18 UTC) |
| Default model | **GFS 0.25°** (NOAA, public domain) — downloaded from NOAA's open-data bucket, fetching only the needed variables via byte ranges (`.idx` files) |
| Extra models (later) | **ECMWF IFS open data 0.25°**, **ICON global** (DWD) — for the model switcher |
| Processing | Python: `xarray` + `cfgrib` → normalise → pack into PNG textures (e.g. wind u/v in R/G channels) + small JSON metadata (value ranges, timestamps) |
| Grid for maps | 0.5° global (720 × 361 px per frame) — sharp enough for a phone, small files |
| Time steps | 3-hourly to +5 days, 6-hourly to +10 days (~60 frames) |
| Layers in v2 | Your 5 priority layers first (see below), then temperature, pressure, CAPE |
| Hosting | **GitHub Pages** (published by the Action); keep only the latest run |
| Fallback | If a run fails, the previous run stays published; app shows "data from HH:00" |

**Size budget:** ~7 layers × ~60 frames × ~100–300 KB ≈ **50–120 MB per run**, overwritten each time. That fits under GitHub Pages' 1 GB site limit and personal bandwidth is tiny (the phone only downloads frames you actually look at, and caches them). Zooming in shows the 0.5° grid smoothly interpolated, the same principle Windy uses at low zoom.

### Priority layers (your top 5, in build order)

| # | Layer | What you'll see | Data source (all free, global) | Difficulty |
|---|---|---|---|---|
| 1 | **Wind** | Animated particles + speed colours; gusts; altitude picker (surface, 850 hPa, **250 hPa jet stream**) | GFS `UGRD`/`VGRD` at 10 m and pressure levels, `GUST` | ⭐⭐⭐ (particles) |
| 2 | **Clouds** | Total / low / mid / high cover, plus real satellite imagery | GFS `TCDC` per layer; NASA GIBS satellite tiles | ⭐ |
| 3 | **Precipitation** | Rain/snow rate, 3 h / 24 h accumulation, rain vs snow; radar overlay on top | GFS `PRATE`/`APCP`, precip type; RainViewer radar | ⭐⭐ |
| 4 | **Waves** | Significant wave height, swell height/period, direction arrows | **GFS-Wave (WAVEWATCH III) 0.25°**, published by NOAA alongside GFS | ⭐⭐ |
| 5 | **Clear-air turbulence (CAT)** | Turbulence risk at cruise flight levels (FL300 / FL340 / FL390) | **Computed in the pipeline** from GFS upper-level winds (see below) | ⭐⭐⭐ |

#### How clear-air turbulence works in this plan
No free source hands out a ready-made, global, open-licence turbulence map, so the pipeline **calculates one**:
- **Method:** the **Ellrod index** — a standard forecasting index that combines *vertical wind shear* and *horizontal deformation* of the wind between two flight levels. It's a few lines of NumPy on GFS winds at 300 / 250 / 200 hPa (≈ FL300 / FL340 / FL390), which the pipeline already downloads for the jet-stream layer.
- **Display:** colour scale none → light → moderate → severe, with the jet stream optionally drawn on top (CAT clusters around jet streams).
- **Possible upgrade:** the official aviation turbulence forecast (**WAFS EDR**, 0.25°, FL100–FL450) is published through aviationweather.gov's WIFS service. Whether it's openly downloadable without an aviation-user account is unclear — worth checking once the basic version works. If it's open, it can replace or sit beside the Ellrod layer.
- ⚠️ This is an indicator for curiosity/planning, **not a flight-safety tool**: Ellrod is a simple index and GFS is coarse for turbulence.

**Extra pipeline load:** GFS-Wave is a separate small file per time step, and turbulence needs only 3 more pressure levels of wind. The size budget above still holds.

**Point forecasts** (tapping the map, city screens) still come from Open-Meteo, which has higher-resolution regional models, so close-up detail stays good.

---

## 6. UX / Design Direction

- **Layout:** location pager on top, glanceable "hero" card, hourly strip, daily list, details grid. Map one tap away (bottom nav: *Forecast · Map · Locations · Settings*).
- **Style:** Material 3 with dynamic colour; subtle animated backgrounds (rain, clouds, sun) that don't hurt readability.
- **Map:** full-screen, dark base map by default, floating layer chip bar, timeline at the bottom, legend auto-shown per layer.
- **Speed:** cached data on open → refresh silently; aim for < 1 s to first content.
- **Gestures:** swipe between cities, long-press map for point forecast, pull-to-refresh.

---

## 7. Roadmap

| Phase | Deliverable | Rough effort* |
|---|---|---|
| 0. Setup | Install Android Studio, enable USB debugging on the phone, create GitHub repo, "Hello world" app running on the phone | 1 weekend |
| 1. Forecast core | Open-Meteo client, home screen, hourly/daily, caching | 2–3 weeks |
| 2. Locations | Search, GPS, saved list, swipe pager | 1–2 weeks |
| 3. Map shell + radar | WebView + MapLibre GL JS, base map, RainViewer loop | 1–2 weeks |
| **→ MVP done** | Daily-driver app | **~2 months** |
| 4. Data pipeline | GitHub Action: GFS → PNG textures → GitHub Pages (start with **wind** only) | 2 weeks |
| 5. Wind layer | Speed heatmap + timeline scrubber + WebGL animated particles, altitude picker | 2–3 weeks |
| 6. Clouds & precipitation | Cloud layers, satellite (NASA GIBS), rain/snow + accumulation | 1–2 weeks |
| 7. Waves & turbulence | GFS-Wave layers; Ellrod CAT index at FL300/340/390 + jet stream | 2 weeks |
| 8. Models & meteogram | ECMWF/ICON in pipeline, model switcher, comparison, charts | 2 weeks |
| 9. Widgets & alerts | Glance widget, WorkManager notifications, warnings | 2 weeks |

\*Evenings/weekends pace for a beginner working with Claude writing most of the code. Each phase ends with a working app — you can stop at any point and still have something usable.

---

## 8. Cost Check — €0

| Item | Cost |
|---|---|
| Android Studio, Kotlin, MapLibre, libraries | Free / open source |
| Open-Meteo, RainViewer, NASA GIBS, MeteoAlarm, NOAA/DWD/ECMWF open data | Free (personal / open licence) |
| GitHub repo + Actions + Pages | Free (public repo = unlimited Actions minutes; Pages hosts the map data) |
| Google Play | **Not needed** — sideload the APK |
| Signing key | Free (self-generated) |

Requirements to stay at €0: keep personal, cache aggressively, show the attributions (Open-Meteo, RainViewer, map data © OpenStreetMap).

### No secrets in the repo
The repo is public, so nothing private ever goes in it:
- **No API keys exist to leak** — Open-Meteo, RainViewer, NOAA/DWD/ECMWF, NASA GIBS and OpenFreeMap are all keyless.
- **APK signing keystore** — the only real secret. Kept on your PC (backed up) and listed in `.gitignore`; if the Action ever builds the APK, it's stored as an encrypted **GitHub Secret**, never in code.
- **Your locations** — saved cities live only in the app on your phone, never in the repo.
- **`local.properties`** and similar machine files — in `.gitignore` from day one (Android Studio's default template already does this).

---

## 9. Risks

| Risk | Mitigation |
|---|---|
| Free services change terms (RainViewer already did in Jan 2026) | Repository layer abstracts each source → swap providers easily; LibreWXR as radar fallback |
| Radar zoom capped at 7 | Fine for regional view; self-host LibreWXR or use national radar if more detail wanted |
| Wind-particle rendering is technically demanding | Start with WebView + existing JS libs; go native later if needed |
| Pipeline maintenance | Start with 2 layers, add more once stable; previous run stays online if a run fails |
| Weather warnings aren't global | No single free worldwide source: MeteoAlarm covers Europe, NWS covers the US; other regions show no warnings |
| Beginner learning curve | Small phases, each ending in a working app; Claude writes and explains the code |

---

## 10. Questions

### Answered ✅
- **Coding experience:** little, prefers Kotlin → Kotlin + Compose, map in WebView, Claude-assisted
- **Coverage:** whole world → global GFS pipeline, 0.5° map grid
- **GitHub:** yes → GitHub Actions + Pages for map data
- **Public repo:** yes, as long as nothing secret is hardcoded → see "No secrets in the repo" in §8
- **Priority layers:** wind, clouds, precipitation, waves, clear-air turbulence → see "Priority layers" in §5

### Still open ❓
1. **Android only?** — Planned Android-only since you have no Apple devices. Confirm?
2. **Radar detail** — Is RainViewer at zoom 7 (regional) enough, or do you want street-level radar (needs self-hosting or a national source)?
3. **Widgets & notifications** — Wanted in v1, or later?
4. **Visual style** — Clean minimal (Apple Weather-like), data-dense (Windy-like), or both via a toggle?
5. **Timeline** — Any target date, and roughly how many hours per week can you put in?

---

## Sources
- [Open-Meteo rate limits (blog)](https://openmeteo.substack.com/p/weather-data-for-multiple-locations)
- [Rain Viewer API](https://rainviewer.com/api.html) · [Transition FAQ](https://www.rainviewer.com/api/transition-faq.html)
- [LibreWXR](https://github.com/JoshuaKimsey/LibreWXR)
- [WAFS 0.25° blended turbulence collection (aviationweather.gov WIFS)](https://aviationweather.gov/wifs/api/collections/EGRR_WAFSHZDS_BLENDED_TURB_0P25?f=json)
- [Windy Map Forecast API](https://api.windy.com/map-forecast) · [docs](https://api4.windy.com/map-forecast/docs)
