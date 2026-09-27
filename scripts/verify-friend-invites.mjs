import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve, extname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { execFileSync } from 'node:child_process';

// Opt-in browser acceptance, using an installed Playwright module and its Chromium runtime.
const modulePath = process.env.TAMBOLA_PLAYWRIGHT_MODULE;
const { chromium } = await import(modulePath ? pathToFileURL(modulePath).href : 'playwright');
const root = fileURLToPath(new URL('../', import.meta.url));
const evidence = resolve(root, process.env.TAMBOLA_INVITE_REVIEW_DIR || 'full-game/.test-workspace/friend-invite-browser');
await mkdir(evidence, { recursive: true });
const legacy = execFileSync('git', ['show', '76b6a2c5da5a4729a7dbcd28fac83878780ac9d8:sw.js'], { cwd: root, encoding: 'utf8', windowsHide: true });
let oldWorker = true;
const server = createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    let name = url.pathname.replace(/^\/tambola-caller\//, '');
    if (name === '') name = 'index.html';
    if (name === 'friends/') name = 'friends/index.html';
    if (!/^(?:index\.html|styles\.css|sw\.js|icon\.svg|manifest\.webmanifest|friends\/(?:index\.html|invite\.js|styles\.css)|(?:src|icons)\/[\w.-]+|audio\/(?:hi\/|hinglish\/)?numbers\/\d{2}\.mp3)$/.test(name)) {
      res.writeHead(404); res.end(); return;
    }
    const body = name === 'sw.js' && oldWorker ? legacy : await readFile(resolve(root, name));
    const types = { '.js': 'text/javascript', '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.mp3': 'audio/mpeg', '.svg': 'image/svg+xml', '.png': 'image/png', '.webmanifest': 'application/manifest+json' };
    res.writeHead(200, { 'content-type': types[extname(name)], 'cache-control': 'no-store' }); res.end(body);
  } catch { res.writeHead(404); res.end(); }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const origin = `http://127.0.0.1:${server.address().port}`;
