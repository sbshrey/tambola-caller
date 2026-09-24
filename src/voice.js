import { getCall } from './calls.js';
import { LANGUAGES, normalizeLanguage } from './languages.js';
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
  function speak(text, language = 'en') {
    stop();
    if (!speechSupported) return;
    try {
      const utterance = new browser.SpeechSynthesisUtterance(text);
      const voices = browser.speechSynthesis.getVoices();
      const locale = LANGUAGES[normalizeLanguage(language)].locale;
      const matches = (v) => v.lang.toLowerCase().replace('_', '-').startsWith(locale.slice(0, 2) + '-');
      // Prefer a local voice in the chosen language for offline fallback.
      const voice = voices.find((v) => v.localService && v.lang.replace('_', '-') === locale)
        ?? voices.find((v) => v.localService && matches(v))
        ?? voices.find((v) => matches(v));
      if (voice) utterance.voice = voice;
      utterance.lang = voice?.lang ?? locale;
      utterance.rate = 0.85;
      utterance.onerror = (event) => {
        if (current === utterance && !['interrupted', 'canceled'].includes(event.error)) onError();
      };
      current = utterance;
      browser.speechSynthesis.speak(utterance);
    } catch { onError(); }
  }
  function announce(number, language = 'en') {
    const text = getCall(number, language).text;
    if (!audio) { speak(text, language); return; }
    stop();
    const token = playback;
    const fallback = () => {
      if (token !== playback) return;
      if (speechSupported) speak(text, language);
      else { stop(); onError(); }
    };
    audio.onerror = fallback;
    audio.src = clipUrl(number, language);
    // Start directly inside the Next / Repeat gesture, including on iOS.
    try { Promise.resolve(audio.play()).catch(fallback); } catch { fallback(); }
  }
  return { supported: Boolean(audio) || speechSupported, stop, speak, announce };
}
