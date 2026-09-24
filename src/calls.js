import { CALL_PHRASES } from './call-phrases.js';
import { HINDI_NUMBERS, normalizeLanguage, numberWords } from './languages.js';

const digits = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine'];
export function getCall(number, language = 'en') {
  if (!Number.isInteger(number) || number < 1 || number > 90) throw new RangeError('Number must be 1–90');
  language = normalizeLanguage(language);
  const phrases = CALL_PHRASES[number - 1];
  const phrase = phrases[language];
  const spelled = numberWords(number, language).toLowerCase();
  const individual = String(number).split('').map((digit) => language === 'hi' ? HINDI_NUMBERS[Number(digit)] : digits[Number(digit)]).join(', ');
  let text;
  if (language === 'hi') {
    // A full sentence improves endings that were unclear in the initial recordings.
    const ending = [15, 28, 37, 39, 42, 48, 58, 63, 67, 73, 80, 87].includes(number) ? 'पूरा नंबर है' : 'संख्या';
    text = `${phrase}। अंक ${individual}। ${ending} ${spelled}।`;
  }
  else {
    // Devanagari keeps the Hindi part of a Hinglish clip unambiguous for TTS.
    const spokenPhrase = language === 'hinglish' ? phrases.hi : phrase;
    const explicitEnding = [60, 66].includes(number)
      || (language === 'en' && [29, 80].includes(number))
      || (language === 'hinglish' && [33, 36, 44, 65, 87].includes(number));
    const fullNumber = explicitEnding ? `The number is ${spelled}.` : `${spelled}.`;
    text = `${spokenPhrase}. ${number < 10 ? 'Single number' : 'Number'} ${individual}. ${fullNumber}`;
  }
  return { number, language, phrase, text };
}

export const callsForLanguage = (language = 'en') => Array.from({ length: 90 }, (_, index) => getCall(index + 1, language));
