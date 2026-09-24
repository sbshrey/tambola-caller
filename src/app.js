import { TOTAL_NUMBERS, newGame, restartGame, drawNumber, undoNumber, numberWords, parseGame } from './game.js';
import { STORAGE_KEY, loadGame, saveGame } from './storage.js';
import { createVoice } from './voice.js';
import { numberMessage, whatsappMessageUrl, openNumberShare, copyText, shareFile } from './sharing.js';
import { clipUrl, clipFileName, createClipLoader } from './audio.js';
import { LANGUAGES, normalizeLanguage } from './languages.js';
import { getCall } from './calls.js';
import { createImageUI } from './image-ui.js';
import { createPrizeUI } from './prize-ui.js';

const $ = (id) => document.getElementById(id);
let storage;
try { storage = window.localStorage; } catch { /* Private/restricted browsers may deny access. */ }
const loaded = loadGame(storage);
let state = loaded.state;
let drawLocked = false;
let toastTimer;
let pendingConfirmation;
let sharingNumber = false;
let sharingAudio = false;
let sharingImage = false;
let audioNumber;
let audioLanguage;
let audioRequest = 0;
let audioFile;
let audioPreparing = false;
const loadClip = createClipLoader();

function renderAudio() {
  $('share-audio').disabled = !audioFile || sharingNumber;
  $('share-audio').textContent = sharingAudio ? 'Opening…' : audioPreparing ? 'Preparing audio…' : 'Share audio clip';
  $('download-audio').hidden = !audioNumber;
  if (audioNumber) {
    $('download-audio').href = clipUrl(audioNumber, audioLanguage);
    $('download-audio').download = clipFileName(audioNumber, audioLanguage);
  } else $('download-audio').removeAttribute('href');
}
function prepareAudio(number, language) {
  if (number !== audioNumber || language !== audioLanguage) {
    audioNumber = number;
    audioLanguage = language;
    const request = ++audioRequest;
    audioFile = null;
    audioPreparing = Boolean(number);
    $('audio-share-status').textContent = `AI-generated ${LANGUAGES[language].label} voice · choose WhatsApp to share`;
    if (number) loadClip(number, language).then((file) => {
      if (request !== audioRequest) return;
      audioFile = file;
      audioPreparing = false;
      if (!file) $('audio-share-status').textContent = 'Audio clip unavailable. Reconnect and reopen, or share the number as text.';
      renderAudio();
    });
  }
  renderAudio();
}

function notice(id, message) { $(id).textContent = message; $(id).hidden = false; }
function toast(message) {
  clearTimeout(toastTimer);
  $('toast').textContent = message;
  $('toast').hidden = false;
  toastTimer = setTimeout(() => { $('toast').hidden = true; }, 4500);
}
const voice = createVoice(() => notice('voice-warning', 'Voice couldn’t play. Check your volume or try “Say it again”. You can keep calling numbers on screen.'), window, $('number-audio'));
const imageUI = createImageUI({
  isSharing: () => sharingNumber,
  setSharing(value) { sharingImage = value; sharingNumber = value; render(); },
  stopVoice: () => voice.stop(),
});
const prizeUI = createPrizeUI({ getState: () => state, commit, notify: toast, stopVoice: () => voice.stop(), announce: (message) => { if (state.voiceEnabled) voice.speak(message); } });
if (!voice.supported) notice('voice-warning', 'Spoken calls aren’t available in this browser. You can still play using the board.');
if (loaded.error) notice('storage-warning', 'Your saved game couldn’t be read. A fresh board is ready; this browser may have storage blocked.');

for (let number = 1; number <= TOTAL_NUMBERS; number++) {
  const cell = document.createElement('div');
  cell.className = 'board-cell';
  cell.textContent = number;
  cell.setAttribute('role', 'listitem');
  cell.dataset.number = number;
  $('board').append(cell);
}

