// Procedural WebAudio: cinematic generative music + all SFX. No assets needed.
export class AudioManager {
  constructor() {
    this.ctx = null; this.master = null; this.musicGain = null; this.sfxGain = null;
    this.muted = false; this.musicOn = true; this.started = false;
    this._step = 0; this._nextNote = 0; this.rainSrc = null; this.rainGain = null;
  }
  init() {
    if (this.ctx) return;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    this.ctx = new AC();
    this.master = this.ctx.createGain(); this.master.gain.value = 0.85; this.master.connect(this.ctx.destination);
    this.musicGain = this.ctx.createGain(); this.musicGain.gain.value = 0.34; this.musicGain.connect(this.master);
    this.sfxGain = this.ctx.createGain(); this.sfxGain.gain.value = 0.9; this.sfxGain.connect(this.master);
  }
  resume() { this.init(); if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume(); }
  setMuted(m) { this.muted = m; if (this.master) this.master.gain.value = m ? 0 : 0.85; }
  setVolumes(music, sfx) {
    if (this.musicGain) this.musicGain.gain.value = music;
    if (this.sfxGain) this.sfxGain.gain.value = sfx;
  }
  startMusic() {
    if (!this.ctx || this.started) return; this.started = true;
    this._nextNote = this.ctx.currentTime + 0.1;
    this._timer = setInterval(() => this._schedule(), 120);
    this._startRainLoop();
  }
  // Generative: D-minor-ish cinematic pad + taiko pulse. Intensity scales with combat flag.
  _schedule() {
    if (!this.ctx || !this.musicOn || this.muted) { this._nextNote = this.ctx ? this.ctx.currentTime + 0.2 : 0; return; }
    const scale = [146.83, 174.61, 196.0, 220.0, 261.63, 293.66, 349.23]; // D F G A C D F
    while (this._nextNote < this.ctx.currentTime + 0.5) {
      const combat = this.combatMode ? 1 : 0;
      const step = this._step++;
      // deep drone every 8 steps
      if (step % 8 === 0) this._pad(73.42, 3.6, 0.10);
      // melody note
      if (step % (combat ? 2 : 4) === 0) {
        const n = scale[(Math.random() * scale.length) | 0] * (Math.random() < 0.25 ? 2 : 1);
        this._pluck(n, combat ? 0.9 : 1.6, combat ? 0.16 : 0.11);
      }
      // taiko pulse
      if (step % (combat ? 2 : 4) === 2) this._drum(combat ? 90 : 65, combat ? 0.5 : 0.3);
      this._nextNote += combat ? 0.32 : 0.52;
    }
  }
  _pluck(freq, dur, vol) {
    const t = this._nextNote, c = this.ctx;
    const o = c.createOscillator(), g = c.createGain(), f = c.createBiquadFilter();
    o.type = 'triangle'; o.frequency.value = freq;
    f.type = 'lowpass'; f.frequency.setValueAtTime(2400, t); f.frequency.exponentialRampToValueAtTime(300, t + dur);
    g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(vol, t + 0.02); g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    o.connect(f); f.connect(g); g.connect(this.musicGain);
    o.start(t); o.stop(t + dur + 0.05);
  }
  _pad(freq, dur, vol) {
    const t = this.ctx.currentTime, c = this.ctx;
    for (const det of [-4, 3]) {
      const o = c.createOscillator(), g = c.createGain();
      o.type = 'sawtooth'; o.frequency.value = freq; o.detune.value = det;
      const f = c.createBiquadFilter(); f.type = 'lowpass'; f.frequency.value = 500;
      g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(vol / 2, t + dur * 0.3); g.gain.linearRampToValueAtTime(0, t + dur);
      o.connect(f); f.connect(g); g.connect(this.musicGain); o.start(t); o.stop(t + dur + 0.1);
    }
  }
  _drum(freq, vol) {
    const t = this._nextNote, c = this.ctx;
    const o = c.createOscillator(), g = c.createGain();
    o.type = 'sine'; o.frequency.setValueAtTime(freq, t); o.frequency.exponentialRampToValueAtTime(38, t + 0.25);
    g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.001, t + 0.4);
    o.connect(g); g.connect(this.musicGain); o.start(t); o.stop(t + 0.45);
  }
  _startRainLoop() {
    const c = this.ctx; if (!c || this.rainSrc) return;
    const len = c.sampleRate * 2, buf = c.createBuffer(1, len, c.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
    this.rainSrc = c.createBufferSource(); this.rainSrc.buffer = buf; this.rainSrc.loop = true;
    const f = c.createBiquadFilter(); f.type = 'highpass'; f.frequency.value = 3500;
    this.rainGain = c.createGain(); this.rainGain.gain.value = 0;
    this.rainSrc.connect(f); f.connect(this.rainGain); this.rainGain.connect(this.master);
    this.rainSrc.start();
  }
  setRain(v) { if (this.rainGain) this.rainGain.gain.setTargetAtTime(v * 0.05, this.ctx.currentTime, 0.5); }
  // ---- SFX ----
  _noise(dur, filterFreq, vol, type = 'lowpass') {
    if (!this.ctx || this.muted) return;
    const c = this.ctx, t = c.currentTime;
    const len = Math.ceil(c.sampleRate * dur), buf = c.createBuffer(1, len, c.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / len);
    const s = c.createBufferSource(); s.buffer = buf;
    const f = c.createBiquadFilter(); f.type = type; f.frequency.value = filterFreq;
    const g = c.createGain(); g.gain.value = vol;
    s.connect(f); f.connect(g); g.connect(this.sfxGain); s.start(t);
  }
  _tone(freq, dur, vol, type = 'square', slideTo = null) {
    if (!this.ctx || this.muted) return;
    const c = this.ctx, t = c.currentTime;
    const o = c.createOscillator(), g = c.createGain();
    o.type = type; o.frequency.setValueAtTime(freq, t);
    if (slideTo) o.frequency.exponentialRampToValueAtTime(slideTo, t + dur);
    g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    o.connect(g); g.connect(this.sfxGain); o.start(t); o.stop(t + dur + 0.02);
  }
  swing() { this._noise(0.16, 3200, 0.35, 'bandpass'); }
  heavySwing() { this._noise(0.3, 1600, 0.45, 'bandpass'); }
  hit() { this._noise(0.14, 700, 0.6); this._tone(120, 0.14, 0.35, 'sine', 50); }
  crit() { this._noise(0.2, 900, 0.7); this._tone(90, 0.25, 0.5, 'sawtooth', 40); }
  block() { this._tone(620, 0.18, 0.35, 'square', 300); this._noise(0.1, 4500, 0.3, 'highpass'); }
  dodge() { this._noise(0.22, 1200, 0.25, 'bandpass'); }
  jump() { this._tone(280, 0.15, 0.2, 'sine', 520); }
  land() { this._noise(0.1, 400, 0.3); }
  step() { this._noise(0.06, 500 + Math.random() * 300, 0.10); }
  potion() { this._tone(400, 0.3, 0.25, 'sine', 900); }
  pickup() { this._tone(660, 0.12, 0.25, 'sine', 990); setTimeout(() => this._tone(880, 0.15, 0.22, 'sine'), 90); }
  coin() { this._tone(990, 0.09, 0.22, 'square'); setTimeout(() => this._tone(1320, 0.14, 0.2, 'square'), 70); }
  quest() { [523, 659, 784].forEach((f, i) => setTimeout(() => this._tone(f, 0.25, 0.22, 'triangle'), i * 110)); }
  levelup() { [392, 523, 659, 784, 1046].forEach((f, i) => setTimeout(() => this._tone(f, 0.3, 0.24, 'triangle'), i * 100)); }
  thunder() { this._noise(1.4, 160, 0.8); }
  skill() { this._tone(200, 0.5, 0.35, 'sawtooth', 1200); this._noise(0.4, 2500, 0.3, 'highpass'); }
  roar() { this._tone(70, 1.0, 0.6, 'sawtooth', 45); this._noise(0.9, 300, 0.5); }
  ui() { this._tone(520, 0.07, 0.15, 'sine'); }
  death() { this._tone(220, 0.8, 0.4, 'sawtooth', 55); }
}
export const audio = new AudioManager();
