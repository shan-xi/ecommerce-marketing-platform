import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const source = await readFile(new URL('../src/app/router.ts', import.meta.url), 'utf8');
const app = await readFile(new URL('../src/app/App.tsx', import.meta.url), 'utf8');
const login = await readFile(new URL('../src/features/auth/LoginForm.tsx', import.meta.url), 'utf8');
const memberApi = await readFile(new URL('../src/shared/lib/memberApi.ts', import.meta.url), 'utf8');
test('router centralizes portal route definitions and safe navigation', () => {
    for (const path of ['/admin/login', '/member/login', '/admin/audience', '/admin/campaigns', '/member/profile', '/member/messages', '/member/coupons']) assert.ok(source.includes(path));
    assert.match(source, /popstate/);
    assert.match(source, /startsWith\('\/\/'\)/);
    assert.match(source, /includes\('\\\\'\)/);
});
test('returnTo only permits routes appropriate to the selected role', () => {
    assert.match(source, /role\s*===\s*'ADMIN'/);
    assert.match(source, /role\s*===\s*'MEMBER'/);
    assert.match(source, /analytics/);
    assert.match(login, /safeReturnTo\([^,]+, expectedRole\)/);
});
test('member pages call only the documented profile and coupon endpoints', () => {
    assert.match(memberApi, /\/api\/v1\/member\/profile/);
    assert.match(memberApi, /\/api\/v1\/member\/coupons/);
    assert.match(app, /getMemberProfile/);
    assert.match(app, /getMemberCoupons/);
});
test('admin dashboard uses campaign list API and does not invent aggregate KPI', () => {
    assert.match(app, /listCampaigns\(0,\s*8\)/);
    assert.match(app, /不使用假 Dashboard KPI/);
});
