// Original score/synthesis for Tambola Together. No samples, external songs or model output.
// Reproducible PCM masters; rerunning updates only the explicitly named sound asset directory.
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
const folder = fileURLToPath(new URL('../media/sound/', import.meta.url));
const check = process.argv.includes('--check');
assert.ok(process.argv.slice(2).every(arg => arg === '--check'), 'Only --check is supported');
if (!check) mkdirSync(folder, { recursive: true });
function output(name, bytes) {
  if (check) assert.deepEqual(readFileSync(folder + name), Buffer.from(bytes), `Non-reproducible asset: ${name}`);
  else writeFileSync(folder + name, bytes);
}
const rate = 44100, beat = .75, seconds = 24, tau = 2 * Math.PI;
const hz = midi => 440 * 2 ** ((midi - 69) / 12);
const music = new Float64Array(rate * seconds);
function note(target, start, midi, length, gain, kind = 'pluck') {
  const frequency = hz(midi), count = Math.floor(rate * length);
  for (let i = 0; i < count; i++) {
    const t = i / rate, attack = Math.min(1, t / .012), release = Math.min(1, (length - t) / .08);
    const envelope = attack * release * (kind === 'pad' ? Math.sin(Math.PI * t / length) ** .5 : Math.exp(-t * (kind === 'bell' ? 4 : 6)));
    const tone = kind === 'bell' ? Math.sin(tau * frequency * t) + .18 * Math.sin(tau * frequency * 2 * t) * Math.exp(-t * 6)
      : Math.sin(tau * frequency * t) + (kind === 'pad' ? .10 : .25) * Math.sin(tau * frequency * 2 * t);
    // Wrap tails into the start of the loop to preserve its natural seam.
    const at = (Math.floor(start * rate) + i) % target.length;
    target[at] += tone * envelope * gain;
  }
}
const chords = [[48,55,59,64], [45,52,55,60], [41,48,52,57], [43,50,57,62], [48,55,59,62], [45,52,55,60], [41,48,55,57], [43,50,55,62]];
for (let bar = 0; bar < 8; bar++) {
  const start = bar * 4 * beat, chord = chords[bar];
  chord.forEach(pitch => note(music, start, pitch, beat * 4.6, .028, 'pad'));
  note(music, start, chord[0] - 12, 1.7, .075);
  [0,1.5,2.5,3.5].forEach((pulse, index) => note(music, start + pulse * beat, chord[1 + index % 3] + 12, .8, .038));
}
// Sparse original call-and-response phrase, leaving room for spoken numbers.
[[0,76],[1.5,74],[3,79],[5,76],[8,72],[10.5,76],[12,74],[15,71],[16,76],[18,79],[19.5,81],[22,79],[24,76],[26.5,74],[28,71],[30.5,74]]
  .forEach(([pulse,pitch]) => note(music, pulse * beat, pitch, .9, .044, 'bell'));
const cue = (duration, notes) => { const samples = new Float64Array(Math.ceil(duration * rate)); notes.forEach(n => note(samples, ...n)); return samples; };
const assets = {
  'game-night.wav': music,
  'mark.wav': cue(.18, [[0,79,.14,.32,'pluck']]),
  'call.wav': cue(.28, [[0,67,.2,.24,'bell']]),
  'deal.wav': cue(.72, [[0,60,.36,.2,'pluck'],[.12,64,.4,.18,'pluck'],[.24,67,.4,.18,'pluck']]),
  'win.wav': cue(1.5, [[0,72,.55,.2,'bell'],[.15,76,.6,.18,'bell'],[.3,79,.75,.16,'bell'],[.5,84,.9,.12,'bell']]),
};
const manifest = { version: 1, title: 'A Little Game Night', provenance: 'Original algorithmic score and additive synthesis authored for Tambola Together; no external samples.', sampleRate: rate, channels: 1, bpm: 80, loopSeconds: seconds, clips: {} };
for (const [name, source] of Object.entries(assets)) {
  let peak = 0; for (const n of source) peak = Math.max(peak, Math.abs(n));
  const gain = Math.min(1, .72 / peak), data = Buffer.alloc(44 + source.length * 2);
  data.write('RIFF'); data.writeUInt32LE(data.length - 8,4); data.write('WAVEfmt ',8); data.writeUInt32LE(16,16);
  data.writeUInt16LE(1,20); data.writeUInt16LE(1,22); data.writeUInt32LE(rate,24); data.writeUInt32LE(rate * 2,28);
  data.writeUInt16LE(2,32); data.writeUInt16LE(16,34); data.write('data',36); data.writeUInt32LE(source.length * 2,40);
  let squares = 0;
  for (let i=0;i<source.length;i++) { const n = source[i] * gain; squares += n*n; data.writeInt16LE(Math.round(n * 32767), 44+i*2); }
  const seam = Math.abs(source[0] - source.at(-1)) * gain;
  if (name === 'game-night.wav') assert.ok(seam < .005, 'Music loop has a discontinuity');
  output(name, data);
  manifest.clips[name] = { sha256: createHash('sha256').update(data).digest('hex'), bytes: data.length, seconds: source.length/rate,
    peak: +(peak*gain).toFixed(6), rms: +Math.sqrt(squares/source.length).toFixed(6), seam: +seam.toFixed(6) };
}
output('manifest.json', JSON.stringify(manifest,null,2)+'\n');
console.log(check ? 'Five PCM assets and manifest reproduce exactly; loop seam and output bounds verified.' : JSON.stringify(manifest,null,2));
