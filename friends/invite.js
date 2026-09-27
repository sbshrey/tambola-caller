export const DOWNLOAD = 'https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha34-round-recovery';
const CODE = /^[A-HJ-NP-Z2-9]{8}$/;

// The fragment stays in the browser: no room lookup, tokens or table code sent to a service.
export function invitationLocation(value) {
  const url = new URL(value);
  const language = url.search === '?lang=hi' ? 'hi' : 'en';
  const queryAllowed = ['', '?lang=en', '?lang=hi'].includes(url.search);
  const candidate = url.hash.slice(1);
  return { language, code: queryAllowed && CODE.test(candidate) ? candidate : null };
}

export function appIntent(code) {
  if (!CODE.test(code)) throw new TypeError('Invalid table code');
  return `intent://friends/${code}#Intent;scheme=tambola-beta;package=io.github.sbshrey.tambola.game.beta;S.browser_fallback_url=${encodeURIComponent(DOWNLOAD)};end`;
}

const hindi = {
  eyebrow: 'साथ खेलें, कहीं से भी', heading: 'दोस्तों की टेबल से जुड़ें।',
  intro: 'अपनी पसंद की जगह से जुड़ें। सबके आने पर होस्ट खेल शुरू करेंगे।', note: 'मुफ़्त वर्चुअल सिक्के · कोई नकद पुरस्कार नहीं',
  'code-label': 'आपकी टेबल का कोड', copy: 'कोड कॉपी करें', open: 'ऐप में खोलें', download: 'Android ऐप डाउनलोड करें',
  review: 'ऐप में टिकट और सिक्कों की संख्या देखकर ही जुड़ें। लिंक खोलने से सिक्के खर्च नहीं होते।',
  'invalid-heading': 'दोस्त का पूरा निमंत्रण खोलें', 'invalid-detail': 'होस्ट का भेजा पूरा लिंक खोलें, या ऐप में उनका आठ अक्षरों वाला कोड भरें।',
  how: 'पहली बार खेल रहे हैं?', step1: 'Internet Beta APK इंस्टॉल करें।', step2: 'इस पेज पर लौटकर “ऐप में खोलें” दबाएँ।',
  step3: 'या ऐप में “दोस्तों के साथ खेलें” → “कोड से जुड़ें” चुनकर होस्ट का कोड भरें।',
  foot: 'PC सर्वर ऑनलाइन होने पर खेलें। टेबल शुरू हो चुकी हो या बंद हो गई हो, तो होस्ट से नया निमंत्रण माँगें।',
};

function renderInvitation() {
  const { code, language } = invitationLocation(location.href);
  const element = id => document.getElementById(id);
  document.documentElement.lang = language;
  if (language === 'hi') for (const [id, text] of Object.entries(hindi)) element(id).textContent = text;
  for (const [id, lang] of [['english', 'en'], ['hindi', 'hi']]) {
    element(id).href = `?lang=${lang}${code ? '#' + code : ''}`;
    if (language === lang) element(id).setAttribute('aria-current', 'page');
    else element(id).removeAttribute('aria-current');
  }
  element('download').href = DOWNLOAD;
  element('invitation').hidden = !code;
  element('invalid').hidden = !!code;
  element('copy-status').textContent = '';
  element('open').removeAttribute('href');
  if (code) {
    element('code').textContent = `${code.slice(0, 4)} ${code.slice(4)}`;
    element('open').href = appIntent(code);
    element('copy').onclick = async () => {
      try {
        await navigator.clipboard.writeText(code);
        if (invitationLocation(location.href).code === code) element('copy-status').textContent = language === 'hi' ? 'कोड कॉपी हो गया।' : 'Code copied.';
      } catch {
        if (invitationLocation(location.href).code !== code) return;
        const selection = getSelection();
        const range = document.createRange(); range.selectNodeContents(element('code'));
        selection?.removeAllRanges(); selection?.addRange(range);
        element('copy-status').textContent = language === 'hi' ? 'चुने हुए कोड को कॉपी करें या ऐप में भरें।' : 'Copy the selected code, or type it in the app.';
      }
    };
  }
}
if (typeof document !== 'undefined') {
  renderInvitation();
  addEventListener('hashchange', renderInvitation);
}
