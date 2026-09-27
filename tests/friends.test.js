import test from 'node:test';
import assert from 'node:assert/strict';
import { invitationLocation, appIntent, DOWNLOAD } from '../friends/invite.js';

test('stable invitation accepts only a code fragment and a known language', () => {
  for (const query of ['', '?lang=en', '?lang=hi']) {
    assert.deepEqual(invitationLocation(`https://sbshrey.github.io/tambola-caller/friends/${query}#ABCDEFG2`),
      { code: 'ABCDEFG2', language: query === '?lang=hi' ? 'hi' : 'en' });
  }
  for (const suffix of ['', '#', '#ABCDEFG0', '#abcdefgh', '#%41BCDEFG2', '#ＡBCDEFG2', '#ABCDEFG2/extra',
    '?code=ABCDEFG2', '?server=https://evil.example#ABCDEFG2', '?lang=hi&lang=en#ABCDEFG2', '#ABCDEFG2?token=x', '#<script>']) {
    assert.equal(invitationLocation('https://sbshrey.github.io/tambola-caller/friends/' + suffix).code, null, suffix);
  }
});

test('explicit app handoff has a fixed package and publisher download destination', () => {
  assert.equal(appIntent('ABCDEFG2'), `intent://friends/ABCDEFG2#Intent;scheme=tambola-beta;package=io.github.sbshrey.tambola.game.beta;S.browser_fallback_url=${encodeURIComponent(DOWNLOAD)};end`);
  for (const code of ['ABCDEFG2;end', 'ABC', 'ABCDEFG0', 'abcdefg2']) assert.throws(() => appIntent(code));
});
