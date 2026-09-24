import { numberWords, TOTAL_NUMBERS } from './game.js';
import { normalizeLanguage } from './languages.js';

export const IMAGE_SIZE = { width: 1080, height: 1540 };

export function imageSummary(state) {
  const called = [...state.called];
  if (!called.length) return null;
  return { called, language: normalizeLanguage(state.callLanguage), latest: called.at(-1), count: called.length,
    recent: called.slice(-10).reverse(), remaining: TOTAL_NUMBERS - called.length };
}

// Draw from game data, without external fonts/images or a screenshot library.
export function drawBoardImage(canvas, summary) {
  canvas.width = IMAGE_SIZE.width;
  canvas.height = IMAGE_SIZE.height;
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
  function text(value, x, y, size, color = ink, weight = 600, align = 'left') {
    ctx.font = `${weight} ${size}px Arial, sans-serif`;
    ctx.fillStyle = color;
    ctx.textAlign = align;
    ctx.textBaseline = 'middle';
    ctx.fillText(String(value), x, y);
  }
  ctx.fillStyle = paper;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = pink;
  ctx.fillRect(0, 0, canvas.width, 112);
  text('tambola.', 56, 57, 54, '#ffffff', 800);
  text(`CALL ${summary.count} OF 90`, 1024, 58, 28, '#ffffff', 700, 'right');
  box(56, 144, 968, 344, '#fff4ce', '#ecdc9c', 28);
  text('LATEST NUMBER', 540, 192, 25, '#796535', 700, 'center');
  text(summary.latest, 540, 318, 190, ink, 800, 'center');
  text(numberWords(summary.latest, summary.language), 540, 444, 38, ink, 600, 'center');
  text('RECENT CALLS', 56, 538, 27, ink, 700);
  text('Latest first', 1024, 538, 25, muted, 400, 'right');
  for (let index = 0; index < 10; index++) {
    const x = 56 + index * 98;
    const latest = index === 0;
    box(x, 576, 86, 66, latest ? pink : '#ffffff', latest ? pink : '#e4dbd5');
    text(summary.recent[index] ?? '–', x + 43, 610, 34, latest ? '#ffffff' : ink, 700, 'center');
  }
  text('THE BOARD', 56, 700, 30, ink, 700);
  text(`${summary.count} called · ${summary.remaining} to go`, 1024, 700, 27, muted, 400, 'right');
  const called = new Set(summary.called);
  for (let number = 1; number <= TOTAL_NUMBERS; number++) {
    const x = 56 + ((number - 1) % 10) * 98;
    const y = 744 + Math.floor((number - 1) / 10) * 74;
    const latest = number === summary.latest;
    const marked = called.has(number);
    box(x, y, 86, 64, latest ? pink : marked ? '#f7d5e3' : '#ffffff', latest ? pink : marked ? '#cc83a0' : '#e4dbd5', 10);
    text(number, x + 43, y + 30, 34, latest ? '#ffffff' : marked ? '#842044' : muted, marked ? 800 : 500, 'center');
    if (marked) { ctx.fillStyle = latest ? '#ffffff' : '#a91e59'; ctx.fillRect(x + 29, y + 52, 28, 3); }
  }
  for (const [index, [label, fill, border]] of [['Latest', pink, pink], ['Called', '#f7d5e3', '#cc83a0'], ['Waiting', '#ffffff', '#e4dbd5']].entries()) {
    const x = 214 + index * 245;
    box(x, 1443, 24, 24, fill, border, 5);
    text(label, x + 38, 1456, 25, muted, 500);
  }
  text('Mark your tickets · This image shows the game at this call', 540, 1510, 24, muted, 400, 'center');
}

export async function createBoardImage(state, { canvas = document.createElement('canvas'), FileType = File } = {}) {
  const summary = imageSummary(state);
  if (!summary) return null;
  drawBoardImage(canvas, summary);
  const blob = await new Promise((resolve, reject) => {
    canvas.toBlob((value) => value ? resolve(value) : reject(new Error('PNG generation failed')), 'image/png');
  });
  return new FileType([blob], `Tambola-call-${summary.count}-number-${summary.latest}.png`, { type: 'image/png' });
}

// A slow encode from an earlier draw must never replace the current image.
export function createImagePreparer(onChange, makeFile = createBoardImage) {
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
    Promise.resolve().then(() => makeFile({ called: summary.called, callLanguage: summary.language })).then((file) => {
      if (!file) throw new Error('PNG unavailable');
      if (request === revision) onChange({ file, summary, pending: false, error: false });
    }).catch(() => {
      if (request === revision) onChange({ file: null, summary, pending: false, error: true });
    });
  };
}
