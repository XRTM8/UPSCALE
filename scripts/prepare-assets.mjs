import { copyFile, mkdir, readdir } from "node:fs/promises";
import { resolve } from "node:path";

const root = process.cwd();
const fonts = resolve(root, "public/assets/fonts");
const icons = resolve(root, "public/assets/icons");
await mkdir(fonts, { recursive: true });
await mkdir(icons, { recursive: true });
for (const family of ["ibm-plex-sans-arabic", "manrope"]) {
  const source = resolve(root, "node_modules/@fontsource", family);
  const files = await readdir(resolve(source, "files"));
  for (const file of files.filter((name) => /-(arabic|latin)-(400|500|600)-normal\.woff2$/.test(name))) {
    await copyFile(resolve(source, "files", file), resolve(fonts, file));
  }
  await copyFile(resolve(source, "LICENSE"), resolve(fonts, `${family}-LICENSE.txt`));
}
for (const name of ["plus", "film", "sliders-horizontal", "arrow-up-right", "x", "hard-drive", "folder-open", "square", "circle-check", "triangle-alert"]) {
  await copyFile(resolve(root, "node_modules/lucide-static/icons", `${name}.svg`), resolve(icons, `${name}.svg`));
}
await copyFile(resolve(root, "node_modules/lucide-static/LICENSE"), resolve(icons, "LICENSE.txt"));
console.log("Prepared local fonts and icons.");
