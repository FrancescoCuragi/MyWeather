/*
 * Wind layer for the MyWeather map: speed colours plus animated particles, drawn on two canvases laid over the
 * MapLibre map. Wind comes from PNG textures published by the data pipeline (see pipeline/build_wind.py):
 * red = eastward wind (u), green = northward wind (v), scaled from [-maxSpeed, +maxSpeed] m/s to 0..255,
 * on a 0.5 degree grid with longitude 0..360 and latitude 90..-90.
 */
(function () {
  'use strict';

  var BASE_URL = 'https://francescocuragi.github.io/MyWeather/wind/';
  var CELL = 4;                 // heatmap resolution: one sample per CELL x CELL css pixels
  var PIXELS_PER_MS = 0.08;     // particle speed: css pixels moved per frame per m/s of wind
  var MAX_AGE = 90;             // frames before a particle respawns
  var FADE = 0.9;               // trail persistence per frame (lower = shorter trails)
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
      lut[i * 4 + 3] = 70 + 110 * f;  // faint for calm air, stronger for storms
    }
    return lut;
  }
  var LUT = buildLut();

  function WindLayer(map, host) {
    this.map = map;
    this.dpr = Math.min(window.devicePixelRatio || 1, 2);
    this.meta = null;
    this.level = 'surface';
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
    this.pctx = this.parts.getContext('2d');
    this.sampleOut = [0, 0];

    this.px = []; this.py = []; this.lon = []; this.lat = []; this.age = [];
    this.count = 0;

    var self = this;
    this.onMoveStart = function () { self.pctx.clearRect(0, 0, self.w, self.h); };
    this.onMove = function () { self.scheduleHeat(); };
    this.onMoveEnd = function () { self.drawHeat(); self.reseedAll(); };
    this.onResize = function () { self.resize(); self.drawHeat(); self.reseedAll(); };
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
    this.count = Math.min(4000, Math.round(this.w * this.h / 110));
    this.heatImage = this.hctx.createImageData(this.heat.width, this.heat.height);
  };

  /* ---- data ---- */

  WindLayer.prototype.loadMeta = function () {
    var self = this;
    if (!this.metaPromise) {
      this.metaPromise = fetch(BASE_URL + 'meta.json', { cache: 'no-cache' })
        .then(function (r) { if (!r.ok) throw new Error('meta ' + r.status); return r.json(); })
        .then(function (m) { self.meta = m; return m; });
      this.metaPromise.catch(function () { self.metaPromise = null; });
    }
    return this.metaPromise;
  };

  /** Valid time (ms since epoch) of forecast step index i. */
  WindLayer.prototype.timeOf = function (i) {
    return Date.parse(this.meta.run) + this.meta.steps[i] * 3600000;
  };

  /** Index of the forecast step closest to the current time. */
  WindLayer.prototype.nowIndex = function () {
    var now = Date.now(), best = 0, bestDiff = Infinity;
    for (var i = 0; i < this.meta.steps.length; i++) {
      var d = Math.abs(this.timeOf(i) - now);
      if (d < bestDiff) { bestDiff = d; best = i; }
    }
    return best;
  };

  WindLayer.prototype.loadFrame = function (level, index) {
    var key = level + '/' + index;
    if (this.cache[key]) return this.cache[key];
    var step = this.meta.steps[index];
    var url = BASE_URL + level + '/f' + ('00' + step).slice(-3) + '.png';
    var maxSpeed = this.meta.levels[level].maxSpeed;
    var promise = new Promise(function (resolve, reject) {
      var img = new Image();
      img.crossOrigin = 'anonymous';
      img.onload = function () {
        var c = document.createElement('canvas');
        c.width = img.width; c.height = img.height;
        var ctx = c.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(img, 0, 0);
        resolve({ data: ctx.getImageData(0, 0, c.width, c.height).data, w: c.width, h: c.height, max: maxSpeed });
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
    var scale = 2 * f.max / 255;
    for (var c = 0; c < 2; c++) {
      var top = d[i00 + c] * (1 - tx) + d[i10 + c] * tx;
      var bottom = d[i01 + c] * (1 - tx) + d[i11 + c] * tx;
      out[c] = (top * (1 - ty) + bottom * ty) * scale - f.max;
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
    var self = this, ticket = ++this.requested;
    this.step = index;
    return this.loadFrame(this.level, index).then(function (frame) {
      if (ticket !== self.requested) return;  // a newer request superseded this one
      self.frame = frame;
      self.drawHeat();
    });
  };

  WindLayer.prototype.prefetch = function (index) {
    if (index >= 0 && index < this.meta.steps.length) this.loadFrame(this.level, index).catch(function () {});
  };

  WindLayer.prototype.show = function () {
    if (this.active) return;
    this.active = true;
    this.heat.style.display = this.parts.style.display = 'block';
    var m = this.map;
    m.on('movestart', this.onMoveStart); m.on('move', this.onMove);
    m.on('moveend', this.onMoveEnd); m.on('resize', this.onResize);
    this.drawHeat(); this.reseedAll();
    this.raf = requestAnimationFrame(this.loop);
  };

  WindLayer.prototype.hide = function () {
    if (!this.active) return;
    this.active = false;
    this.heat.style.display = this.parts.style.display = 'none';
    var m = this.map;
    m.off('movestart', this.onMoveStart); m.off('move', this.onMove);
    m.off('moveend', this.onMoveEnd); m.off('resize', this.onResize);
    cancelAnimationFrame(this.raf);
  };

  WindLayer.prototype.legend = function () {
    var max = COLOR_MAX[this.level];
    var stops = [];
    for (var i = 0; i <= 10; i++) {
      var k = Math.round(i / 10 * 255) * 4;
      stops.push('rgb(' + LUT[k] + ',' + LUT[k + 1] + ',' + LUT[k + 2] + ')');
    }
    return { gradient: 'linear-gradient(to right,' + stops.join(',') + ')', max: max };
  };

  /* ---- drawing ---- */

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
    var colorMax = COLOR_MAX[this.level];
    for (var y = 0; y < ch; y++) {
      for (var x = 0; x < cw; x++) {
        var p = map.unproject([x * CELL + CELL / 2, y * CELL + CELL / 2]);
        var o = (y * cw + x) * 4;
        if (!this.sample(f, p.lng, p.lat, out)) { px[o + 3] = 0; continue; }
        var speed = Math.sqrt(out[0] * out[0] + out[1] * out[1]);
        var k = Math.min(255, Math.round(speed / colorMax * 255)) * 4;
        px[o] = LUT[k]; px[o + 1] = LUT[k + 1]; px[o + 2] = LUT[k + 2]; px[o + 3] = LUT[k + 3];
      }
    }
    this.hctx.putImageData(img, 0, 0);
  };

  WindLayer.prototype.respawn = function (i) {
    // Spawn at a random screen position so particle density stays even however the map is zoomed or panned.
    var p = this.map.unproject([Math.random() * this.w, Math.random() * this.h]);
    this.lon[i] = p.lng; this.lat[i] = p.lat;
    this.age[i] = Math.floor(Math.random() * MAX_AGE);
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
    if (!f || map.isMoving() || document.hidden) return;

    var ctx = this.pctx, out = this.sampleOut, w = this.w, h = this.h;
    ctx.globalCompositeOperation = 'destination-in';
    ctx.fillStyle = 'rgba(0,0,0,' + FADE + ')';
    ctx.fillRect(0, 0, w, h);
    ctx.globalCompositeOperation = 'source-over';
    ctx.strokeStyle = 'rgba(255,255,255,0.85)';
    ctx.lineWidth = 1.1;
    ctx.beginPath();

    var degPerPx = 360 / (512 * Math.pow(2, map.getZoom()));
    for (var i = 0; i < this.count; i++) {
      if (++this.age[i] > MAX_AGE || !this.sample(f, this.lon[i], this.lat[i], out)) { this.respawn(i); continue; }
      var cos = Math.cos(this.lat[i] * Math.PI / 180);
      this.lon[i] += out[0] * PIXELS_PER_MS * degPerPx;
      this.lat[i] += out[1] * PIXELS_PER_MS * degPerPx * cos;
      var s = map.project([this.lon[i], this.lat[i]]);
      if (s.x < -20 || s.x > w + 20 || s.y < -20 || s.y > h + 20) { this.respawn(i); continue; }
      ctx.moveTo(this.px[i], this.py[i]);
      ctx.lineTo(s.x, s.y);
      this.px[i] = s.x; this.py[i] = s.y;
    }
    ctx.stroke();
  };

  window.WindLayer = WindLayer;
})();
