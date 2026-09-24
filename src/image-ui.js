import { IMAGE_FORMATS, createImagePreparer, imageDescription } from './board-image.js';
import { shareFile } from './sharing.js';

// Prepare each PNG before the tap so mobile file sharing keeps user activation.
export function createImageUI({ isSharing, setSharing, stopVoice }) {
  const $ = (id) => document.getElementById(id);
  const kinds = Object.keys(IMAGE_FORMATS);
  const buttons = { combined: $('share-image'), number: $('share-number-image'), recent: $('share-recent-image'), board: $('share-board-image') };
  const images = Object.fromEntries(kinds.map((kind) => [kind, { file: null, pending: false, error: false }]));
  let previewKind = 'combined';
  let previewFile;
  let previewUrl;
  let sharingKind;

  function releasePreview() {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = null;
    previewFile = null;
    $('image-preview').removeAttribute('src');
    $('download-image').removeAttribute('href');
  }
  const preparers = kinds.map((kind) => createImagePreparer((image) => {
    images[kind] = image;
    if (kind === previewKind && previewFile && image.file !== previewFile) {
      releasePreview();
      if ($('image-share-dialog').open) $('image-share-dialog').close();
    }
    renderControls();
  }, undefined, kind));

  function renderControls() {
    for (const kind of kinds) {
      buttons[kind].disabled = !images[kind].file || isSharing();
      buttons[kind].setAttribute('aria-busy', String(images[kind].pending || sharingKind === kind));
    }
    $('preview-image').disabled = !images.combined.file || isSharing();
    $('share-image-preview').disabled = !images[previewKind].file || isSharing();
    $('share-image-label').textContent = sharingKind === 'combined' ? 'Opening…' : images.combined.pending ? 'Preparing image…' : 'Share full summary';
    $('image-share-hint').textContent = kinds.some((kind) => images[kind].error)
      ? 'Some images are unavailable. Reopen to retry, or share the number as text.'
      : 'Current number + last 10 calls + full board · PNG';
  }
  function showPreview(kind, fallback = false) {
    const image = images[kind];
    if (!image.file) return;
    releasePreview();
    previewKind = kind;
    previewFile = image.file;
    previewUrl = URL.createObjectURL(image.file);
    const format = IMAGE_FORMATS[kind];
    $('image-share-title').textContent = `Share ${format.title.toLowerCase()}`;
    $('image-preview').src = previewUrl;
    $('image-preview').alt = imageDescription(image.summary, kind);
    $('image-preview').width = format.width;
    $('image-preview').height = format.height;
    $('download-image').href = previewUrl;
    $('download-image').download = image.file.name;
    $('image-share-description').textContent = fallback
      ? 'This browser can’t share the image directly. Download the PNG, then attach it in your WhatsApp group. You can also press and hold the image to save it.'
      : 'Choose WhatsApp → your group → Send. Or download the PNG and attach it in WhatsApp.';
    renderControls();
    if (!$('image-share-dialog').open) $('image-share-dialog').showModal();
  }
  async function shareImage(kind) {
    const file = images[kind].file;
    if (!file || isSharing()) return;
    sharingKind = kind;
    setSharing(true);
    stopVoice();
    // No encoding or other await between the gesture and native sharing.
    const result = await shareFile(file);
    sharingKind = null;
    setSharing(false);
    if (result === 'fallback' && images[kind].file === file) showPreview(kind, true);
  }
  for (const kind of kinds) buttons[kind].addEventListener('click', () => shareImage(kind));
  $('preview-image').addEventListener('click', () => showPreview('combined'));
  $('share-image-preview').addEventListener('click', () => shareImage(previewKind));
  $('image-share-dialog').addEventListener('close', () => { if (!$('image-share-dialog').open) releasePreview(); });
  return { render(state) { for (const prepare of preparers) prepare(state); renderControls(); } };
}
