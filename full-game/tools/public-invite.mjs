import { createHash } from 'node:crypto';

const download = 'https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha28-friend-links';
const css = `:root{color-scheme:dark;--bg:#19152b;--panel:#28203e;--cream:#fff3da;--muted:#c7bdd9;--gold:#f4c879;--coral:#ff967e}
*{box-sizing:border-box}body{margin:0;background:radial-gradient(ellipse at 20% 0%,#393057,transparent 65%),var(--bg);color:var(--cream);font:17px/1.55 'Trebuchet MS','Nirmala UI',sans-serif;min-height:100svh}a{color:inherit}header{max-width:1080px;margin:auto;padding:24px;display:flex;align-items:center;justify-content:space-between;gap:16px}header strong{font-size:18px}nav a{display:inline-flex;align-items:center;min-height:48px;padding:0 10px;border-radius:10px}nav a[aria-current]{background:var(--panel);color:var(--gold)}main{max-width:1080px;margin:3vh auto;padding:24px;display:grid;grid-template-columns:1fr 1fr;gap:60px;align-items:center}.eyebrow{color:var(--gold);font-size:13px;font-weight:bold;letter-spacing:.14em;text-transform:uppercase}h1{font-size:clamp(36px,5vw,60px);line-height:1.1;letter-spacing:-.04em;margin:18px 0 24px}p{color:var(--muted);margin:16px 0}.ticket{position:relative;padding:30px;background:var(--panel);border:1px solid #534564;border-radius:26px;box-shadow:0 20px 80px #0004}.label{margin:0;font-size:14px}.code{display:block;color:var(--gold);font:bold clamp(24px,5vw,38px)/1.5 ui-monospace,monospace;letter-spacing:.12em;margin:6px 0 20px;user-select:all;overflow-wrap:anywhere}.rule{border-top:1px dashed #766481;padding-top:22px}.button{display:flex;min-height:56px;justify-content:center;align-items:center;text-align:center;border-radius:14px;padding:12px 18px;text-decoration:none;font-weight:bold;background:var(--coral);color:var(--bg)}.secondary{background:transparent;border:1px solid #766481;color:var(--cream);margin-top:12px}.small{font-size:14px}.foot{max-width:1080px;margin:20px auto;padding:0 24px 32px;font-size:13px;color:var(--muted)}a:focus-visible{outline:3px solid var(--gold);outline-offset:5px}h2{font-size:20px;margin:0 0 8px}ol{padding-left:22px;color:var(--muted)}li{padding:4px 0}@media(max-width:700px){header{padding:16px;flex-wrap:wrap}main{grid-template-columns:1fr;gap:22px;margin:0 auto;padding:16px}h1{font-size:38px;margin:10px 0 16px}.ticket{padding:22px}.foot{padding:0 16px 24px}}`;
const styleHash = createHash('sha256').update(css).digest('base64');

/** No room lookup, credentials, request-host reflection, scripts or automatic app launch. */
export function invitationPage(path) {
  const match = /^\/friends\/([A-HJ-NP-Z2-9]{8})(?:\?lang=(en|hi))?$/.exec(path);
  if (!match) return null;
  const [, code, language = 'en'] = match;
  const hi = language === 'hi';
  const words = hi ? {
    eyebrow: 'साथ खेलें, कहीं से भी', title: 'दोस्तों के साथ<br>तंबोला की शाम।',
    intro: 'अपनी पसंद की जगह से जुड़ें। सबके आने पर होस्ट खेल शुरू करेंगे।', code: 'आपकी टेबल का कोड',
    open: 'ऐप में खोलें', download: 'Android ऐप डाउनलोड करें', review: 'ऐप में टिकट और सिक्कों की संख्या देखकर ही जुड़ें। लिंक खोलने से सिक्के खर्च नहीं होते।',
    how: 'पहली बार खेल रहे हैं?', steps: ['Internet Beta APK इंस्टॉल करें।', 'इस पेज पर लौटकर “ऐप में खोलें” दबाएँ।', 'या ऐप में “दोस्तों के साथ खेलें” → “कोड से जुड़ें” चुनकर ऊपर का कोड भरें।'],
    note: 'मुफ़्त वर्चुअल सिक्के · कोई नकद पुरस्कार नहीं', foot: 'अस्थायी PC सर्वर • सर्वर ऑनलाइन होने पर खेलें। टेबल शुरू हो चुकी हो या लिंक न खुले तो होस्ट से नया निमंत्रण माँगें।'
  } : {
    eyebrow: 'Good company. Any distance.', title: 'Your friends.<br>Your Tambola night.',
    intro: 'Pull up a seat from wherever you are. Your host starts the game when everyone is here.', code: 'Your table code',
    open: 'Open in app', download: 'Download Android app', review: 'Choose your tickets and confirm the coin cost in the app. Opening this link spends nothing.',
    how: 'Joining for the first time?', steps: ['Install the Internet Beta APK.', 'Return here and tap “Open in app”.', 'Or choose “Play with friends” → “Join with code” in the app and enter the code above.'],
    note: 'Free virtual coins · No cash prizes', foot: 'Temporary PC server • Play while the server is online. If the table has started or this link stops opening, ask your host for a fresh invitation.'
  };
  const intent = `intent://friends/${code}#Intent;scheme=tambola-beta;package=io.github.sbshrey.tambola.game.beta;S.browser_fallback_url=${encodeURIComponent(download)};end`;
  return {
    headers: { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store', 'referrer-policy': 'no-referrer',
      'x-content-type-options': 'nosniff', 'x-robots-tag': 'noindex, nofollow',
      'content-security-policy': `default-src 'none'; style-src 'sha256-${styleHash}'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'` },
    body: `<!doctype html><html lang="${language}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="referrer" content="no-referrer"><title>Join your friends · Tambola Together</title><style>${css}</style></head><body>
<header><strong>Tambola Together</strong><nav aria-label="Language"><a href="/friends/${code}?lang=en" lang="en" ${!hi ? 'aria-current="page"' : ''}>English</a><a href="/friends/${code}?lang=hi" lang="hi" ${hi ? 'aria-current="page"' : ''}>हिन्दी</a></nav></header>
<main><section><div class="eyebrow">${words.eyebrow}</div><h1>${words.title}</h1><p>${words.intro}</p><p class="small">${words.note}</p></section>
<section class="ticket" aria-label="${words.code}"><p class="label">${words.code}</p><code class="code">${code.slice(0, 4)} ${code.slice(4)}</code><a class="button" href="${intent}">${words.open}</a><a class="button secondary" href="${download}">${words.download}</a><p class="small">${words.review}</p><div class="rule"><h2>${words.how}</h2><ol class="small">${words.steps.map(step => `<li>${step}</li>`).join('')}</ol></div></section></main>
<footer class="foot">${words.foot}</footer></body></html>`
  };
}
