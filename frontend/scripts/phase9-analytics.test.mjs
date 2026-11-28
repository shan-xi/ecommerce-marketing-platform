import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');

test('Phase 9 Admin analytics route is wired and role-gated', () => {
    const app = read('../src/app/App.tsx');
    assert.match(app, /analyticsRoute/);
    assert.match(app, /user\?\.role === 'ADMIN' && <CampaignAnalyticsPage/);
    assert.match(app, /CampaignAnalyticsPage/);
});

test('Campaign analytics UI distinguishes totals, unique members, and numerator/denominator rates', () => {
    const page = read('../src/features/analytics/CampaignAnalyticsPage.tsx');
    for (const label of ['Impression Count', 'Unique Reach', 'Click Count', 'Unique Clickers', 'Landing Page Views', 'Unique Landing Visitors', 'Successful Coupon Claims', 'Unique Claimers', 'Claim Attempts']) {
        assert.ok(page.includes(label), `missing ${label}`);
    }
    assert.match(page, /rate\.numerator/);
    assert.match(page, /rate\.denominator/);
    assert.match(page, /不可用（分母為 0）/);
    assert.match(page, /此活動目前尚無送達、互動或領券紀錄/);
    assert.doesNotMatch(page, /Click[- ]to[- ]Landing Conversion/);
});

test('Analytics API client uses the approved single Admin endpoint', () => {
    const api = read('../src/shared/lib/analyticsApi.ts');
    assert.match(api, /\/api\/v1\/admin\/campaigns\/\$\{campaignId\}\/analytics/);
    assert.doesNotMatch(api, /coupons\/analytics/);
});

test('Campaign management exposes analytics navigation for published campaigns', () => {
    const admin = read('../src/features/campaign/CampaignAdmin.tsx');
    assert.match(admin, /selected\.status !== 'DRAFT'/);
    assert.match(admin, /\/admin\/campaigns\/\$\{selected\.id\}\/analytics/);
});


test('Phase 9 API verification loads the CSRF helper before shared auth helpers', () => {
    const suite = read('../../scripts/api-tests/suites/phase9-analytics-verification.zsh');
    assert.match(suite, /source \"\$API_TEST_HOME\/lib\/csrf\.zsh\"\s+source \"\$API_TEST_HOME\/lib\/auth\.zsh\"/);
});


test('member campaign click is awaited before navigation and guarded against duplicate clicks', async () => {
    const inbox = read('../src/features/engagement/MemberInbox.tsx');
    assert.match(inbox, /async function openCampaign\(\)/);
    assert.match(inbox, /await recordCampaignEvent\(message\.campaignId, 'CLICK', message\.id\)/);
    assert.match(inbox, /finally\s*\{\s*window\.location\.assign/);
    assert.match(inbox, /if \(opening\) return/);
    assert.match(inbox, /disabled=\{opening\}/);
});

test('impression and landing page views remain repeatable event counts', async () => {
    const inbox = read('../src/features/engagement/MemberInbox.tsx');
    const landing = read('../src/features/engagement/CampaignLandingPage.tsx');
    assert.match(inbox, /recordCampaignEvent\(message\.campaignId, 'IMPRESSION', message\.id\)/);
    assert.match(landing, /recordCampaignEvent\(campaignId, 'LANDING_PAGE_VIEW'\)/);
    assert.match(inbox, /const impressionSent = useRef\(false\)/);
    assert.match(landing, /const viewRecorded = useRef<string \| null>\(null\)/);
});


test('stale campaign analytics requests cannot overwrite the latest request', () => {
    const page = read('../src/features/analytics/CampaignAnalyticsPage.tsx');
    assert.match(page, /useRef/);
    assert.match(page, /const requestVersion = useRef\(0\)/);
    assert.match(page, /const currentRequest = \+\+requestVersion\.current/);
    assert.match(page, /if \(currentRequest === requestVersion\.current\) \{\s*setReport\(nextReport\)/);
    assert.match(page, /if \(currentRequest === requestVersion\.current\) \{\s*setReport\(null\);\s*setError/);
    assert.match(page, /if \(currentRequest === requestVersion\.current\) \{\s*setLoading\(false\)/);
    assert.match(page, /return \(\) => \{\s*\/\/ Invalidate the request[\s\S]*?requestVersion\.current \+= 1/);
});


test('analytics page never renders a report belonging to a previous campaign during the render before effects', () => {
    const page = read('../src/features/analytics/CampaignAnalyticsPage.tsx');
    assert.match(page, /const visibleReport = report\?\.campaignId === campaignId \? report : null/);
    assert.match(page, /!loading && visibleReport && <>/);
    assert.doesNotMatch(page, /!loading && report && <>/);
});
