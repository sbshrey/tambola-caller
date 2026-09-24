// Optional paid transcription audit. Never supplies the expected text to the model.
import { readFile, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { hash, selectCalls } from './generate-audio.mjs';
import { HINDI_NUMBERS, languageFolder, numberWords } from '../src/languages.js';

const normalize = (text) => text.toLowerCase().normalize('NFD').replace(/[\u093c]/g, '').replace(/ँ/g, 'ं').replace(/[०-९]/g, (digit) => String(digit.charCodeAt(0) - 0x966)).replace(/[^\p{L}\p{M}\p{N}]+/gu, ' ').trim();
export function detectsNumber(transcript, number) {
  const text = ` ${normalize(transcript)} `;
  // ASR sometimes joins the spoken digits and repeated full number: 47 + 47 -> 4747.
  const variants = { 6: ['छ', 'छः'], 58: ['अठावन', 'अठ्ठावन'] };
  return [String(number), String(number).repeat(2), numberWords(number), HINDI_NUMBERS[number], ...(variants[number] ?? [])].some((value) => text.includes(` ${normalize(value)} `));
}
export async function validate(selected, { apiKey = process.env.OPENAI_API_KEY, model = 'gpt-4o-mini-transcribe' } = {}) {
  if (!apiKey) throw Error('OPENAI_API_KEY is required.');
  if (!selected.length) return;
  const language = selected[0].language;
  if (!['en', 'hi', 'hinglish'].includes(language) || selected.some((call) => call.language !== language)) throw Error('Audit one valid language at a time.');
  const output = new URL(`../docs/audio-validation-v1.5-${language}.json`, import.meta.url);
  let report;
  try { report = JSON.parse(await readFile(output, 'utf8')); } catch (error) { if (error.code !== 'ENOENT') throw error; }
  report ??= { note: 'Automated transcription without expected wording. Number detection is not a human accent or full-phrase quality assessment. Failed detections require review; reruns reuse matching hashes.', clips: {} };
  // Sequential requests make each persisted result resumable and avoid duplicate billing.
  for (const call of selected) {
    const key = `${call.language}:${call.number}`;
    const bytes = await readFile(new URL(`../audio/${languageFolder(call.language)}numbers/${String(call.number).padStart(2, '0')}.mp3`, import.meta.url));
    const sha256 = hash(bytes);
    const languageHint = call.language === 'hi' ? 'hi' : 'en';
    const previous = report.clips[key];
    if (previous?.sha256 === sha256 && (previous.expectedNumberDetected || (previous.model === model && previous.languageHint === languageHint))) continue;
    const form = new FormData();
    form.set('model', model);
    form.set('language', languageHint);
    form.set('file', new Blob([bytes], { type: 'audio/mpeg' }), 'clip.mp3');
    const response = await fetch('https://api.openai.com/v1/audio/transcriptions', { method: 'POST', headers: { Authorization: `Bearer ${apiKey}` }, body: form, signal: AbortSignal.timeout(120_000) });
    if (!response.ok) throw Error(`Transcription HTTP ${response.status} for ${key}; no automatic retry.`);
    const { text } = await response.json();
    const detected = detectsNumber(text, call.number);
    report.clips[key] = { number: call.number, language: call.language, languageHint, sha256, model, transcript: text, expectedNumberDetected: detected, checkedAt: new Date().toISOString() };
    await writeFile(output, JSON.stringify(report, null, 2) + '\n');
    console.log(`${key}: ${detected ? 'PASS' : 'REVIEW'} ${text}`);
  }
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const args = process.argv.slice(2);
    const full = args.includes('--full-model');
    const selected = selectCalls(args.filter((arg) => arg !== '--full-model'));
    if (!selected.length) throw Error('Choose --samples, --all, or --numbers, with --language en, hi, or hinglish. Uses paid API.');
    await validate(selected, { model: full ? 'gpt-4o-transcribe' : 'gpt-4o-mini-transcribe' });
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
