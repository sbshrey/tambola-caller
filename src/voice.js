import { numberWords } from './game.js';
import { clipUrl } from './audio.js';

export function createVoice(onError, browser = window, player = null) {
  const speechSupported = 'speechSynthesis' in browser && 'SpeechSynthesisUtterance' in browser;
  const audio = player ?? (typeof browser.Audio === 'function' ? new browser.Audio() : null);
  if (audio) audio.preload = 'auto';
  let current;
  let playback = 0;
  function stop() {
    playback++;
    current = null;
    if (audio) {
      audio.onerror = null;
      audio.pause();
      audio.removeAttribute('src');
      audio.load();
    }
    if (speechSupported) {
      browser.speechSynthesis.cancel();
    }
  }
  function speak(text) {
    stop();
    if (!speechSupported) return;
    try {
      const utterance = new browser.SpeechSynthesisUtterance(text);
      const voices = browser.speechSynthesis.getVoices();
      // Prefer a local English voice so announcements can work offline.
      const voice = voices.find((v) => v.localService && /^en[-_]IN$/i.test(v.lang))
        ?? voices.find((v) => v.localService && /^en\b/i.test(v.lang))
        ?? voices.find((v) => /^en[-_]IN$/i.test(v.lang))
        ?? voices.find((v) => /^en\b/i.test(v.lang));
      if (voice) utterance.voice = voice;
      utterance.lang = voice?.lang ?? 'en-IN';
      utterance.rate = 0.85;
      utterance.onerror = (event) => {
        if (current === utterance && !['interrupted', 'canceled'].includes(event.error)) onError();
      };
      current = utterance;
      browser.speechSynthesis.speak(utterance);
    } catch { onError(); }
  }
  function announce(number) {
    const text = `Number ${number}. ${numberWords(number)}.`;
    if (!audio) { speak(text); return; }
    stop();
    const token = playback;
    const fallback = () => {
      if (token !== playback) return;
      if (speechSupported) speak(text);
      else { stop(); onError(); }
    };
    audio.onerror = fallback;
    audio.src = clipUrl(number);
    // Start directly inside the Next / Repeat gesture, including on iOS.
    try { Promise.resolve(audio.play()).catch(fallback); } catch { fallback(); }
  }
  return { supported: Boolean(audio) || speechSupported, stop, speak, announce };
}
