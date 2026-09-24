import { numberWords, TOTAL_NUMBERS } from './game.js';
import { normalizeLanguage } from './languages.js';
import { getCall } from './calls.js';

export const IMAGE_SIZE = { width: 1080, height: 1540 };
export const IMAGE_FORMATS = Object.freeze({
  combined: { ...IMAGE_SIZE, title: 'Full game summary' },
  number: { width: 1080, height: 1080, title: 'Current number' },
  recent: { width: 1080, height: 720, title: 'Recent numbers' },
  board: { width: 1080, height: 1140, title: 'Tambola board' },
});

function imageFormat(kind) {
  if (!Object.hasOwn(IMAGE_FORMATS, kind)) throw new RangeError('Unknown image format');
  return IMAGE_FORMATS[kind];
}

export function imageDescription(summary, kind = 'combined') {
  imageFormat(kind);
  const call = `Call ${summary.count}: ${summary.latest}.`;
  if (kind === 'number') return `${call} ${numberWords(summary.latest, summary.language)}. ${getCall(summary.latest, summary.language).phrase}.`;
  const recent = `Last ${summary.recent.length} ${summary.recent.length === 1 ? 'call' : 'calls'}, latest first: ${summary.recent.join(', ')}.`;
  const board = `${summary.count} of 90 board numbers marked; ${summary.latest} highlighted as latest.`;
  return kind === 'recent' ? recent : kind === 'board' ? board : `${call} ${recent} ${board}`;
}

export function imageSummary(state) {
  const called = [...state.called];
  if (!called.length) return null;
  return { called, language: normalizeLanguage(state.callLanguage), latest: called.at(-1), count: called.length,
    recent: called.slice(-10).reverse(), remaining: TOTAL_NUMBERS - called.length };
}

