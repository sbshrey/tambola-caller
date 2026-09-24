import { numberWords } from '../src/game.js';

// Familiar short calls vary by group. Keep the number clear even without a nickname.
// This is a curated selection, not a claim that every group uses the same wording.
const nicknames = {
  1: "Kelly's eye", 2: 'One little duck', 3: 'Cup of tea', 4: 'Knock at the door',
  5: 'High five', 6: 'Half a dozen', 7: 'Lucky seven', 8: 'Garden gate',
  10: 'A perfect ten', 11: 'Legs eleven', 12: 'One dozen', 13: 'Lucky thirteen',
  14: "Valentine's Day", 15: 'Independence Day', 16: 'Sweet sixteen',
  18: 'Coming of age', 19: 'Goodbye teens', 20: 'One score', 21: 'Key to the door',
  22: 'Two little ducks', 24: 'Two dozen', 25: 'Silver jubilee', 26: 'Republic Day',
  32: 'All the teeth', 33: 'All the threes', 36: 'Three dozen', 40: 'Life begins at forty',
  44: 'All the fours', 45: 'Halfway there', 48: 'Four dozen', 50: 'Half a century',
  52: 'A full deck', 55: 'All the fives', 60: 'Five dozen', 66: 'All the sixes',
  72: 'Six dozen', 75: 'Diamond jubilee', 77: 'All the sevens',
  83: 'India wins the World Cup', 84: 'Seven dozen', 88: 'All the eights',
  89: 'Nearly there', 90: 'Top of the house',
};
const digits = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine'];
export const calls = Array.from({ length: 90 }, (_, index) => {
  const number = index + 1;
  const spelled = numberWords(number).toLowerCase();
  const individual = String(number).split('').map((digit) => digits[Number(digit)]).join(', ');
  const phrase = nicknames[number];
  // Explicit wording prevents the repeated sixes from swallowing the final tens.
  const fullNumber = [60, 66].includes(number) ? `The number is ${spelled}.` : `${spelled}.`;
  return { number, phrase: phrase ?? '', text: `${phrase ? `${phrase}. ` : ''}${number < 10 ? 'Single number' : 'Number'} ${individual}. ${fullNumber}` };
});

export const speechSettings = {
  model: 'gpt-4o-mini-tts',
  voice: 'coral',
  response_format: 'mp3',
  instructions: 'You are a warm, cheerful female Indian Tambola host. Speak English with a natural, consistent Indian English accent and a feminine tone. Read only the supplied words exactly. Use a steady, unhurried pace for older listeners, with a short pause after the nickname and between the individual digits, then clearly emphasize the full number. Keep the same voice, pitch, accent and volume across calls. No singing, sound effects, music, laughter, extra words, or exaggerated character voice.',
};