function render() {
  const count = state.called.length;
  const latest = state.called.at(-1);
  const complete = count === TOTAL_NUMBERS;
  const called = new Set(state.called);
  $('current-number').textContent = latest ?? '—';
  $('number-label').textContent = latest ? 'NUMBER CALLED' : 'YOUR FIRST NUMBER AWAITS';
  $('call-language').value = state.callLanguage;
  $('number-words').textContent = latest ? numberWords(latest, state.callLanguage) : 'Ready, set, Tambola!';
  $('number-words').lang = state.callLanguage === 'hi' ? 'hi' : 'en-IN';
  $('call-phrase').textContent = latest ? getCall(latest, state.callLanguage).phrase : '';
  $('call-phrase').lang = state.callLanguage === 'hi' ? 'hi' : state.callLanguage === 'hinglish' ? 'hi-Latn' : 'en-IN';
  $('call-phrase').hidden = !latest;
  $('called-count').textContent = count;
  $('remaining-count').textContent = TOTAL_NUMBERS - count;
  $('round-status').textContent = complete ? 'ALL 90 CALLED' : count ? 'GAME IN FULL SWING' : 'READY WHEN YOU ARE';
  $('next').disabled = complete || drawLocked;
  $('next-label').textContent = complete ? 'All 90 called!' : 'Next number';
  $('share-number').disabled = !count || sharingNumber;
  $('share-number').setAttribute('aria-busy', String(sharingNumber));
  $('share-number-label').textContent = sharingNumber && !sharingAudio && !sharingImage ? 'Opening…' : 'Share number';
  $('copy-number').disabled = !count || sharingNumber;
  prepareAudio(latest, state.callLanguage);
  imageUI.render(state);
  $('undo').disabled = !count;
  $('repeat').disabled = !count || !voice.supported;
  $('history').disabled = !count;
  $('voice-toggle').disabled = !voice.supported;
  $('voice-toggle').setAttribute('aria-pressed', String(state.voiceEnabled && voice.supported));
  $('voice-label').textContent = !voice.supported ? 'No voice' : state.voiceEnabled ? 'Voice on' : 'Voice off';
  $('caller-hint').textContent = complete ? 'That’s every number! Celebrate, then start a fresh game.' : 'Gather your tickets. We’ll take care of the numbers.';
  for (const cell of $('board').children) {
    const number = Number(cell.dataset.number);
    cell.classList.toggle('called', called.has(number));
    cell.classList.toggle('latest', number === latest);
    cell.setAttribute('aria-label', `${number}, ${number === latest ? 'latest call' : called.has(number) ? 'called' : 'not called'}`);
  }
  $('progress').setAttribute('aria-valuenow', count);
  $('progress-fill').style.width = `${count / TOTAL_NUMBERS * 100}%`;
  $('board-note').textContent = count ? `${count} of 90 called · ${complete ? 'That’s a wrap!' : 'Eyes on your tickets!'}` : 'A fresh board. Who’s feeling lucky?';
  $('recent').replaceChildren();
  const recent = state.called.slice(-10).reverse();
  for (let index = 0; index < 10; index++) {
    const item = document.createElement('li');
    item.textContent = recent[index] ?? '–';
    if (recent[index] === undefined) { item.className = 'empty'; item.setAttribute('aria-hidden', 'true'); }
    $('recent').append(item);
  }
  prizeUI.render();
  if ($('history-dialog').open) renderHistory();
}

function commit(nextState) {
  state = nextState;
  const saved = saveGame(storage, state);
  $('save-status').textContent = saved ? 'Saved on this device' : 'Not saved';
  if (!saved) notice('storage-warning', 'This browser can’t save your game. Keep this page open to avoid losing your progress.');
  else $('storage-warning').hidden = true;
  render();
}