// Draw from game data, without external fonts/images or a screenshot library.
export function drawBoardImage(canvas, summary, kind = 'combined') {
  const format = imageFormat(kind);
  canvas.width = format.width;
  canvas.height = format.height;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Image drawing is unavailable');
  const pink = '#a91e59', ink = '#30252c', muted = '#716670', paper = '#fcf8f1';
  function box(x, y, width, height, fill, stroke, radius = 14) {
    ctx.beginPath();
    ctx.moveTo(x + radius, y);
    ctx.arcTo(x + width, y, x + width, y + height, radius);
    ctx.arcTo(x + width, y + height, x, y + height, radius);
    ctx.arcTo(x, y + height, x, y, radius);
    ctx.arcTo(x, y, x + width, y, radius);
    ctx.closePath();
    ctx.fillStyle = fill;
    ctx.fill();
    if (stroke) { ctx.strokeStyle = stroke; ctx.lineWidth = 2; ctx.stroke(); }
  }
  function text(value, x, y, size, color = ink, weight = 600, align = 'left', maxWidth = 968) {
    const family = /[\u0900-\u097f]/.test(String(value))
      ? '"Nirmala UI", "Noto Sans Devanagari", "Kohinoor Devanagari", "Devanagari Sangam MN", Mangal, sans-serif'
      : 'Arial, sans-serif';
    ctx.font = `${weight} ${size}px ${family}`;
    ctx.fillStyle = color;
    ctx.textAlign = align;
    ctx.textBaseline = 'middle';
    ctx.fillText(String(value), x, y, maxWidth);
  }
  function recentGrid(y, compact = false) {
    const columns = compact ? 10 : 5;
    const step = compact ? 98 : 196;
    const width = compact ? 86 : 180;
    const height = compact ? 66 : 136;
    for (let index = 0; index < 10; index++) {
      const x = 56 + (index % columns) * step;
      const top = y + Math.floor(index / columns) * 156;
      const latest = index === 0;
      box(x, top, width, height, latest ? pink : '#ffffff', latest ? pink : '#e4dbd5');
      text(summary.recent[index] ?? '–', x + width / 2, top + height / 2, compact ? 34 : 68, latest ? '#ffffff' : ink, 700, 'center');
    }
  }
  function boardGrid(y, step = 74, height = 64) {
    const called = new Set(summary.called);
    for (let number = 1; number <= TOTAL_NUMBERS; number++) {
      const x = 56 + ((number - 1) % 10) * 98;
      const top = y + Math.floor((number - 1) / 10) * step;
      const latest = number === summary.latest;
      const marked = called.has(number);
      box(x, top, 86, height, latest ? pink : marked ? '#f7d5e3' : '#ffffff', latest ? pink : marked ? '#cc83a0' : '#e4dbd5', 10);
      text(number, x + 43, top + height / 2 - 2, 34, latest ? '#ffffff' : marked ? '#842044' : muted, marked ? 800 : 500, 'center');
      if (marked) { ctx.fillStyle = latest ? '#ffffff' : '#a91e59'; ctx.fillRect(x + 29, top + height - 12, 28, 3); }
    }
  }
  function legend(y) {
    for (const [index, [label, fill, border]] of [['Latest', pink, pink], ['Called', '#f7d5e3', '#cc83a0'], ['Waiting', '#ffffff', '#e4dbd5']].entries()) {
      const x = 214 + index * 245;
      box(x, y, 24, 24, fill, border, 5);
      text(label, x + 38, y + 13, 25, muted, 500);
    }
  }
  ctx.fillStyle = paper;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = pink;
  ctx.fillRect(0, 0, canvas.width, 112);
  text('tambola.', 56, 57, 54, '#ffffff', 800);
  text(`CALL ${summary.count} OF 90`, 1024, 58, 28, '#ffffff', 700, 'right');
  if (kind === 'number') {
    box(56, 160, 968, 784, '#fff4ce', '#ecdc9c', 28);
    text('NUMBER CALLED', 540, 236, 30, '#796535', 700, 'center');
    text(summary.latest, 540, 490, 360, ink, 800, 'center');
    text(numberWords(summary.latest, summary.language), 540, 740, 64, ink, 700, 'center', 860);
    text(getCall(summary.latest, summary.language).phrase, 540, 854, 42, pink, 600, 'center', 860);
  } else if (kind === 'recent') {
    text('RECENT NUMBERS', 56, 184, 36, ink, 700);
    text('Latest first', 1024, 184, 28, muted, 400, 'right');
    recentGrid(260);
    text(`Last ${summary.recent.length} ${summary.recent.length === 1 ? 'call' : 'calls'} · ${summary.count} called · ${summary.remaining} to go`, 540, 624, 28, muted, 500, 'center');
  } else if (kind === 'board') {
    text('THE TAMBOLA BOARD', 56, 180, 34, ink, 700);
    text(`${summary.count} called · ${summary.remaining} to go`, 1024, 180, 27, muted, 400, 'right');
    boardGrid(230, 84, 72);
    legend(1028);
  } else {
    box(56, 144, 968, 344, '#fff4ce', '#ecdc9c', 28);
    text('LATEST NUMBER', 540, 192, 25, '#796535', 700, 'center');
    text(summary.latest, 540, 318, 190, ink, 800, 'center');
    text(numberWords(summary.latest, summary.language), 540, 444, 38, ink, 600, 'center');
    text('RECENT CALLS', 56, 538, 27, ink, 700);
    text('Latest first', 1024, 538, 25, muted, 400, 'right');
    recentGrid(576, true);
    text('THE BOARD', 56, 700, 30, ink, 700);
    text(`${summary.count} called · ${summary.remaining} to go`, 1024, 700, 27, muted, 400, 'right');
    boardGrid(744);
    legend(1443);
  }
  text('Mark your tickets · Snapshot of this call', 540, canvas.height - 36, 24, muted, 400, 'center');
}

export async function createBoardImage(state, { canvas = document.createElement('canvas'), FileType = File, kind = 'combined' } = {}) {
  imageFormat(kind);
  const summary = imageSummary(state);
  if (!summary) return null;
  drawBoardImage(canvas, summary, kind);
  const blob = await new Promise((resolve, reject) => {
    canvas.toBlob((value) => value ? resolve(value) : reject(new Error('PNG generation failed')), 'image/png');
  });
  const prefix = kind === 'combined' ? 'Tambola' : `Tambola-${kind}`;
  return new FileType([blob], `${prefix}-call-${summary.count}-number-${summary.latest}.png`, { type: 'image/png' });
}

// A slow encode from an earlier draw must never replace the current image.
export function createImagePreparer(onChange, makeFile = createBoardImage, kind = 'combined') {
  imageFormat(kind);
  let lastKey;
  let revision = 0;
  return (state) => {
    const summary = imageSummary(state);
    const key = `${normalizeLanguage(state.callLanguage)}:${state.called.join(',')}`;
    if (key === lastKey) return;
    lastKey = key;
    const request = ++revision;
    onChange({ file: null, summary, pending: Boolean(summary), error: false });
    if (!summary) return;
    Promise.resolve().then(() => makeFile({ called: summary.called, callLanguage: summary.language }, { kind })).then((file) => {
      if (!file) throw new Error('PNG unavailable');
      if (request === revision) onChange({ file, summary, pending: false, error: false });
    }).catch(() => {
      if (request === revision) onChange({ file: null, summary, pending: false, error: true });
    });
  };
}
