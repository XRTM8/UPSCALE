import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createServer } from "node:http";
import { readFile, mkdir } from "node:fs/promises";
import { resolve, extname, sep } from "node:path";
import { chromium } from "playwright-core";

const root = resolve("dist");
const scratch = resolve(".tmp");
const screenshots = resolve(scratch, "screenshots");
await mkdir(screenshots, { recursive: true });
const mime = { ".html": "text/html", ".js": "text/javascript", ".wasm": "application/wasm", ".css": "text/css", ".svg": "image/svg+xml", ".woff2": "font/woff2" };
const server = createServer(async (request, response) => {
  try {
    const pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname);
    const file = resolve(root, `.${pathname === "/" ? "/index.html" : pathname}`);
    if (!file.startsWith(root + sep)) { response.writeHead(403).end(); return; }
    const bytes = await readFile(file);
    response.writeHead(200, { "Content-Type": mime[extname(file)] || "application/octet-stream" }).end(bytes);
  } catch { response.writeHead(404).end(); }
});
await new Promise((ready) => server.listen(0, "127.0.0.1", ready));
const base = `http://127.0.0.1:${server.address().port}`;
let browser;
try {
  browser = await chromium.launch({ headless: true, ...(process.env.UPSCALE_BROWSER_PATH ? { executablePath: process.env.UPSCALE_BROWSER_PATH } : { channel: process.platform === "win32" ? "msedge" : "chrome" }) });
  const context = await browser.newContext({ viewport: { width: 1180, height: 850 }, hasTouch: true });
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(base);
  await page.waitForSelector("#studio .tc-head");
  await page.evaluate(() => document.fonts.ready);
  assert.equal(await page.locator(".tc-app").getAttribute("dir"), "rtl");
  await page.screenshot({ path: resolve(screenshots, "desktop-ar.png"), fullPage: true });
  await page.locator('[data-open="output"]').click();
  const platform = page.locator("#output-panel select").nth(0);
  const post = page.locator("#output-panel select").nth(1);
  const resolution = page.locator("#output-panel select").nth(2);
  await platform.selectOption("youtube");
  await post.selectOption("landscape");
  await resolution.selectOption("uhd");
  await page.waitForFunction(() => document.querySelector(".tc-screen").classList.contains("landscape"));
  assert.match(await page.locator(".tc-preset-value").textContent(), /3840 × 2160/);
  await page.locator("#output-panel").press("Escape");
  assert.equal(await page.locator("#output-panel").isVisible(), false);
  await page.locator(".tc-language").click();
  await page.waitForFunction(() => document.documentElement.lang === "en");
  await page.reload();
  await page.waitForSelector(".tc-head");
  assert.equal(await page.locator(".tc-app").getAttribute("dir"), "ltr");
  await page.locator('[data-open="output"]').click();
  assert.equal(await platform.inputValue(), "youtube");
  assert.equal(await resolution.inputValue(), "uhd");
  await platform.selectOption("instagram");
  assert.equal(await resolution.inputValue(), "full_hd");
  assert.equal(await resolution.locator('option[value="uhd"]').isDisabled(), true);
  await post.selectOption("reel");
  await page.locator("#output-panel").press("Escape");

  let layouts = 0;
  for (const language of ["ar", "en"]) {
    if (await page.locator(".tc-app").getAttribute("lang") !== language) await page.locator(".tc-language").click();
    for (const width of [1180, 736, 390, 320]) {
      await page.setViewportSize({ width, height: width < 700 ? 720 : 850 });
      for (const panel of [null, "processing", "output"]) {
        if (panel) await page.locator(`[data-open="${panel}"]`).click();
        const layout = await page.locator(".tc-app").evaluate((app) => {
          const bounds = app.getBoundingClientRect();
          const bad = [...app.querySelectorAll("button,.tc-panel:not([hidden])")]
            .filter((element) => element.getClientRects().length)
            .filter((element) => { const r = element.getBoundingClientRect(); return r.left < bounds.left - 1 || r.right > bounds.right + 1 || r.top < bounds.top - 1 || r.bottom > bounds.bottom + 1; })
            .map((element) => element.textContent.trim());
          return { scroll: app.scrollWidth, width: app.clientWidth, bad };
        });
        assert.ok(layout.scroll <= layout.width + 1, JSON.stringify({ language, width, panel, layout }));
        assert.deepEqual(layout.bad, [], JSON.stringify({ language, width, panel, layout }));
        if (language === "ar" && width === 390) await page.screenshot({ path: resolve(screenshots, `mobile-${panel || "preview"}.png`), fullPage: true });
        if (panel) await page.locator(`#${panel}-panel .tc-close`).click();
        layouts++;
      }
    }
  }

  const fixture = resolve(scratch, "مقطع معاينة [1].mp4");
  const generated = spawnSync(process.env.UPSCALE_FFMPEG || "ffmpeg", ["-hide_banner", "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=180x320:rate=24:duration=1", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", fixture], { windowsHide: true });
  assert.equal(generated.status, 0, generated.stderr?.toString());
  const chooser = page.waitForEvent("filechooser");
  await page.locator(".tc-import").click();
  await (await chooser).setFiles(fixture);
  await page.waitForFunction(() => !document.querySelector(".tc-video").hidden && document.querySelector(".tc-video").readyState >= 1);
  assert.match(await page.locator(".tc-source").textContent(), /مقطع معاينة/);
  assert.equal(await page.locator(".tc-import").isDisabled(), true, "Browser preview must not pretend to export through Rust");
  assert.equal(await page.locator(".job-strip").count(), 0, "No fabricated export progress");
  assert.deepEqual(errors, []);
  console.log(`UI passed: ${layouts} responsive states, language/preferences, presets, and real local video preview.`);
} finally {
  await browser?.close();
  await new Promise((done) => server.close(done));
}
