import { callsForLanguage } from '../src/calls.js';
export { callsForLanguage } from '../src/calls.js';
export const calls = callsForLanguage('en');

export const speechSettings = {
  model: 'gpt-4o-mini-tts',
  voice: 'coral',
  response_format: 'mp3',
  instructions: 'You are a warm, cheerful female Indian Tambola host. Speak English with a natural, consistent Indian English accent and a feminine tone. Read only the supplied words exactly. Use a steady, unhurried pace for older listeners, with a short pause after the nickname and between the individual digits, then clearly emphasize the full number. Keep the same voice, pitch, accent and volume across calls. No singing, sound effects, music, laughter, extra words, or exaggerated character voice.',
};

export function settingsFor(language = 'en') {
  if (language === 'en') return speechSettings;
  const pronunciation = language === 'hi'
    ? 'Speak entirely in natural Hindi with clear Hindi number names. Read the Devanagari words exactly, including the full number at the end. Do not translate into English.'
    : 'Speak natural Hinglish. Read the Devanagari phrase in Hindi and the English words and number names in Indian English exactly as written. Do not translate either part. Clearly finish with the complete English number.';
  return { ...speechSettings, instructions: 'You are a warm female Indian Tambola host. ' + pronunciation + ' Use a steady, unhurried pace for older listeners, short pauses between the phrase, digits and full number. Keep the same feminine voice, pitch and volume across calls. Read only the supplied text. No singing, music, laughter or extra words.' };
}
