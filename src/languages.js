export const LANGUAGES = Object.freeze({
  en: { label: 'English (India)', locale: 'en-IN', folder: '' },
  hi: { label: 'हिन्दी', locale: 'hi-IN', folder: 'hi/' },
  hinglish: { label: 'Hinglish', locale: 'hi-IN', folder: 'hinglish/' },
});
export const normalizeLanguage = (language) => Object.hasOwn(LANGUAGES, language) ? language : 'en';
export const languageFolder = (language) => LANGUAGES[normalizeLanguage(language)].folder;

const ONES = ['Zero', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight',
  'Nine', 'Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen',
  'Seventeen', 'Eighteen', 'Nineteen'];
const TENS = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];
export const HINDI_NUMBERS = ('शून्य एक दो तीन चार पाँच छह सात आठ नौ दस ग्यारह बारह तेरह चौदह पंद्रह सोलह सत्रह अठारह उन्नीस बीस इक्कीस बाईस तेईस चौबीस पच्चीस छब्बीस सत्ताईस अट्ठाईस उनतीस तीस इकतीस बत्तीस तैंतीस चौंतीस पैंतीस छत्तीस सैंतीस अड़तीस उनतालीस चालीस इकतालीस बयालीस तैंतालीस चवालीस पैंतालीस छियालीस सैंतालीस अड़तालीस उनचास पचास इक्यावन बावन तिरपन चौवन पचपन छप्पन सत्तावन अट्ठावन उनसठ साठ इकसठ बासठ तिरसठ चौंसठ पैंसठ छियासठ सड़सठ अड़सठ उनहत्तर सत्तर इकहत्तर बहत्तर तिहत्तर चौहत्तर पचहत्तर छिहत्तर सतहत्तर अठहत्तर उन्यासी अस्सी इक्यासी बयासी तिरासी चौरासी पचासी छियासी सतासी अट्ठासी नवासी नब्बे').split(' ');
export function numberWords(number, language = 'en') {
  if (!Number.isInteger(number) || number < 1 || number > 90) return '';
  if (language === 'hi') return HINDI_NUMBERS[number];
  return number < 20 ? ONES[number] : `${TENS[Math.floor(number / 10)]}${number % 10 ? ` ${ONES[number % 10].toLowerCase()}` : ''}`;
}
