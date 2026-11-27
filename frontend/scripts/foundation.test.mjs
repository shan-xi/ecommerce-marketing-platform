import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const packageJson = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
const appTs = await readFile(new URL('../src/app/App.tsx', import.meta.url), 'utf8');
const authApi = await readFile(new URL('../src/shared/lib/authApi.ts', import.meta.url), 'utf8');
const authProvider = await readFile(new URL('../src/features/auth/AuthProvider.tsx', import.meta.url), 'utf8');
const loginForm = await readFile(new URL('../src/features/auth/LoginForm.tsx', import.meta.url), 'utf8');
const viteConfig = await readFile(new URL('../vite.config.ts', import.meta.url), 'utf8');
const tsConfig = JSON.parse(await readFile(new URL('../tsconfig.app.json', import.meta.url), 'utf8'));

test('frontend dependencies are pinned to exact versions', () => {
    for (const [name, version] of Object.entries({...packageJson.dependencies, ...packageJson.devDependencies})) {
        assert.match(version, /^\d+\.\d+\.\d+$/, `${name} must use an exact version`);
    }
});
test('TypeScript strict mode is enabled', () => {
    assert.equal(tsConfig.compilerOptions.strict, true);
    assert.equal(tsConfig.compilerOptions.noEmit, true);
});
test('frontend proxies API and health calls to the backend', () => {
    assert.match(viteConfig, /'\/api'/);
    assert.match(viteConfig, /'\/actuator'/);
    assert.match(viteConfig, /http:\/\/localhost:8080/);
});
test('authentication uses cookies, CSRF, single-flight refresh, and bounded retry', () => {
    assert.match(authApi, /credentials: 'include'/);
    assert.match(authApi, /X-CSRF-TOKEN/);
    assert.match(authApi, /refreshInFlight/);
    assert.match(authApi, /retry = true/);
    assert.doesNotMatch(authApi, /localStorage|sessionStorage|indexedDB/);
});
test('auth API preserves HTTP status for session-state decisions', () => {
    assert.match(authApi, /class AuthApiError extends Error/);
    assert.match(authApi, /new AuthApiError\([^\n]*response\.status/);
    assert.match(authProvider, /cause instanceof AuthApiError && cause\.status === 401/);
    assert.match(authProvider, /setState\('error'\)/);
});
test('failed logout is surfaced without claiming local logout succeeded', () => {
    assert.match(authProvider, /登出失敗，無法確認伺服器 Session 已撤銷/);
    assert.match(authProvider, /await logoutRequest\(\);[\s\S]*setUser\(null\);[\s\S]*catch \(cause\)/);
    assert.match(authProvider, /throw cause/);
    assert.match(loginForm, /submitLogout[\s\S]*catch \(cause\)/);
});
test('auth provider exposes loading, authenticated, and anonymous states', () => {
    assert.match(authProvider, /'loading' \| 'authenticated' \| 'anonymous' \| 'error'/);
});
test('Phase 3 app exposes login without implementing business workflows', () => {
    assert.match(appTs, /PHASE 3/);
    assert.match(appTs, /LoginForm/);
    assert.doesNotMatch(appTs, /coupon claim|campaign create|publish campaign/i);
});
