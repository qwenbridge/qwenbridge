import { readFile, writeFile, mkdir } from 'node:fs/promises';
const target = new URL('../../qwenbridge-server/src/main/resources/META-INF/resources/web/', import.meta.url);
await mkdir(target, { recursive: true });
await writeFile(new URL('../dist/search-element.css', import.meta.url), await readFile(new URL('../src/search-element.css', import.meta.url)));
if (process.argv.includes('--build')) process.exit(0);
// Every module the Web Component imports, transitively (the browser loads them from /web/).
await mkdir(new URL('retry/', target), { recursive: true });
for (const file of ['commerce-client.js', 'errors.js', 'retry/retry-classifier.js', 'retry/retry-policy.js', 'web-component.js', 'search-element.css']) {
  const content = (await readFile(new URL('../dist/' + file, import.meta.url), 'utf8')).replace(/\n\/\/# sourceMappingURL=.*\n?$/, '\n');
  const path = new URL(file, target);
  if (process.argv.includes('--check')) { if (await readFile(path, 'utf8') !== content) throw new Error('Stale browser asset: ' + file); }
  else await writeFile(path, content);
}
