import { readFile, writeFile, mkdir, rename } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { calls, speechSettings } from './calls.mjs';

const root = new URL('../audio/', import.meta.url);
export const hash = (data) => createHash('sha256').update(data).digest('hex');
export const requestFor = (call) => ({ ...speechSettings, input: call.text });
export function selectCalls(args) {
  if (!args.length || args.includes('--help')) return [];
  if (args.length === 1 && args[0] === '--all') return calls;
  if (args.length === 1 && args[0] === '--samples') return calls.filter(({ number }) => [7, 22, 90].includes(number));
  if (args.length === 2 && args[0] === '--numbers' && /^\d+(,\d+)*$/.test(args[1])) {
    const numbers = [...new Set(args[1].split(',').map(Number))];
    if (numbers.every((n) => n >= 1 && n <= 90)) return numbers.map((n) => calls[n - 1]);
  }
  throw new Error('Use --samples, --all, or --numbers 7,22,90.');
}

export async function generate(selected, { directory = root, apiKey = process.env.OPENAI_API_KEY, fetcher = fetch, log = console.log } = {}) {
  if (!apiKey) throw new Error('Set OPENAI_API_KEY in your local environment. Never put it in the website or Git.');
  await mkdir(new URL('numbers/', directory), { recursive: true });
  const manifestUrl = new URL('manifest.json', directory);
  let manifest;
  try { manifest = JSON.parse(await readFile(manifestUrl, 'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw new Error('Cannot read audio/manifest.json; fix it before generating.'); }
  manifest ??= { version: 1, disclosure: 'AI-generated voice', clips: {} };
  for (const call of selected) {
    const request = requestFor(call);
    const fingerprint = hash(JSON.stringify(request));
    const name = `${String(call.number).padStart(2, '0')}.mp3`;
    const destination = new URL(`numbers/${name}`, directory);
    const previous = manifest.clips[call.number];
    if (previous?.fingerprint === fingerprint) {
      try {
        if (hash(await readFile(destination)) === previous.sha256) { log(`Keeping ${name} (already generated)`); continue; }
      } catch (error) { if (error.code !== 'ENOENT') throw error; }
    }
    // One request per missing/changed clip. Do not silently retry billable requests.
    let response;
    try {
      response = await fetcher('https://api.openai.com/v1/audio/speech', {
        method: 'POST', headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
        body: JSON.stringify(request), signal: AbortSignal.timeout(120_000),
      });
    } catch { throw new Error(`Speech request failed for ${call.number}. Rerun to resume; the last request may have been billed.`); }
    if (!response.ok) throw new Error(`Speech API returned HTTP ${response.status} for ${call.number}. Check API access/billing. Existing clips are kept.`);
    const bytes = Buffer.from(await response.arrayBuffer());
    if (bytes.length < 1000 || !(bytes.subarray(0, 3).toString() === 'ID3' || (bytes[0] === 0xff && (bytes[1] & 0xe0) === 0xe0))) {
      throw new Error(`Invalid MP3 response for ${call.number}; nothing published for this clip.`);
    }
    await writeFile(new URL(`numbers/${name}.tmp`, directory), bytes);
    await rename(new URL(`numbers/${name}.tmp`, directory), destination);
    manifest.clips[call.number] = { ...call, file: `numbers/${name}`, model: request.model, voice: request.voice,
      fingerprint, sha256: hash(bytes), bytes: bytes.length, generatedAt: new Date().toISOString() };
    await writeFile(new URL('manifest.json.tmp', directory), `${JSON.stringify(manifest, null, 2)}\n`);
    await rename(new URL('manifest.json.tmp', directory), manifestUrl);
    log(`Generated ${name} (${bytes.length} bytes)`);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const selected = selectCalls(process.argv.slice(2));
    if (!selected.length) console.log('Generate AI voice clips locally (uses paid OpenAI API):\n  node scripts/generate-audio.mjs --samples\n  node scripts/generate-audio.mjs --all\n  node scripts/generate-audio.mjs --numbers 22\nUnchanged clips are reused. Edit scripts/calls.mjs to change wording or voice.');
    else await generate(selected);
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