function confirmAction(title, description, label, action) {
  pendingConfirmation = action;
  $('confirm-title').textContent = title;
  $('confirm-description').textContent = description;
  $('confirm-action').textContent = label;
  $('confirm-dialog').returnValue = '';
  $('confirm-dialog').showModal();
}
$('confirm-dialog').addEventListener('close', () => {
  const action = pendingConfirmation;
  pendingConfirmation = undefined;
  if ($('confirm-dialog').returnValue === 'confirm') action?.();
});

$('next').addEventListener('click', () => {
  if (drawLocked || state.called.length === TOTAL_NUMBERS) return;
  drawLocked = true;
  commit(drawNumber(state));
  const latest = state.called.at(-1);
  $('announcement').textContent = `Number ${latest}, ${numberWords(latest)}. ${state.called.length} of 90 called.`;
  if (state.voiceEnabled) voice.announce(latest, state.callLanguage);
  // Absorb accidental double taps without delaying the number announcement.
  setTimeout(() => { drawLocked = false; render(); }, 650);
});
$('undo').addEventListener('click', () => {
  if (!state.called.length) return;
  const last = state.called.at(-1);
  const action = () => { voice.stop(); commit(undoNumber(state)); toast(`Undid ${last}. It can be drawn again.`); };
  const affectsClaims = Object.values(state.claims).some((claim) => claim.at === state.called.length);
  if (affectsClaims) confirmAction(`Undo number ${last}?`, 'Claims recorded on this call will also be removed. Earlier claims will stay.', 'Undo call', action);
  else action();
});
$('new-game').addEventListener('click', () => {
  if (!state.called.length) { toast('Your fresh board is ready. Tap Next number to begin.'); return; }
  confirmAction('Start a fresh game?', 'This clears all called numbers and recorded claims on this device. Players, prize schemes and your voice setting will stay.', 'Start new game', () => {
    voice.stop();
    commit(restartGame(state));
    $('announcement').textContent = 'New game ready. All 90 numbers are waiting.';
    $('next').focus();
  });
});
$('voice-toggle').addEventListener('click', () => {
  commit({ ...state, voiceEnabled: !state.voiceEnabled });
  $('voice-warning').hidden = true;
  if (state.voiceEnabled) voice.speak('Voice is on. Let’s play Tambola!');
  else voice.stop();
});
$('repeat').addEventListener('click', () => {
  if (state.called.length) { $('voice-warning').hidden = true; voice.announce(state.called.at(-1), state.callLanguage); }
});
$('call-language').addEventListener('change', () => {
  voice.stop();
  $('voice-warning').hidden = true;
  commit({ ...state, callLanguage: normalizeLanguage($('call-language').value) });
  toast(`Call language: ${LANGUAGES[state.callLanguage].label}. ${state.called.length ? 'Tap Say it again to hear this number.' : 'Ready for your next call.'}`);
});
$('preview-voice').addEventListener('click', () => { $('voice-warning').hidden = true; voice.announce(22, state.callLanguage); });

function renderHistory() {
  $('history-description').textContent = `${state.called.length} numbers, in the order they were called.`;
  $('history-list').replaceChildren();
  state.called.forEach((number, index) => {
    const item = document.createElement('li');
    const position = document.createElement('small');
    position.textContent = `CALL ${index + 1}`;
    item.append(position, String(number));
    $('history-list').append(item);
  });
}
$('history').addEventListener('click', () => { renderHistory(); $('history-dialog').showModal(); });

function showNumberMessage(text) {
  $('number-message').value = text;
  $('open-whatsapp').href = whatsappMessageUrl(text);
  $('number-copy-status').textContent = '';
  $('number-share-dialog').showModal();
}
$('share-number').addEventListener('click', async () => {
  const message = numberMessage(state);
  if (!message || sharingNumber) return;
  sharingNumber = true;
  render();
  voice.stop();
  const result = await openNumberShare(message);
  sharingNumber = false;
  render();
  if (result === 'fallback') showNumberMessage(message);
  // Cancellation is quiet. A resolved share is not proof the message was sent.
});
$('copy-number').addEventListener('click', async () => {
  const message = numberMessage(state);
  if (!message) return;
  if (await copyText(message)) toast('Number message copied. Paste it into your WhatsApp group.');
  else showNumberMessage(message);
});
$('copy-number-message').addEventListener('click', async () => {
  const copied = await copyText($('number-message').value);
  $('number-copy-status').textContent = copied
    ? 'Copied. Paste the message into your WhatsApp group.'
    : 'Press and hold the selected message, then choose Copy.';
  if (!copied) { $('number-message').focus(); $('number-message').select(); }
});

