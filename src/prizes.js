export const CLAIMS = Object.freeze({
  early5: { label: 'Early 5', minimum: 5 }, top: { label: 'Top line', minimum: 5 },
  middle: { label: 'Middle line', minimum: 5 }, bottom: { label: 'Bottom line', minimum: 5 },
  full: { label: 'Full house', minimum: 15 },
});
export function defaultSetup() {
  return { players: [], schemes: Object.entries(CLAIMS).map(([id, value]) => ({ id, ...value, prize: 10, enabled: true })) };
}
export function formatMoney(paise) {
  return `₹${(paise / 100).toLocaleString('en-IN', { minimumFractionDigits: paise % 100 ? 2 : 0, maximumFractionDigits: 2 })}`;
}
export function validPrize(value) { return Number.isInteger(value) && value >= 0 && value <= 100000; }
function validName(value) { return typeof value === 'string' && value.trim().length > 0 && value === value.trim() && value.length <= 60 && !/[\r\n]/.test(value); }
function validId(value) { return typeof value === 'string' && /^[a-zA-Z0-9_-]{1,80}$/.test(value) && !['__proto__', 'constructor', 'prototype'].includes(value); }
export function validateSetup(setup) {
  const { players, schemes } = setup;
  if (!Array.isArray(players) || players.length > 100 || !Array.isArray(schemes) || !schemes.length || schemes.length > 20) throw Error('Use up to 100 players and 1–20 prize schemes.');
  for (const list of [players, schemes]) {
    if (new Set(list.map((item) => item?.id)).size !== list.length) throw Error('Duplicate IDs in setup.');
  }
  const names = new Set();
  for (const player of players) {
    if (!validId(player?.id) || !validName(player.name)) throw Error('Player names must be 1–60 characters.');
    const key = player.name.toLocaleLowerCase('en-IN');
    if (names.has(key)) throw Error('Use different player names, such as “Asha S” and “Asha R”.');
    names.add(key);
  }
  const labels = new Set();
  for (const scheme of schemes) {
    if (!validId(scheme?.id) || !validName(scheme.label) || !Number.isInteger(scheme.minimum) || scheme.minimum < 1 || scheme.minimum > 90 || !validPrize(scheme.prize) || typeof scheme.enabled !== 'boolean') throw Error('Each scheme needs a name and a whole-rupee prize from ₹0 to ₹1,00,000.');
    if (Object.hasOwn(CLAIMS, scheme.id) && scheme.minimum !== CLAIMS[scheme.id].minimum) throw Error('The earliest call for a standard scheme cannot change.');
    const key = scheme.label.toLocaleLowerCase('en-IN');
    if (labels.has(key)) throw Error('Use a different name for each prize scheme.');
    labels.add(key);
  }
  return { players: players.map(({ id, name }) => ({ id, name })), schemes: schemes.map(({ id, label, minimum, prize, enabled }) => ({ id, label, minimum, prize, enabled })) };
}
export function configureGame(state, setup) {
  const next = validateSetup(setup);
  for (const [id, claim] of Object.entries(state.claims)) {
    if (!next.schemes.some((scheme) => scheme.id === id && scheme.enabled && scheme.minimum <= claim.at)) throw Error('Remove the recorded claim before removing or disabling its scheme.');
    if (claim.winnerIds.some((winner) => !next.players.some((player) => player.id === winner))) throw Error('Remove a player’s winner assignment before removing that player.');
  }
  return { ...state, ...next };
}
export function winnerNames(state, claim) {
  return claim.winnerIds.map((id) => state.players.find((player) => player.id === id)?.name).filter(Boolean);
}
export function awardShares(state, claim) {
  const names = winnerNames(state, claim);
  return names.map((name, index) => ({ id: claim.winnerIds[index], name, paise: claim.prize * 100 / names.length }));
}
export function claimLabel(state, claim) {
  const shares = awardShares(state, claim);
  if (shares.length) return shares.map(({ name, paise }) => `${name} · ${formatMoney(paise)}`).join(' + ');
  return claim.legacyWinner || 'Winner not selected';
}
export function resultsMessage(state) {
  if (!Object.keys(state.claims).length) return '';
  const lines = ['🏆 Tambola results', `After ${state.called.length} of 90 calls`, ''];
  const totals = new Map();
  for (const scheme of state.schemes.filter((item) => item.enabled)) {
    const claim = state.claims[scheme.id];
    if (!claim) { lines.push(`${scheme.label}: Not claimed`); continue; }
    const shares = awardShares(state, claim);
    lines.push(`${scheme.label} — ${formatMoney(claim.prize * 100)} prize`);
    if (shares.length) {
      for (const share of shares) {
        lines.push(`  ${share.name}: ${formatMoney(share.paise)}`);
        const previous = totals.get(share.id);
        totals.set(share.id, { name: share.name, paise: (previous?.paise ?? 0) + share.paise });
      }
    } else lines.push(claim.legacyWinner ? `  Previous winner note: ${claim.legacyWinner}` : '  Winner not selected');
    lines.push('');
  }
  if (totals.size) {
    if (lines.at(-1) !== '') lines.push('');
    lines.push('Player totals');
    for (const { name, paise } of totals.values()) lines.push(`${name}: ${formatMoney(paise)}`);
    lines.push(`Total assigned: ${formatMoney([...totals.values()].reduce((sum, item) => sum + item.paise, 0))}`);
  }
  lines.push('', 'Congratulations! 🎉');
  return lines.join('\n');
}