const base = origin + '/tambola-caller/';
const browser = await chromium.launch({ headless: true });
const result = { passed: false, checks: [], scope: 'Headless Chromium, owned local fixtures. No Android browser-to-app or physical-phone acceptance.' };
try {
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const caller = await context.newPage();
  await caller.goto(base);
  await caller.evaluate(async () => { await navigator.serviceWorker.ready; });
  await caller.reload();
  await caller.evaluate(() => { window.routingSentinel = 'caller-still-open'; });
  const callerState = await caller.evaluate(() => JSON.stringify(localStorage));
  const invite = await context.newPage();
  await invite.goto(base + 'friends/#ABCDEFG2');
  assert.equal(await invite.locator('#invitation').count(), 0, 'Legacy worker must reproduce wrong-page navigation');
  await invite.screenshot({ path: resolve(evidence, 'legacy-worker-wrong-page.png') });
  result.checks.push('Legacy v1.6.0 worker reproduced serving caller HTML for an invitation');
  oldWorker = false;
  // A real invitation navigation must discover the updated worker without a manual update call.
  await invite.reload();
  try { await invite.locator('#open[href^="intent:"]').waitFor({ timeout: 30000 }); }
  catch (error) { await invite.screenshot({ path: resolve(evidence, 'upgrade-failure.png'), fullPage: true }); console.error('Upgrade URL:', invite.url()); throw error; }
  assert.equal(await invite.locator('#code').textContent(), 'ABCD EFG2');
  assert.equal(await caller.evaluate(() => window.routingSentinel), 'caller-still-open');
  assert.equal(await caller.evaluate(() => JSON.stringify(localStorage)), callerState);
  await context.setOffline(true);
  await caller.reload();
  assert.ok((await caller.title()).includes('Tambola'));
  await context.setOffline(false);
  result.checks.push('Routing update repairs the old invitation tab, leaves caller document/storage intact, retains caller offline reload');
  await context.close();

  const fresh = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await fresh.newPage();
  const errors = [];
  const requests = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  page.on('request', request => requests.push({ url: request.url(), type: request.resourceType() }));
  await page.goto(base + 'friends/#ABCDEFG2');
  await page.locator('#open[href^="intent:"]').waitFor();
  assert.equal(await page.locator('#open').getAttribute('href'), 'intent://friends/ABCDEFG2#Intent;scheme=tambola-beta;package=io.github.sbshrey.tambola.game.beta;S.browser_fallback_url=https%3A%2F%2Fgithub.com%2Fsbshrey%2Ftambola-caller%2Freleases%2Ftag%2Ffull-game-alpha33-readable-claims;end');
  await page.screenshot({ path: resolve(evidence, 'english-desktop.png'), fullPage: true });
  await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { async writeText(value) { window.copiedCode = value; } } }));
  await page.getByRole('button', { name: 'Copy code', exact: true }).click();
  assert.equal(await page.evaluate(() => window.copiedCode), 'ABCDEFG2');
  await page.goto(base + 'friends/#HJKM2345');
  await page.waitForFunction(() => document.getElementById('code').textContent === 'HJKM 2345');
  assert.ok((await page.locator('#open').getAttribute('href')).startsWith('intent://friends/HJKM2345#'));
  await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { async writeText() { throw Error('denied'); } } }));
  await page.getByRole('button', { name: 'Copy code', exact: true }).click();
  assert.equal(await page.evaluate(() => getSelection().toString()), 'HJKM 2345');
  assert.ok((await page.locator('#copy-status').textContent()).includes('selected code'));
  await page.goto(base + 'friends/#HJKM2345?token=x');
  await page.locator('#invalid').waitFor();
  assert.equal(await page.locator('#open').getAttribute('href'), null);
  result.checks.push('Explicit fixed-package intent; valid code change refreshes; invalid change removes launch; copy success and permission fallback');

  for (const language of ['en', 'hi']) {
    await page.setViewportSize({ width: 360, height: 800 });
    await page.goto(base + `friends/?lang=${language}#ABCDEFG2`);
    await page.locator('#open[href^="intent:"]').waitFor();
    for (const size of ['16px', '32px']) {
      await page.evaluate(value => { document.documentElement.style.fontSize = value; }, size);
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${language} ${size} horizontal overflow`);
      if (size === '16px') assert.ok((await page.locator('#open').boundingBox()).y < 744, 'Open app must be visible on a normal mobile screen');
      for (const selector of ['#english', '#hindi', '#copy', '#open', '#download']) {
        const box = await page.locator(selector).boundingBox(); assert.ok(box.height >= 48, `${selector} target size`);
      }
      await page.screenshot({ path: resolve(evidence, `${language}-mobile-${size}.png`), fullPage: true });
    }
  }
  await page.locator('#english').click();
  assert.ok(page.url().endsWith('?lang=en#ABCDEFG2'));
  await page.keyboard.press('Tab');
  assert.ok(await page.evaluate(() => ['A', 'BUTTON'].includes(document.activeElement.tagName)));
  result.checks.push('English/Hindi mobile at normal and 200% text: no horizontal overflow, 48px targets, language preserves code, keyboard focus');
  assert.deepEqual(errors, []);
  assert.ok(requests.every(request => request.url.startsWith(origin) && !request.url.includes('ABCDEFG2') && !['fetch', 'xhr', 'websocket'].includes(request.type)));
  result.checks.push('No browser errors, external requests, room lookups or code-bearing network requests');
  await fresh.close();
  result.passed = true;
} finally {
  result.observedAt = new Date().toISOString();
  await writeFile(resolve(evidence, 'validation.json'), JSON.stringify(result, null, 2) + '\n');
  await browser.close(); server.closeAllConnections(); server.close();
}
console.log(JSON.stringify(result));
