export function clipUrl(number) {
  if (!Number.isInteger(number) || number < 1 || number > 90) throw new RangeError('Number must be 1–90');
  return new URL(`../audio/numbers/${String(number).padStart(2, '0')}.mp3`, import.meta.url).href;
}

// Prepare before the share tap: a network wait can lose mobile user activation.
export function createClipLoader(fetcher = fetch, FileType = File) {
  let currentNumber;
  let pending;
  return (number) => {
    if (number !== currentNumber) {
      currentNumber = number;
      pending = fetcher(clipUrl(number)).then(async (response) => {
        if (!response.ok) throw new Error('Audio unavailable');
        const blob = await response.blob();
        if (!blob.size || !/^(audio\/|application\/octet-stream)/i.test(blob.type)) throw new Error('Audio unavailable');
        return new FileType([blob], `Tambola-${String(number).padStart(2, '0')}-AI-voice.mp3`, { type: 'audio/mpeg' });
      }).catch(() => null);
    }
    return pending;
  };
}

export async function shareClip(file, device = navigator) {
  if (!file || typeof device.share !== 'function' || typeof device.canShare !== 'function') return 'fallback';
  try {
    const data = { files: [file] };
    if (!device.canShare(data)) return 'fallback';
    await device.share(data);
    return 'opened';
  } catch (error) { return error?.name === 'AbortError' ? 'cancelled' : 'fallback'; }
}
