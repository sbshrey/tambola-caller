import { numberWords } from './game.js';

export function createVoice(onError, browser = window) {
  const supported = 'speechSynthesis' in browser && 'SpeechSynthesisUtterance' in browser;
  let current;
  function stop() {
    if (supported) {
      current = null;
      browser.speechSynthesis.cancel();
    }
  }
  function speak(text) {
    if (!supported) return;
    try {
      stop();
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
  return {
    supported, stop, speak,
    announce: (number) => speak(`Number ${number}. ${numberWords(number)}.`),
  };
}
