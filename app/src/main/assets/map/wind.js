/*
 * Weather field layers for the MyWeather map, drawn on canvases laid over the MapLibre map: wind (speed colours
 * plus animated particles), cloud cover and precipitation. The data comes from PNG textures published by the
 * pipeline (see pipeline/build_wind.py and pipeline/build_fields.py), on a regular grid with longitude 0..360
 * and latitude 90..-90:
 *   wind    red = eastward wind (u), green = northward wind (v), scaled from [-maxSpeed, +maxSpeed] m/s to 0..255
 *   cloud   grayscale, 0..255 = 0..100 % cover
 *   precip  red = sqrt(rain rate / 50 mm/h) * 255, green = snow fraction * 255
 *   wave    red = height (0..16 m), green/blue = east/north unit vector of the direction the waves travel
 *   period  grayscale, 0..255 = 0..30 s
 *   turbulence  grayscale, 0..255 = Ellrod index 0..20 (1e-7 s^-2): ~4 light, ~8 moderate, ~12 severe
 */
(function () {
  'use strict';

  var SITE = 'https://francescocuragi.github.io/MyWeather/';
  var CLOUD_RGB = [236, 242, 252];
  var MAX_RAIN_RATE = 50;       // mm/h at the top of the precip texture range

  /** Which published folder and which kind of data each layer name (the texture subfolder) uses. */
  function layerInfo(level) {
    if (level.indexOf('clouds-') === 0) return { base: 'fields', kind: 'cloud' };
    if (level === 'precip') return { base: 'fields', kind: 'precip' };
    if (level === 'waves-period') return { base: 'fields', kind: 'period' };
    if (level.indexOf('cat-') === 0) return { base: 'fields', kind: 'turbulence' };
    if (level.indexOf('waves-') === 0) return { base: 'fields', kind: 'wave' };
    return { base: 'wind', kind: 'wind' };
  }
  var CELL = 4;                 // heatmap resolution: one sample per CELL x CELL css pixels
  var PIXELS_PER_MS = 0.03;     // particle speed: css pixels moved per frame per m/s of wind
  var MAX_AGE = 90;             // frames before a particle respawns
  var FADE = 0.88;              // trail persistence per frame (lower = shorter trails)
  var WAVE_PX = 0.3;             // wave crest drift: css pixels per frame (before the height boost)
  var CREST_LIFE = 130;          // frames a wave crest lives, fading in and out
  var CACHE_FRAMES = 16;
  // Speed (m/s) at which the colour scale tops out, per level.
  var COLOR_MAX = { surface: 25, '850mb': 40, '250mb': 80 };

  var STOPS = [
    [0.0, 60, 90, 200], [0.2, 40, 170, 200], [0.4, 60, 200, 120],
    [0.6, 240, 220, 60], [0.8, 240, 120, 40], [1.0, 200, 40, 140]
  ];

  function buildLut() {
    var lut = new Uint8ClampedArray(256 * 4);
    for (var i = 0; i < 256; i++) {
      var f = i / 255, s = 1;
      while (s < STOPS.length - 1 && STOPS[s][0] < f) s++;
      var a = STOPS[s - 1], b = STOPS[s], t = (f - a[0]) / (b[0] - a[0]);
      for (var c = 0; c < 3; c++) lut[i * 4 + c] = a[c + 1] + (b[c + 1] - a[c + 1]) * t;
      lut[i * 4 + 3] = 22 + 75 * f;  // nearly clear for calm air, stronger for storms, so the map stays readable
    }
    return lut;
  }
  var LUT = buildLut();

  // Precipitation colours by rain rate in mm/h; alpha is added per pixel in buildPrecipLut.
  var RAIN_STOPS = [[0.1, 110, 180, 255], [0.5, 60, 130, 255], [2, 40, 200, 130], [5, 250, 230, 60], [15, 250, 130, 40], [50, 220, 40, 170]];
  var SNOW_STOPS = [[0.1, 200, 225, 255], [2, 225, 240, 255], [10, 255, 255, 255], [50, 215, 190, 255]];

  function buildPrecipLut(stops) {
    var lut = new Uint8ClampedArray(256 * 4);
    for (var i = 0; i < 256; i++) {
      var rate = Math.pow(i / 255, 2) * MAX_RAIN_RATE, s = 1;
      while (s < stops.length - 1 && stops[s][0] < rate) s++;
      var a = stops[s - 1], b = stops[s], t = Math.max(0, Math.min(1, (rate - a[0]) / (b[0] - a[0])));
      for (var c = 0; c < 3; c++) lut[i * 4 + c] = a[c + 1] + (b[c + 1] - a[c + 1]) * t;
      // Opacity grows smoothly from nothing at ~0.02 mm/h to strong at ~1.5 mm/h, so edges stay soft.
      lut[i * 4 + 3] = 200 * Math.sqrt(Math.max(0, Math.min(1, (rate - 0.02) / 1.5)));
    }
    return lut;
  }
  var RAIN_LUT = buildPrecipLut(RAIN_STOPS), SNOW_LUT = buildPrecipLut(SNOW_STOPS);

  var MAX_WAVE_M = 16, MAX_PERIOD_S = 30, MAX_TURBULENCE = 20;
  var WAVE_STOPS = [[0, 25, 40, 120], [1, 45, 90, 200], [2, 60, 150, 230], [3.5, 100, 185, 235], [5, 160, 210, 245],
                    [7, 230, 190, 235], [10, 235, 120, 190], [14, 190, 60, 170]];
  var PERIOD_STOPS = [[0, 40, 40, 110], [6, 90, 60, 190], [9, 170, 80, 200], [12, 240, 110, 170], [16, 250, 170, 110],
                      [22, 255, 230, 150], [30, 255, 250, 230]];

  /** A 256-entry colour table over 0..max; alphaOf(value) gives the opacity (0..255) for each entry. */
  function buildValueLut(stops, max, alphaOf) {
    var lut = new Uint8ClampedArray(256 * 4);
    for (var i = 0; i < 256; i++) {
      var v = i / 255 * max, s = 1;
      while (s < stops.length - 1 && stops[s][0] < v) s++;
      var a = stops[s - 1], b = stops[s], t = Math.max(0, Math.min(1, (v - a[0]) / (b[0] - a[0])));
      for (var c = 0; c < 3; c++) lut[i * 4 + c] = a[c + 1] + (b[c + 1] - a[c + 1]) * t;
      lut[i * 4 + 3] = alphaOf(v);
    }
    return lut;
  }
  // Land is stored as height 0, so it stays clear; open sea gets a gentle tint that firms up with bigger seas.
  var WAVE_LUT = buildValueLut(WAVE_STOPS, MAX_WAVE_M, function (h) { return h < 0.05 ? 0 : 50 + 85 * Math.min(1, h / 6); });
  var PERIOD_LUT = buildValueLut(PERIOD_STOPS, MAX_PERIOD_S, function (t) { return t < 0.5 ? 0 : 130; });
  // Turbulence by Ellrod index: invisible below ~2, a faint yellow at 'light' (4), orange at 'moderate' (8), red at 'severe' (12).
  var TURB_STOPS = [[0, 60, 90, 160], [4, 250, 225, 70], [8, 245, 140, 40], [12, 230, 55, 60], [20, 170, 40, 170]];
  var TURB_LUT = buildValueLut(TURB_STOPS, MAX_TURBULENCE, function (t) { return t < 2 ? 0 : Math.min(125, 10 + (t - 2) * 20); });

  function WindLayer(map, host) {
    this.map = map;
    this.dpr = Math.min(window.devicePixelRatio || 1, 2);
    this.meta = null;
    this.metas = {};            // base folder -> loaded meta.json
    this.metaPromises = {};
    this.level = 'surface';
    this.kind = 'wind';
    this.step = 0;
    this.frame = null;          // decoded texture currently shown
    this.cache = {};            // "level/step" -> promise of decoded frame
    this.cacheOrder = [];
    this.active = false;
    this.raf = null;
    this.requested = 0;         // guards against out-of-order frame loads

    this.heat = document.createElement('canvas');
    this.parts = document.createElement('canvas');
    [this.heat, this.parts].forEach(function (c) {
      c.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;pointer-events:none;display:none';
      host.appendChild(c);
    });
    this.hctx = this.heat.getContext('2d');
    this.maskCanvas = document.createElement('canvas');   // where the map shows water (sea layers only)
    this.mctx = this.maskCanvas.getContext('2d', { willReadFrequently: true });
    this.maskData = null;
    this.maskKey = '';
    this.pctx = this.parts.getContext('2d');
    this.sampleOut = [0, 0, 0];
    this.sampleTmp = [0, 0, 0];

    this.px = []; this.py = []; this.lon = []; this.lat = []; this.age = [];
    this.count = 0;

    var self = this;
    this.onMoveStart = function () { self.pctx.clearRect(0, 0, self.w, self.h); };
    this.onMove = function () { self.scheduleHeat(); };
    this.onMoveEnd = function () { self.drawHeat(); self.reseedAll(); };
    this.onResize = function () { self.resize(); self.drawHeat(); self.reseedAll(); };
    this.onIdle = function () { self.maskKey = ''; self.drawHeat(); };
    this.loop = function () { self.tick(); };
    this.resize();
  }

  WindLayer.prototype.resize = function () {
    var box = this.map.getContainer();
    this.w = box.clientWidth; this.h = box.clientHeight;
    this.heat.width = Math.max(1, Math.ceil(this.w / CELL));
    this.heat.height = Math.max(1, Math.ceil(this.h / CELL));
    this.parts.width = Math.round(this.w * this.dpr);
    this.parts.height = Math.round(this.h * this.dpr);
    this.pctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    this.count = Math.min(4000, Math.round(this.w * this.h / 300));
    this.heatImage = this.hctx.createImageData(this.heat.width, this.heat.height);
    this.maskCanvas.width = this.heat.width;
    this.maskCanvas.height = this.heat.height;
    this.maskKey = '';
  };

  /* ---- data ---- */

  WindLayer.prototype.loadMeta = function (base) {
    var self = this;
    if (!this.metaPromises[base]) {
      this.metaPromises[base] = fetch(SITE + base + '/meta.json', { cache: 'no-cache' })
        .then(function (r) { if (!r.ok) throw new Error('meta ' + r.status); return r.json(); })
        .then(function (m) { self.metas[base] = m; return m; });
      this.metaPromises[base].catch(function () { delete self.metaPromises[base]; });
    }
    return this.metaPromises[base];
  };

  /** Switches to another layer (a texture subfolder name): loads its meta and resets what is drawn. */
  WindLayer.prototype.useLevel = function (level) {
    var info = layerInfo(level), self = this;
    return this.loadMeta(info.base).then(function (meta) {
      self.level = level;
      self.kind = info.kind;
      self.base = info.base;
      self.meta = meta;
      self.requested++;          // drop any frame still loading for the previous layer
      self.frame = null;
      self.hctx.clearRect(0, 0, self.heat.width, self.heat.height);
      self.pctx.clearRect(0, 0, self.w, self.h);
      self.applyKind();
    });
  };

  /** Particles only make sense for wind; the other layers are colour fields. */
  WindLayer.prototype.applyKind = function () {
    this.parts.style.display = this.active && (this.kind === 'wind' || this.kind === 'wave') ? 'block' : 'none';
  };

  /** Timeline hour closest to a wall-clock time (ms), within the loaded forecast. */
  WindLayer.prototype.indexForTime = function (ms) {
    var hours = Math.round((ms - Date.parse(this.meta.run)) / 3600000) - this.meta.steps[0];
    return Math.max(0, Math.min(this.hourCount() - 1, hours));
  };

  /*
   * The timeline moves in whole hours from the first step; textures only exist every 3 hours, so the hours in
   * between are linear blends of the two surrounding textures.
   */
  WindLayer.prototype.hourCount = function () {
    var steps = this.meta.steps;
    return steps[steps.length - 1] - steps[0] + 1;
  };

  /** Valid time (ms since epoch) of timeline hour i. */
  WindLayer.prototype.timeOf = function (i) {
    return Date.parse(this.meta.run) + (this.meta.steps[0] + i) * 3600000;
  };

  /** Timeline hour closest to the current time. */
  WindLayer.prototype.nowIndex = function () {
    var hours = Math.round((Date.now() - Date.parse(this.meta.run)) / 3600000) - this.meta.steps[0];
    return Math.max(0, Math.min(this.hourCount() - 1, hours));
  };

  /** The two texture indices around timeline hour i, and how far (0..1) i lies from the first to the second. */
  WindLayer.prototype.bracket = function (i) {
    var steps = this.meta.steps, hour = steps[0] + i, k = 0;
    while (k < steps.length - 2 && steps[k + 1] <= hour) k++;
    var span = steps[k + 1] - steps[k];
    return { a: k, b: k + 1, t: Math.max(0, Math.min(1, (hour - steps[k]) / span)) };
  };

  WindLayer.prototype.loadFrame = function (level, index) {
    var key = level + '/' + index;
    if (this.cache[key]) return this.cache[key];
    var step = this.meta.steps[index];
    var info = layerInfo(level);
    var url = SITE + info.base + '/' + level + '/f' + ('00' + step).slice(-3) + '.png';
    // sample() turns channel byte b into b * scale[channel] + offset[channel]: m/s for wind, metres and a unit
    // vector for waves, seconds for the period, and plain 0..1 values for the rest.
    var scale, offset, spec = this.meta.levels[level];
    if (info.kind === 'wind') {
      var w = 2 * spec.maxSpeed / 255;
      scale = [w, w, w]; offset = [-spec.maxSpeed, -spec.maxSpeed, -spec.maxSpeed];
    } else if (info.kind === 'wave') {
      scale = [spec.maxHeight / 255, 2 / 255, 2 / 255]; offset = [0, -1, -1];
    } else if (info.kind === 'period') {
      scale = [spec.maxPeriod / 255, 0, 0]; offset = [0, 0, 0];
    } else if (info.kind === 'turbulence') {
      scale = [spec.maxIndex / 255, 0, 0]; offset = [0, 0, 0];
    } else {
      scale = [1 / 255, 1 / 255, 1 / 255]; offset = [0, 0, 0];
    }
    var promise = new Promise(function (resolve, reject) {
      var img = new Image();
      img.crossOrigin = 'anonymous';
      img.onload = function () {
        var c = document.createElement('canvas');
        c.width = img.width; c.height = img.height;
        var ctx = c.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(img, 0, 0);
        resolve({ data: ctx.getImageData(0, 0, c.width, c.height).data, w: c.width, h: c.height, scale: scale, offset: offset });
      };
      img.onerror = function () { reject(new Error('wind frame ' + url)); };
      img.src = url;
    });
    var self = this;
    promise.catch(function () { delete self.cache[key]; });
    this.cache[key] = promise;
    this.cacheOrder.push(key);
    while (this.cacheOrder.length > CACHE_FRAMES) delete this.cache[this.cacheOrder.shift()];
    return promise;
  };

  /** Wind at (lon, lat) for the current (possibly blended) frame; same contract as sample(). */
  WindLayer.prototype.sampleWind = function (frame, lon, lat, out) {
    if (!this.sample(frame.a, lon, lat, out)) return false;
    if (frame.t > 0) {
      var other = this.sampleTmp;
      this.sample(frame.b, lon, lat, other);
      out[0] += (other[0] - out[0]) * frame.t;
      out[1] += (other[1] - out[1]) * frame.t;
      out[2] += (other[2] - out[2]) * frame.t;
    }
    return true;
  };

  /**
   * Raw values at a point for the frame on screen, or null when nothing is loaded or the point is off the grid:
   * {u, v} in m/s for wind; {u: cloud 0..1} for clouds; {u: rain rate 0..1 (sqrt-encoded), v: snow fraction};
   * {u: height m, v: east, w: north} for waves; {u: seconds} for the wave period.
   */
  WindLayer.prototype.windAt = function (lon, lat) {
    var out = [0, 0, 0];
    return this.frame && this.sampleWind(this.frame, lon, lat, out) ? { u: out[0], v: out[1], w: out[2] } : null;
  };

  /** Bilinear wind sample at (lon, lat) in degrees; writes [u, v] in m/s to out, returns false outside the data. */
  WindLayer.prototype.sample = function (f, lon, lat, out) {
    if (lat > 90 || lat < -90) return false;
    lon = ((lon % 360) + 360) % 360;
    var fx = lon / 360 * f.w, fy = (90 - lat) / 180 * (f.h - 1);
    var x0 = Math.floor(fx), y0 = Math.floor(fy);
    var tx = fx - x0, ty = fy - y0;
    var x1 = (x0 + 1) % f.w, y1 = Math.min(y0 + 1, f.h - 1);
    x0 = x0 % f.w;
    var d = f.data, w = f.w;
    var i00 = (y0 * w + x0) * 4, i10 = (y0 * w + x1) * 4, i01 = (y1 * w + x0) * 4, i11 = (y1 * w + x1) * 4;
    var scale = f.scale, offset = f.offset;
    for (var c = 0; c < 3; c++) {
      var top = d[i00 + c] * (1 - tx) + d[i10 + c] * tx;
      var bottom = d[i01 + c] * (1 - tx) + d[i11 + c] * tx;
      out[c] = (top * (1 - ty) + bottom * ty) * scale[c] + offset[c];
    }
    return true;
  };

  /* ---- control ---- */

  WindLayer.prototype.setLevel = function (level) {
    this.level = level;
    return this.setStep(this.step);
  };

  /** Shows forecast step index i once its texture is available. Resolves when drawn. */
  WindLayer.prototype.setStep = function (index) {
    var self = this, ticket = ++this.requested, br = this.bracket(index);
    this.step = index;
    return Promise.all([this.loadFrame(this.level, br.a), this.loadFrame(this.level, br.b)]).then(function (pair) {
      if (ticket !== self.requested) return;  // a newer request superseded this one
      self.frame = { a: pair[0], b: pair[1], t: br.t };
      self.drawHeat();
    });
  };

  /** Starts downloading the textures needed for timeline hour i, so playback does not wait on the network. */
  WindLayer.prototype.prefetch = function (index) {
    if (index < 0 || index >= this.hourCount()) return;
    var br = this.bracket(index), self = this;
    [br.a, br.b].forEach(function (k) { self.loadFrame(self.level, k).catch(function () {}); });
  };

  WindLayer.prototype.show = function () {
    if (this.active) return;
    this.active = true;
    this.heat.style.display = 'block';
    this.applyKind();
    var m = this.map;
    m.on('movestart', this.onMoveStart); m.on('move', this.onMove);
    m.on('moveend', this.onMoveEnd); m.on('resize', this.onResize); m.on('idle', this.onIdle);
    this.drawHeat(); this.reseedAll();
    this.raf = requestAnimationFrame(this.loop);
  };

  WindLayer.prototype.hide = function () {
    if (!this.active) return;
    this.active = false;
    this.heat.style.display = this.parts.style.display = 'none';
    var m = this.map;
    m.off('movestart', this.onMoveStart); m.off('move', this.onMove);
    m.off('moveend', this.onMoveEnd); m.off('resize', this.onResize); m.off('idle', this.onIdle);
    cancelAnimationFrame(this.raf);
  };

  /** Legend for the current layer: a CSS gradient plus labels placed at positions 0..1 along it. */
  WindLayer.prototype.legend = function () {
    function rgb(lut, k) { return 'rgb(' + lut[k] + ',' + lut[k + 1] + ',' + lut[k + 2] + ')'; }
    var stops = [], i;
    if (this.kind === 'cloud') {
      return {
        gradient: 'linear-gradient(to right, rgba(' + CLOUD_RGB.join(',') + ',0.05), rgb(' + CLOUD_RGB.join(',') + '))',
        labels: [{ pos: 0, text: '0%' }, { pos: 1, text: '100% cloud cover' }]
      };
    }
    if (this.kind === 'turbulence') {
      for (i = 0; i <= 10; i++) stops.push(rgb(TURB_LUT, Math.round(i / 10 * 16 / MAX_TURBULENCE * 255) * 4));
      return {
        gradient: 'linear-gradient(to right,' + stops.join(',') + ')',
        labels: [{ pos: 4 / 16, text: 'Light' }, { pos: 8 / 16, text: 'Moderate' }, { pos: 12 / 16, text: 'Severe' }]
      };
    }
    if (this.kind === 'wave' || this.kind === 'period') {
      var isWave = this.kind === 'wave', table = isWave ? WAVE_LUT : PERIOD_LUT;
      var top = isWave ? 12 : 24, full = isWave ? MAX_WAVE_M : MAX_PERIOD_S;  // legend range, and the texture range
      for (i = 0; i <= 10; i++) stops.push(rgb(table, Math.round(i / 10 * top / full * 255) * 4));
      var marks = isWave ? [0, 3, 6, 9, 12] : [0, 6, 12, 18, 24];
      return {
        gradient: 'linear-gradient(to right,' + stops.join(',') + ')',
        labels: marks.map(function (m) {
          return { pos: m / top, text: m === top ? m + '+ ' + (isWave ? 'm' : 's') : String(m) };
        })
      };
    }
    if (this.kind === 'precip') {
      for (i = 0; i <= 10; i++) stops.push(rgb(RAIN_LUT, Math.round(i / 10 * 255) * 4));
      var labels = [0.1, 1, 5, 20, 50].map(function (rate) {
        return { pos: Math.sqrt(rate / MAX_RAIN_RATE), text: rate === 50 ? '50+ mm/h' : String(rate) };
      });
      return { gradient: 'linear-gradient(to right,' + stops.join(',') + ')', labels: labels };
    }
    var max = COLOR_MAX[this.level];
    for (i = 0; i <= 10; i++) stops.push(rgb(LUT, Math.round(i / 10 * 255) * 4));
    return {
      gradient: 'linear-gradient(to right,' + stops.join(',') + ')',
      labels: [{ pos: 0, text: '0' }, { pos: 1, text: max + '+ m/s' }]
    };
  };

  /* ---- drawing ---- */

  /**
   * The wave data is on a 28 km grid, far coarser than the coastline, so on its own it spills over onto land.
   * The map itself knows exactly where the water is: paint its water polygons into a mask with the same
   * resolution as the colour layer, then use it to clip the colours and to keep crests off the land.
   */
  WindLayer.prototype.updateMask = function () {
    var map = this.map, c = map.getCenter();
    var key = [c.lng.toFixed(5), c.lat.toFixed(5), map.getZoom().toFixed(3), map.getBearing().toFixed(1), this.w, this.h].join();
    if (key === this.maskKey && this.maskData) return this.maskData;
    this.maskKey = key;

    var ctx = this.mctx, cw = this.maskCanvas.width, ch = this.maskCanvas.height;
    ctx.clearRect(0, 0, cw, ch);
    ctx.fillStyle = ctx.strokeStyle = '#fff';
    ctx.lineWidth = 1;  // the stroke also covers hairline seams between neighbouring map tiles
    var features = map.queryRenderedFeatures({ layers: ['water'] });
    for (var i = 0; i < features.length; i++) {
      var g = features[i].geometry;
      var polygons = g.type === 'Polygon' ? [g.coordinates] : g.type === 'MultiPolygon' ? g.coordinates : [];
      for (var k = 0; k < polygons.length; k++) {
        ctx.beginPath();
        polygons[k].forEach(function (ring) {
          ring.forEach(function (pt, n) {
            var s = map.project(pt);
            n === 0 ? ctx.moveTo(s.x / CELL, s.y / CELL) : ctx.lineTo(s.x / CELL, s.y / CELL);
          });
          ctx.closePath();
        });
        ctx.fill('evenodd');
        ctx.stroke();
      }
    }
    this.maskData = ctx.getImageData(0, 0, cw, ch).data;
    return this.maskData;
  };

  /** True where the map shows water at a screen position (css pixels). Always true when no mask is in use. */
  WindLayer.prototype.isWater = function (x, y) {
    if (!this.maskData) return true;
    var cx = Math.floor(x / CELL), cy = Math.floor(y / CELL), cw = this.maskCanvas.width;
    if (cx < 0 || cy < 0 || cx >= cw || cy >= this.maskCanvas.height) return false;
    return this.maskData[(cy * cw + cx) * 4 + 3] >= 128;
  };

  WindLayer.prototype.scheduleHeat = function () {
    var self = this;
    if (this.heatPending) return;
    this.heatPending = true;
    requestAnimationFrame(function () { self.heatPending = false; self.drawHeat(); });
  };

  WindLayer.prototype.drawHeat = function () {
    if (!this.active || !this.frame) return;
    var f = this.frame, map = this.map, out = this.sampleOut;
    var img = this.heatImage, px = img.data, cw = this.heat.width, ch = this.heat.height;
    var colorMax = COLOR_MAX[this.level], kind = this.kind;
    var seaOnly = kind === 'wave' || kind === 'period';
    var mask = seaOnly ? this.updateMask() : null;
    if (!seaOnly) this.maskData = null;
    for (var y = 0; y < ch; y++) {
      for (var x = 0; x < cw; x++) {
        var p = map.unproject([x * CELL + CELL / 2, y * CELL + CELL / 2]);
        var o = (y * cw + x) * 4;
        if (!this.sampleWind(f, p.lng, p.lat, out)) { px[o + 3] = 0; continue; }
        if (kind === 'cloud') {
          var cover = Math.max(0, Math.min(1, out[0]));
          px[o] = CLOUD_RGB[0]; px[o + 1] = CLOUD_RGB[1]; px[o + 2] = CLOUD_RGB[2];
          px[o + 3] = Math.pow(cover, 1.6) * 115;  // thin cloud barely shows, full overcast stays see-through
        } else if (kind === 'wave' || kind === 'period' || kind === 'turbulence') {
          var table = kind === 'wave' ? WAVE_LUT : kind === 'period' ? PERIOD_LUT : TURB_LUT;
          var top = kind === 'wave' ? MAX_WAVE_M : kind === 'period' ? MAX_PERIOD_S : MAX_TURBULENCE;
          var wi = Math.max(0, Math.min(255, Math.round(out[0] / top * 255))) * 4;
          px[o] = table[wi]; px[o + 1] = table[wi + 1]; px[o + 2] = table[wi + 2];
          px[o + 3] = seaOnly ? table[wi + 3] * mask[o + 3] / 255 : table[wi + 3];  // waves are clipped to the sea
        } else if (kind === 'precip') {
          var lut = out[1] > 0.5 ? SNOW_LUT : RAIN_LUT;
          var q = Math.max(0, Math.min(255, Math.round(out[0] * 255))) * 4;
          px[o] = lut[q]; px[o + 1] = lut[q + 1]; px[o + 2] = lut[q + 2]; px[o + 3] = lut[q + 3];
        } else {
          var speed = Math.sqrt(out[0] * out[0] + out[1] * out[1]);
          var k = Math.min(255, Math.round(speed / colorMax * 255)) * 4;
          px[o] = LUT[k]; px[o + 1] = LUT[k + 1]; px[o + 2] = LUT[k + 2]; px[o + 3] = LUT[k + 3];
        }
      }
    }
    this.hctx.putImageData(img, 0, 0);
  };

  WindLayer.prototype.respawn = function (i) {
    // Spawn at a random screen position so particle density stays even however the map is zoomed or panned.
    var p = this.map.unproject([Math.random() * this.w, Math.random() * this.h]);
    this.lon[i] = p.lng; this.lat[i] = p.lat;
    this.age[i] = Math.floor(Math.random() * (this.kind === 'wave' ? CREST_LIFE : MAX_AGE));
    var s = this.map.project([p.lng, p.lat]);
    this.px[i] = s.x; this.py[i] = s.y;
  };

  WindLayer.prototype.reseedAll = function () {
    this.pctx.clearRect(0, 0, this.w, this.h);
    for (var i = 0; i < this.count; i++) this.respawn(i);
  };

  WindLayer.prototype.tick = function () {
    if (!this.active) return;
    this.raf = requestAnimationFrame(this.loop);
    var map = this.map, f = this.frame;
    var kind = this.kind;
    if (!f || (kind !== 'wind' && kind !== 'wave') || map.isMoving() || document.hidden) return;

    if (kind === 'wave') { this.tickWaves(f); return; }

    var ctx = this.pctx, out = this.sampleOut, w = this.w, h = this.h;
    ctx.globalCompositeOperation = 'destination-in';
    ctx.fillStyle = 'rgba(0,0,0,' + FADE + ')';
    ctx.fillRect(0, 0, w, h);
    ctx.globalCompositeOperation = 'source-over';
    ctx.strokeStyle = 'rgba(255,255,255,0.6)';
    ctx.lineWidth = 0.9;
    ctx.beginPath();

    var degPerPx = 360 / (512 * Math.pow(2, map.getZoom()));
    for (var i = 0; i < this.count; i++) {
      if (++this.age[i] > MAX_AGE || !this.sampleWind(f, this.lon[i], this.lat[i], out)) { this.respawn(i); continue; }
      var cos = Math.cos(this.lat[i] * Math.PI / 180), ux, uy;  // css pixels per frame, east and north
      if (kind === 'wind') {
        ux = out[0] * PIXELS_PER_MS; uy = out[1] * PIXELS_PER_MS;
      } else {
        // Waves: a unit direction vector; a vanishing one means land or the coast edge, so start over elsewhere.
        var mag = Math.sqrt(out[1] * out[1] + out[2] * out[2]);
        if (mag < 0.3 || out[0] < 0.05) { this.respawn(i); continue; }
        var pace = WAVE_PX * (0.6 + Math.min(1, out[0] / 5)) / mag;  // bigger seas move a little faster
        ux = out[1] * pace; uy = out[2] * pace;
      }
      this.lon[i] += ux * degPerPx;
      this.lat[i] += uy * degPerPx * cos;
      var s = map.project([this.lon[i], this.lat[i]]);
      if (s.x < -20 || s.x > w + 20 || s.y < -20 || s.y > h + 20) { this.respawn(i); continue; }
      ctx.moveTo(this.px[i], this.py[i]);
      ctx.lineTo(s.x, s.y);
      this.px[i] = s.x; this.py[i] = s.y;
    }
    ctx.stroke();
  };

  /**
   * Waves are drawn as short curved crest lines, perpendicular to the direction of travel, that fade in, drift
   * slowly with the waves and fade out. That keeps them visually distinct from the wind's flowing streaks.
   */
  WindLayer.prototype.tickWaves = function (f) {
    var map = this.map, ctx = this.pctx, out = this.sampleOut, w = this.w, h = this.h;
    ctx.clearRect(0, 0, w, h);
    ctx.lineCap = 'round';
    ctx.lineWidth = 1.3;
    var degPerPx = 360 / (512 * Math.pow(2, map.getZoom()));
    var n = Math.round(this.count * 0.32);  // crests are larger than particles, so fewer of them
    for (var i = 0; i < n; i++) {
      var age = ++this.age[i];
      if (age > CREST_LIFE) { this.respawn(i); continue; }
      // A crest that starts on land (no wave data there) just stays idle until its life runs out. Retrying
      // elsewhere instead would crowd every crest into the water and make the sea look far denser than the rest.
      if (!this.sampleWind(f, this.lon[i], this.lat[i], out)) continue;
      var mag = Math.sqrt(out[1] * out[1] + out[2] * out[2]);
      if (mag < 0.3 || out[0] < 0.05) continue;
      var east = out[1] / mag, north = out[2] / mag;
      var pace = WAVE_PX * (0.6 + Math.min(1, out[0] / 5));            // bigger seas drift a little faster
      this.lon[i] += east * pace * degPerPx;
      this.lat[i] += north * pace * degPerPx * Math.cos(this.lat[i] * Math.PI / 180);
      var s = map.project([this.lon[i], this.lat[i]]);
      if (s.x < -30 || s.x > w + 30 || s.y < -30 || s.y > h + 30) { this.respawn(i); continue; }
      if (!this.isWater(s.x, s.y)) continue;  // drifted onto land: hide it until it fades out and restarts

      var sx = east, sy = -north;               // travel direction on screen (y points down)
      var qx = -sy, qy = sx;                    // the crest runs across it
      var half = 6 + Math.min(6, out[0] * 1.5);  // longer crests for bigger seas
      var envelope = Math.sin(Math.PI * age / CREST_LIFE);
      ctx.strokeStyle = 'rgba(255,255,255,' + (0.55 * envelope).toFixed(2) + ')';
      ctx.beginPath();
      ctx.moveTo(s.x - qx * half, s.y - qy * half);
      ctx.quadraticCurveTo(s.x + sx * half * 0.9, s.y + sy * half * 0.9, s.x + qx * half, s.y + qy * half);
      ctx.stroke();
    }
  };

  window.WindLayer = WindLayer;
})();