$('share').addEventListener('click', () => {
  $('share-url').value = new URL('./', location.href).href;
  $('share-local-note').hidden = !['localhost', '127.0.0.1', '[::1]'].includes(location.hostname);
  $('share-dialog').showModal();
});

$('share-audio').addEventListener('click', async () => {
  if (!audioFile || sharingNumber) return;
  const request = audioRequest;
  const file = audioFile;
  sharingAudio = true;
  sharingNumber = true;
  render();
  voice.stop();
  const result = await shareFile(file);
  sharingAudio = false;
  sharingNumber = false;
  render();
  if (result === 'fallback' && request === audioRequest) {
    $('audio-share-status').textContent = 'This browser can’t share audio files. Download the MP3, then attach it in WhatsApp, or use Share number.';
  }
});
$('copy-link').addEventListener('click', async () => {
  if (await copyText($('share-url').value)) {
    $('share-dialog').close();
    toast('App link copied. Paste it into your group chat.');
  } else {
    $('share-url').focus();
    $('share-url').select();
    $('copy-link').textContent = 'Select and copy the link';
  }
});

if (document.fullscreenEnabled) {
  $('fullscreen').hidden = false;
  $('fullscreen').addEventListener('click', async () => {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await document.documentElement.requestFullscreen();
    } catch { toast('Full screen isn’t available in this browser.'); }
  });
  document.addEventListener('fullscreenchange', () => {
    $('fullscreen').textContent = document.fullscreenElement ? 'Exit full screen ↙' : 'Full screen ↗';
  });
}

window.addEventListener('storage', (event) => {
  if (event.key !== STORAGE_KEY) return;
  try {
    const updated = event.newValue ? parseGame(event.newValue) : newGame();
    voice.stop();
    // Close stale confirmations so they cannot act on a different game's state.
    pendingConfirmation = undefined;
    for (const dialog of document.querySelectorAll('dialog[open]')) dialog.close();
    state = updated;
    render();
    toast('Game updated in another tab. Use one tab to call numbers.');
  } catch { notice('storage-warning', 'Another tab saved an unreadable game. Your current board has been kept.'); }
});
render();

async function prepareOffline() {
  if (!('serviceWorker' in navigator)) {
    $('offline-status').textContent = 'Keep this page open to play';
    return;
  }
  try {
    await navigator.serviceWorker.register('./sw.js');
    const registration = await navigator.serviceWorker.ready;
    const pack = await new Promise((resolve) => {
      const channel = new MessageChannel();
      const timeout = setTimeout(() => { channel.port1.close(); resolve(null); }, 3000);
      channel.port1.onmessage = ({ data }) => { clearTimeout(timeout); channel.port1.close(); resolve(data); };
      registration.active?.postMessage({ type: 'offline-version' }, [channel.port2]);
    });
    const updateStatus = () => {
      $('offline-status').textContent = pack?.version === '1.6.0' && pack.audioClips === 270
        ? (navigator.onLine ? 'v1.6 · All 3 languages ready offline' : 'v1.6 · Offline · All 3 languages ready')
        : 'App update: reopen online, then close all Tambola tabs and reopen';
    };
    updateStatus();
    window.addEventListener('online', updateStatus);
    window.addEventListener('offline', updateStatus);
  } catch { $('offline-status').textContent = 'Offline setup unavailable · keep this page open'; }
}
prepareOffline();
