import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const packageJson = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
const appTs = await readFile(new URL('../src/app/App.tsx', import.meta.url), 'utf8');
const authApi = await readFile(new URL('../src/shared/lib/authApi.ts', import.meta.url), 'utf8');
const authProvider = await readFile(new URL('../src/features/auth/AuthProvider.tsx', import.meta.url), 'utf8');
const loginForm = await readFile(new URL('../src/features/auth/LoginForm.tsx', import.meta.url), 'utf8');
const viteConfig = await readFile(new URL('../vite.config.ts', import.meta.url), 'utf8');
const audienceAdmin = await readFile(new URL('../src/features/audience/AudienceAdmin.tsx', import.meta.url), 'utf8');
const audienceApi = await readFile(new URL('../src/shared/lib/audienceApi.ts', import.meta.url), 'utf8');
const campaignApi = await readFile(new URL('../src/shared/lib/campaignApi.ts', import.meta.url), 'utf8');
const campaignAdmin = await readFile(new URL('../src/features/campaign/CampaignAdmin.tsx', import.meta.url), 'utf8');
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
test('Phase 7 Admin workspace remains available alongside Phase 8 public and Member routes', () => {
    assert.match(appTs, /PHASE 7 \+ (?:PHASE )?8(?: \+ 9)?/);
    assert.match(appTs, /LoginForm/);
    assert.match(appTs, /user\?\.role === 'ADMIN'/);
    assert.match(appTs, /user\.role === 'ADMIN' \? '\/admin'/);
    assert.match(appTs, /route === '\/admin'/);
    assert.match(appTs, /route === '\/admin\/campaigns'/);
    assert.match(appTs, /CampaignAdmin/);
    assert.match(appTs, /CampaignLandingPage/);
    assert.match(appTs, /MemberInbox/);
    assert.match(appTs, /\/member\/messages/);
    assert.match(appTs, /safeCampaignReturn/);
    assert.match(appTs, /Publish/);
    assert.match(appTs, /Delivery Status/);
});
test('audience UI supports all eight canonical fields and the documented operator allowlist', () => {
    for (const field of ['age', 'gender', 'registeredAt', 'membershipTier', 'orderCount', 'totalSpending', 'lastOrderAt', 'lastLoginAt']) assert.match(audienceAdmin, new RegExp(`value:\\s*'${field}'`));
    for (const op of ['=', '!=', '>', '>=', '<', '<=', 'IN', 'NOT IN']) assert.ok(audienceAdmin.includes(`'${op}'`));
    assert.match(audienceAdmin, /conditions:\s*rules\.conditions\.map\(parseCondition\)/);
    assert.match(audienceAdmin, /預覽受眾/);
});
test('audience API client uses the documented reusable segment endpoints', () => {
    for (const path of ['/api/v1/admin/audience-segments', '/preview']) assert.ok(audienceApi.includes(path));
    assert.match(audienceApi, /listAudienceSegments/);
    assert.match(audienceApi, /updateAudienceSegment/);
});


test('campaign API client uses Phase 5 CRUD and persisted preview paths', () => {
    for (const path of ['/api/v1/admin/campaigns', 'audience-preview']) assert.ok(campaignApi.includes(path));
    assert.match(campaignApi, /createCampaign/);
    assert.match(campaignApi, /updateCampaign/);
    assert.match(campaignApi, /status/);
});
test('campaign Admin supports draft editing, coupon configuration, and Phase 7 lifecycle controls', () => {
    assert.match(campaignAdmin, /儲存 Draft/);
    assert.match(campaignAdmin, /previewCampaignAudience\(selected\.id\)/);
    assert.match(campaignAdmin, /segmentId/);
    assert.match(campaignAdmin, /campaign-specific rules/);
    assert.match(campaignAdmin, /selected\.status\s*!==\s*'DRAFT'/);
    assert.match(campaignAdmin, /CampaignCoupons/);
    assert.match(campaignAdmin, /CampaignLifecycle/);
    assert.match(campaignAdmin, /selected &&/);
});

const campaignLifecycle = await readFile(new URL('../src/features/campaign/CampaignLifecycle.tsx', import.meta.url), 'utf8');
test('Phase 7 UI exposes message configuration, publish/pause, delivery status, and failed-only retry', () => {
    for (const text of [/儲存\s+Message/, /Publish Campaign/, /Pause Campaign/, /Message Delivery/, /Retry Failed\s+Delivery/]) assert.match(campaignLifecycle, text);
    assert.match(campaignLifecycle, /delivery\.retryable/);
    assert.match(campaignLifecycle, /campaignStatus === 'DRAFT'/);
});
test('Phase 7 API client uses only documented lifecycle endpoints', () => {
    for (const path of ['/message', '/publish', '/pause', '/delivery-status', '/delivery/retry']) assert.ok(campaignApi.includes(path));
    assert.match(campaignApi, /getDeliveryStatus/);
    assert.match(campaignApi, /retryDelivery/);
});

const landingPage = await readFile(new URL('../src/features/engagement/CampaignLandingPage.tsx', import.meta.url), 'utf8');
const memberInbox = await readFile(new URL('../src/features/engagement/MemberInbox.tsx', import.meta.url), 'utf8');
const engagementApi = await readFile(new URL('../src/shared/lib/engagementApi.ts', import.meta.url), 'utf8');
test('Phase 8 public landing page uses server claim state and the existing idempotent claim API', () => {
    assert.match(landingPage, /getPublicCampaign/);
    assert.match(landingPage, /getPublicCoupons/);
    assert.match(landingPage, /claimKeys/);
    assert.match(landingPage, /sessionStorage/);
    assert.match(landingPage, /claimCampaignCoupon/);
    assert.match(landingPage, /returnTo/);
    assert.match(engagementApi, /\/api\/v1\/campaigns\/\$\{campaignId\}\/coupons\/\$\{couponId\}\/claims/);
});
test('Phase 8 Member inbox records visible impressions and deliberate clicks', () => {
    assert.match(memberInbox, /IntersectionObserver/);
    assert.match(memberInbox, /intersectionRatio >= 0\.5/);
    assert.match(memberInbox, /'IMPRESSION'/);
    assert.match(memberInbox, /'CLICK'/);
});
