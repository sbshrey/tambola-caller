import { newGame, parseGame } from './game.js';

export const STORAGE_KEY = 'tambola-caller.game.v1';

export function loadGame(storage) {
  try {
    const raw = storage.getItem(STORAGE_KEY);
    return { state: raw ? parseGame(raw) : newGame(), error: null };
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
