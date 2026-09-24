import { numberWords, TOTAL_NUMBERS } from './game.js';
import { getCall } from './calls.js';

export function numberMessage(state) {
  const latest = state.called.at(-1);
  if (latest === undefined) return '';
  const recent = state.called.slice(-5).reverse().join(', ');
  if (state.callLanguage === 'hi') return `🎱 *${latest} — ${numberWords(latest, 'hi')}*\n${getCall(latest, 'hi').phrase}\nबुलावा ${state.called.length} / ${TOTAL_NUMBERS}\nहाल के नंबर: ${recent}`;
  if (state.callLanguage === 'hinglish') return `🎱 *${latest} — ${numberWords(latest)}*\n${getCall(latest, 'hinglish').phrase}\nCall ${state.called.length} of ${TOTAL_NUMBERS}\nRecent: ${recent}`;
  return `🎱 *${latest} — ${numberWords(latest)}*\nCall ${state.called.length} of ${TOTAL_NUMBERS}\nRecent: ${recent}`;
}

export function whatsappMessageUrl(text) {
  return `https://wa.me/?text=${encodeURIComponent(text)}`;
}

// Call directly from a click handler to preserve the browser's user activation.
// "opened" means the share API resolved, not confirmed WhatsApp delivery.
export async function openNumberShare(text, device = navigator) {
  if (!text || typeof device.share !== 'function') return 'fallback';
  try {
    const data = { text };
    if (typeof device.canShare === 'function' && !device.canShare(data)) return 'fallback';
    await device.share(data);
    return 'opened';
  } catch (error) {
    return error?.name === 'AbortError' ? 'cancelled' : 'fallback';
  }
}

export async function copyText(text, device = navigator) {
  if (!text) return false;
  try {
    await device.clipboard.writeText(text);
    return true;
  } catch { return false; }
}

// Images and audio use the same capability check and cancellation behavior.
export async function shareFile(file, device = navigator) {
  if (!file || typeof device.share !== 'function' || typeof device.canShare !== 'function') return 'fallback';
  try {
    const data = { files: [file] };
    if (!device.canShare(data)) return 'fallback';
    await device.share(data);
    return 'opened';
  } catch (error) { return error?.name === 'AbortError' ? 'cancelled' : 'fallback'; }
}
