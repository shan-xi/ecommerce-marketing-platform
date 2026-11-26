import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const packageJson = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
const appTs = await readFile(new URL('../src/app/App.tsx', import.meta.url), 'utf8');
const apiTs = await readFile(new URL('../src/shared/lib/healthApi.ts', import.meta.url), 'utf8');
const viteConfig = await readFile(new URL('../vite.config.ts', import.meta.url), 'utf8');
const tsConfig = JSON.parse(await readFile(new URL('../tsconfig.app.json', import.meta.url), 'utf8'));

test('frontend dependencies are pinned to exact versions', () => {
  for (const [name, version] of Object.entries({
    ...packageJson.dependencies,
    ...packageJson.devDependencies,
  })) {
    assert.match(version, /^\d+\.\d+\.\d+$/, `${name} must use an exact version`);
  }
});

test('TypeScript strict mode is enabled', () => {
  assert.equal(tsConfig.compilerOptions.strict, true);
  assert.equal(tsConfig.compilerOptions.noEmit, true);
});

test('frontend calls the backend health endpoint through a Vite proxy', () => {
  assert.match(apiTs, /fetch\('\/actuator\/health'/);
  assert.match(viteConfig, /'\/actuator'/);
  assert.match(viteConfig, /http:\/\/localhost:8080/);
});

test('Phase 1 shell contains no implemented business workflow', () => {
  assert.match(appTs, /PHASE 1/);
  assert.doesNotMatch(appTs, /coupon claim|campaign create|publish campaign/i);
});
