import { recordClaim, removeClaim } from './game.js';
import { configureGame, claimLabel, formatMoney, resultsMessage, winnerNames } from './prizes.js';
import { openNumberShare, copyText, whatsappMessageUrl } from './sharing.js';

export function createPrizeUI({ getState, commit, notify, announce, stopVoice }) {
  const $ = (id) => document.getElementById(id);
  let activeClaim;
  let draft;
  let sharing = false;
  const id = (prefix) => `${prefix}-${crypto.randomUUID()}`;
  const error = (field, value = '') => { $(field).textContent = value; $(field).hidden = !value; };
  const input = (value, label, onInput, type = 'text') => {
    const element = document.createElement('input');
    element.type = type; element.value = value; element.setAttribute('aria-label', label);
    if (type === 'text') element.maxLength = 60;
    else { element.min = '0'; element.max = '100000'; element.step = '1'; element.inputMode = 'numeric'; }
    element.addEventListener('input', () => onInput(type === 'number' ? (element.value === '' ? NaN : Number(element.value)) : element.value));
    return element;
  };
  function renderSetup() {
    const state = getState();
    $('player-list').replaceChildren();
    for (const player of draft.players) {
      const row = document.createElement('div'); row.className = 'setup-player';
      const name = input(player.name, `Player name: ${player.name}`, (value) => { player.name = value; });
      const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'text-button'; remove.textContent = 'Remove';
      remove.setAttribute('aria-label', `Remove player ${player.name}`);
      remove.disabled = Object.values(state.claims).some((claim) => claim.winnerIds.includes(player.id));
      remove.title = remove.disabled ? 'Remove this player’s winner assignments first' : '';
      remove.addEventListener('click', () => { draft.players = draft.players.filter((item) => item.id !== player.id); renderSetup(); });
      row.append(name, remove); $('player-list').append(row);
    }
    $('player-empty').hidden = Boolean(draft.players.length);
    $('scheme-list').replaceChildren();
    for (const scheme of draft.schemes) {
      const row = document.createElement('div'); row.className = 'setup-scheme';
      const enabled = document.createElement('input'); enabled.type = 'checkbox'; enabled.checked = scheme.enabled;
      enabled.setAttribute('aria-label', `Use ${scheme.label}`); enabled.disabled = Boolean(state.claims[scheme.id]);
      enabled.addEventListener('change', () => { scheme.enabled = enabled.checked; renderSetup(); });
      const name = input(scheme.label, `Scheme name: ${scheme.label}`, (value) => { scheme.label = value; });
      const prize = input(scheme.prize, `Prize in rupees for ${scheme.label}`, (value) => { scheme.prize = value; }, 'number');
      const amount = document.createElement('label'); amount.className = 'scheme-amount'; amount.append('₹ ', prize);
      row.append(enabled, name, amount);
      if (!scheme.enabled) {
        const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'text-button scheme-remove'; remove.textContent = 'Remove scheme';
        remove.setAttribute('aria-label', `Remove scheme ${scheme.label}`);
        remove.addEventListener('click', () => { draft.schemes = draft.schemes.filter((item) => item.id !== scheme.id); renderSetup(); });
        row.append(remove);
      }
      $('scheme-list').append(row);
    }
  }
  function openSetup() {
    draft = structuredClone({ players: getState().players, schemes: getState().schemes });
    $('new-players').value = ''; $('new-scheme').value = ''; error('setup-error');
    renderSetup(); $('setup-dialog').showModal();
  }
  $('manage-game').addEventListener('click', openSetup);
  $('setup-cancel').addEventListener('click', () => $('setup-dialog').close());
  $('add-players').addEventListener('click', () => {
    const names = $('new-players').value.split(/\r?\n|,/).map((name) => name.trim()).filter(Boolean);
    if (!names.length) { error('setup-error', 'Enter player names, one per line or separated by commas.'); return; }
    if (draft.players.length + names.length > 100) { error('setup-error', 'You can add up to 100 players.'); return; }
    draft.players.push(...names.map((name) => ({ id: id('p'), name })));
    $('new-players').value = ''; error('setup-error'); renderSetup();
  });
  $('add-scheme').addEventListener('click', () => {
    const name = $('new-scheme').value.trim();
    if (!name) { error('setup-error', 'Enter a name for the custom prize scheme.'); return; }
    if (draft.schemes.length >= 20) { error('setup-error', 'You can use up to 20 schemes. Untick and remove an unused scheme first.'); return; }
    draft.schemes.push({ id: id('s'), label: name, prize: 10, minimum: 1, enabled: true });
    $('new-scheme').value = ''; error('setup-error'); renderSetup();
  });
  $('setup-form').addEventListener('submit', (event) => {
    event.preventDefault();
    // Include names typed into the quick-add boxes even if Add was not tapped.
    const setup = structuredClone(draft);
    setup.players.push(...$('new-players').value.split(/\r?\n|,/).map((name) => name.trim()).filter(Boolean).map((name) => ({ id: id('p'), name })));
    const schemeName = $('new-scheme').value.trim();
    if (schemeName) setup.schemes.push({ id: id('s'), label: schemeName, prize: 10, minimum: 1, enabled: true });
    setup.players.forEach((player) => { player.name = player.name.trim(); });
    setup.schemes.forEach((scheme) => { scheme.label = scheme.label.trim(); });
    try { commit(configureGame(getState(), setup)); $('setup-dialog').close(); notify('Players and prize schemes saved. Your game continues.'); }
    catch (reason) { error('setup-error', reason.message); }
  });
  function fillWinners(selected = []) {
    for (const [index, field] of ['winner-one', 'winner-two'].entries()) {
      const select = $(field); select.replaceChildren(new Option(index ? 'No second winner' : 'Choose a player (optional)', ''));
      for (const player of getState().players) select.add(new Option(player.name, player.id));
      select.value = selected[index] ?? '';
    }
    updateSplit();
  }
  function selectedWinners() { return [$('winner-one').value, $('winner-two').value].filter(Boolean); }
  function updateSplit() {
    for (const [field, other] of [['winner-one', 'winner-two'], ['winner-two', 'winner-one']]) {
      for (const option of $(field).options) option.disabled = Boolean(option.value && option.value === $(other).value);
    }
    const count = selectedWinners().length;
    $('quick-add-player').disabled = count === 2;
    $('quick-add-player').title = count === 2 ? 'Clear one winner selection before adding another' : '';
    const prize = Number($('claim-prize').value);
    $('prize-split').textContent = Number.isInteger(prize) && prize >= 0
      ? `${formatMoney(prize * 100)} total${count ? ` · ${formatMoney(prize * 100 / count)} ${count === 2 ? 'each for 2 winners' : 'for 1 winner'}` : ' · select winners whenever you’re ready'}` : 'Enter a whole-rupee prize.';
  }
  $('winner-one').addEventListener('change', updateSplit);
  $('winner-two').addEventListener('change', updateSplit);
  $('claim-prize').addEventListener('input', updateSplit);
  function openClaim(type) {
    activeClaim = type;
    const state = getState(); const scheme = state.schemes.find((item) => item.id === type); const claim = state.claims[type];
    $('claim-title').textContent = `${scheme.label} — winners`;
    $('claim-prize').value = claim?.prize ?? scheme.prize;
    $('claim-at').textContent = claim ? `Recorded after call ${claim.at}. Editing keeps that call position.` : `${state.called.length} numbers called. The host confirms this claim.`;
    $('legacy-winner').hidden = !claim?.legacyWinner;
    $('legacy-winner').textContent = claim?.legacyWinner ? `Previous winner note: ${claim.legacyWinner}. Select players to replace it, or leave the selections blank to keep it.` : '';
    $('claim-remove').hidden = !claim;
    $('quick-player').value = ''; error('claim-error'); fillWinners(claim?.winnerIds);
    $('claim-dialog').showModal();
  }
  $('quick-add-player').addEventListener('click', () => {
    const name = $('quick-player').value.trim();
    const player = { id: id('p'), name };
    const selected = [$('winner-one').value, $('winner-two').value];
    try {
      const state = getState(); commit(configureGame(state, { ...state, players: [...state.players, player] }));
      const empty = selected.indexOf(''); if (empty >= 0) selected[empty] = player.id;
      fillWinners(selected); $('quick-player').value = ''; error('claim-error');
    } catch (reason) { error('claim-error', reason.message); }
  });
  $('claim-cancel').addEventListener('click', () => $('claim-dialog').close());
  $('claim-form').addEventListener('submit', (event) => {
    event.preventDefault();
    if ($('quick-player').value.trim()) { error('claim-error', 'Tap Add & select for the new player, or clear that name before saving.'); return; }
    try {
      const next = recordClaim(getState(), activeClaim, selectedWinners(), $('claim-prize').value === '' ? NaN : Number($('claim-prize').value), true);
      commit(next); $('claim-dialog').close();
      const names = winnerNames(next, next.claims[activeClaim]);
      const label = next.schemes.find((scheme) => scheme.id === activeClaim).label;
      const message = `${label} recorded${names.length ? ` for ${names.join(' and ')}` : ''}. Congratulations!`;
      notify(message); announce(message);
    } catch (reason) { error('claim-error', reason.message); }
  });
  $('claim-remove').addEventListener('click', () => {
    commit(removeClaim(getState(), activeClaim)); $('claim-dialog').close(); stopVoice(); notify('Claim removed.');
  });
  function refreshResults() {
    const message = resultsMessage(getState());
    $('results-message').value = message;
    $('results-whatsapp').href = whatsappMessageUrl(message);
    $('results-share').disabled = !message || sharing;
  }
  $('show-results').addEventListener('click', () => { refreshResults(); error('results-status'); $('results-dialog').showModal(); });
  $('results-share').addEventListener('click', async () => {
    const message = resultsMessage(getState());
    if (!message || sharing) return;
    sharing = true; refreshResults(); stopVoice();
    const result = await openNumberShare(message);
    sharing = false; refreshResults();
    if (result === 'fallback') error('results-status', 'Use Open WhatsApp to choose your group and send, or copy the results below.');
  });
  $('results-copy').addEventListener('click', async () => {
    if (await copyText($('results-message').value)) error('results-status', 'Copied. Paste these results into your group.');
    else { $('results-message').focus(); $('results-message').select(); error('results-status', 'Press and hold the selected results, then choose Copy.'); }
  });
  function render() {
    const state = getState();
    $('setup-summary').textContent = `${state.players.length} players · ${state.schemes.filter((scheme) => scheme.enabled).length} prize schemes · edit anytime`;
    $('claims').replaceChildren();
    for (const scheme of state.schemes.filter((item) => item.enabled)) {
      const claim = state.claims[scheme.id];
      const button = document.createElement('button'); button.className = 'claim-button'; button.dataset.claim = scheme.id;
      button.textContent = `${claim ? '✓ ' : ''}${scheme.label}`; button.setAttribute('aria-pressed', String(Boolean(claim)));
      button.disabled = state.called.length < scheme.minimum;
      button.title = button.disabled ? `Available after ${scheme.minimum} numbers are called` : `Record or edit ${scheme.label}`;
      const detail = document.createElement('span'); detail.className = 'claim-winner';
      detail.textContent = claim ? claimLabel(state, claim) : `${formatMoney(scheme.prize * 100)} prize`;
      button.append(detail); button.addEventListener('click', () => openClaim(scheme.id)); $('claims').append(button);
    }
    $('show-results').disabled = !Object.keys(state.claims).length;
    if ($('results-dialog').open) refreshResults();
  }
  return { render };
}
