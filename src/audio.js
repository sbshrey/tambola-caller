import { languageFolder, normalizeLanguage } from './languages.js';

export function clipUrl(number, language = 'en') {
  if (!Number.isInteger(number) || number < 1 || number > 90) throw new RangeError('Number must be 1–90');
  return new URL(`../audio/${languageFolder(language)}numbers/${String(number).padStart(2, '0')}.mp3`, import.meta.url).href;
}
export const clipFileName = (number, language = 'en') => `Tambola-${String(number).padStart(2, '0')}-${{ en: 'English', hi: 'Hindi', hinglish: 'Hinglish' }[normalizeLanguage(language)]}-AI-voice.mp3`;

// Prepare before the share tap: a network wait can lose mobile user activation.
export function createClipLoader(fetcher = fetch, FileType = File) {
  let currentKey;
  let pending;
  return (number, language = 'en') => {
    const key = clipUrl(number, language);
    if (key !== currentKey) {
      currentKey = key;
      pending = fetcher(key).then(async (response) => {
        if (!response.ok) throw new Error('Audio unavailable');
        const blob = await response.blob();
        if (!blob.size || !/^(audio\/|application\/octet-stream)/i.test(blob.type)) throw new Error('Audio unavailable');
        return new FileType([blob], clipFileName(number, language), { type: 'audio/mpeg' });
      }).catch(() => null);
    }
    return pending;
  };
}
