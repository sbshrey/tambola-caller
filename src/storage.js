import { newGame, parseGame } from './game.js';

export const STORAGE_KEY = 'tambola-caller.game.v2';
export const LEGACY_STORAGE_KEY = 'tambola-caller.game.v1';

export function loadGame(storage) {
  try {
    const current = storage.getItem(STORAGE_KEY);
    const raw = current ?? storage.getItem(LEGACY_STORAGE_KEY);
    const state = raw ? parseGame(raw) : newGame();
    // Keep the original save as a backup; older open app versions cannot overwrite v2.
    if (!current && raw) saveGame(storage, state);
    return { state, error: null };
  } catch (error) {
    return { state: newGame(), error };
  }
}

export function saveGame(storage, state) {
  try {
    storage.setItem(STORAGE_KEY, JSON.stringify(state));
    return true;
  } catch {
    return false;
  }
}
