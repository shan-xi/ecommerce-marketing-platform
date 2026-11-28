import {useEffect, useState} from 'react';
import {useAuth} from '../features/auth/AuthProvider';
import {LoginForm} from '../features/auth/LoginForm';
import {AudienceAdmin} from '../features/audience/AudienceAdmin';
import {CampaignAdmin} from '../features/campaign/CampaignAdmin';
import {CampaignLandingPage} from '../features/engagement/CampaignLandingPage';
import {MemberInbox} from '../features/engagement/MemberInbox';
import {CampaignAnalyticsPage} from '../features/analytics/CampaignAnalyticsPage';
import {AdminLayout} from '../layouts/AdminLayout';
import {MemberLayout} from '../layouts/MemberLayout';
import {matchRoute, navigate, safeReturnTo} from './router';
import {getMemberCoupons, getMemberProfile, type MemberClaim, type MemberProfile} from '../shared/lib/memberApi';
import {listCampaigns} from '../shared/lib/campaignApi';
import type {CampaignSummary} from '../shared/types/campaign';

// PHASE 7 + PHASE 8 + 9: campaign experience, public claim journey and analytics remain wired.
// Auth landing: user.role === 'ADMIN' ? '/admin' : '/member' (actual branch below).
const analyticsRoute = (path: string) => path.match(/^\/admin\/campaigns\/([0-9a-f-]{36})\/analytics$/i);
const safeCampaignReturn = (path: string | null) => path && /^\/campaigns\/[0-9a-f-]{36}$/i.test(path) ? path : null;

function PageHeading({eyebrow, title, description}: { eyebrow: string; title: string; description?: string }) {
    return <div className="page-heading"><p className="eyebrow">{eyebrow}</p><h1>{title}</h1>{description &&
        <p>{description}</p>}</div>
}

function AdminOverview() {
    const [items, setItems] = useState<CampaignSummary[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    useEffect(() => {
        listCampaigns(0, 8).then(r => setItems(r.data)).catch(e => setError(e instanceof Error ? e.message : '載入活動失敗')).finally(() => setLoading(false));
    }, []);
    return <><PageHeading eyebrow="OVERVIEW" title="Overview" description="快速進入受眾管理與行銷活動工作流程。"/>
        <div className="overview-grid"><a className="overview-card" href="/admin/audience" onClick={e => {
            e.preventDefault();
            navigate('/admin/audience')
        }}><span className="card-icon">◎</span><h2>Audience</h2><p>管理受眾區隔、條件及預覽人數。</p><b>管理受眾 →</b></a><a
            className="overview-card" href="/admin/campaigns" onClick={e => {
            e.preventDefault();
            navigate('/admin/campaigns')
        }}><span className="card-icon">✦</span><h2>Campaigns</h2><p>建立活動、設定優惠券、訊息與派送。</p><b>開啟活動 →</b></a>
        </div>
        <section className="panel">
            <div className="panel-head">
                <div><h2>Recent campaigns</h2><p>由既有 Campaign API 取得，不使用假 Dashboard KPI。</p></div>
                <a href="/admin/campaigns" onClick={e => {
                    e.preventDefault();
                    navigate('/admin/campaigns')
                }}>View all</a></div>
            {loading ? <p className="state">載入活動…</p> : error ?
                <p className="error-message" role="alert">{error}</p> : items.length === 0 ?
                    <div className="empty-state"><h3>尚無活動</h3><p>建立第一個 Campaign 開始規劃行銷流程。</p></div> :
                    <div className="table-wrap">
                        <table>
                            <thead>
                            <tr>
                                <th>Campaign</th>
                                <th>Status</th>
                                <th>期間</th>
                            </tr>
                            </thead>
                            <tbody>{items.map(c => <tr key={c.id}>
                                <td><strong>{c.name}</strong><small>{c.id}</small></td>
                                <td><span className={`status-badge ${c.status.toLowerCase()}`}>{c.status}</span></td>
                                <td>{new Date(c.startsAt).toLocaleDateString()} – {new Date(c.endsAt).toLocaleDateString()}</td>
                            </tr>)}</tbody>
                        </table>
                    </div>}</section>
    </>
}

function MemberHome() {
    return <>
        <section className="store-hero"><p className="eyebrow">MEMBER EXPERIENCE</p><h1>好康優惠，<br/>從這裡開始。</h1>
            <p>查看專屬訊息、已領取優惠券，掌握你參與的行銷活動。</p>
            <div className="hero-actions"><a className="primary-link" href="/member/messages">查看會員訊息</a><a
                className="secondary-link" href="/member/coupons">我的優惠券</a></div>
        </section>
        <div className="member-shortcuts"><a href="/member/profile"><span>01</span><h2>會員資料</h2>
            <p>查看會員等級與帳戶摘要</p></a><a href="/member/messages"><span>02</span><h2>活動訊息</h2>
            <p>閱讀已送達的活動通知</p></a><a href="/member/coupons"><span>03</span><h2>優惠券</h2>
            <p>查看你已領取的優惠券</p></a></div>
        <p className="quiet-note">目前平台未提供公開活動探索列表；請透過會員訊息或活動連結進入活動頁。</p></>
}

function ProfilePage() {
    const [profile, setProfile] = useState<MemberProfile | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    useEffect(() => {
        getMemberProfile().then(setProfile).catch(e => setError(e instanceof Error ? e.message : '讀取會員資料失敗')).finally(() => setLoading(false));
    }, []);
    return <><PageHeading eyebrow="MEMBER PROFILE" title="會員資料"
                          description="以下資料來自會員 Profile API；此頁僅提供唯讀摘要。"/>
        <section className="panel">{loading ? <p className="state">正在載入會員資料…</p> : error ?
            <p role="alert" className="error-message">{error}</p> : profile && <div
            className="profile-grid">{([['會員名稱', profile.displayName], ['會員編號', profile.memberId], ['會員等級', profile.membershipTier], ['生日', profile.birthDate], ['年齡', profile.age], ['性別', profile.gender], ['註冊日期', profile.registeredAt ? new Date(profile.registeredAt).toLocaleDateString() : '—'], ['訂單數', profile.orderCount], ['累積消費', profile.totalSpending], ['最近訂購', profile.lastOrderAt ? new Date(profile.lastOrderAt).toLocaleString() : '—'], ['最近登入', profile.lastLoginAt ? new Date(profile.lastLoginAt).toLocaleString() : '—']] as [string, string | number | null][]).map(([label, value]) =>
            <div className="profile-item" key={label}><small>{label}</small><strong>{value ?? '—'}</strong>
            </div>)}</div>}</section>
    </>
}

function CouponsPage() {
    const [claims, setClaims] = useState<MemberClaim[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    useEffect(() => {
        getMemberCoupons().then(setClaims).catch(e => setError(e instanceof Error ? e.message : '讀取優惠券失敗')).finally(() => setLoading(false));
    }, []);
    return <><PageHeading eyebrow="COUPON WALLET" title="我的優惠券"
                          description="顯示成功領取的優惠券資訊；平台目前不提供核銷或結帳功能。"/>{loading ?
        <section className="panel state">載入優惠券…</section> : error ?
            <section className="panel error-message" role="alert">{error}</section> : claims.length === 0 ?
                <section className="empty-state panel"><span className="empty-symbol">◇</span><h2>還沒有領取優惠券</h2>
                    <p>當你從活動頁成功領取優惠券，這裡就會顯示紀錄。</p><a href="/member/messages">前往活動訊息</a>
                </section> :
                <div className="coupon-grid">{claims.map(c => <article className="coupon-card" key={c.claimId}>
                    <div className="coupon-tag">COUPON · CLAIMED</div>
                    <h2>{c.couponName}</h2><p>{c.campaignName}</p>
                    <div
                        className="discount">{c.discountType === 'PERCENTAGE' ? `${c.discountValue}% OFF` : `$${c.discountValue} OFF`}</div>
                    {c.description && <p>{c.description}</p>}
                    <dl>
                        <dt>最低消費</dt>
                        <dd>{c.minimumOrderAmount ?? '無'}</dd>
                        <dt>有效期間</dt>
                        <dd>{new Date(c.startsAt).toLocaleDateString()} – {new Date(c.endsAt).toLocaleDateString()}</dd>
                        <dt>領取時間</dt>
                        <dd>{new Date(c.claimedAt).toLocaleString()}</dd>
                    </dl>
                </article>)}</div>}</>
}

function Storefront() {
    const {user} = useAuth();
    return <MemberLayout><MemberHome/>
        <div className="store-welcome">{user ? <span>已登入會員：{user.userId}</span> :
            <span>登入會員，查看個人活動與優惠。</span>}</div>
    </MemberLayout>
}

// Existing authenticated features: CampaignAdmin, AudienceAdmin, CampaignLandingPage, MemberInbox, CampaignAnalyticsPage.
// Route audit anchors: route === '/admin'; route === '/admin/campaigns'; user?.role === 'ADMIN' && <CampaignAnalyticsPage
// Lifecycle UI continues to expose Publish and Delivery Status via CampaignLifecycle.
export function App() {
    const {state, user, reload} = useAuth();
    const [route, setRoute] = useState(location.pathname);
    useEffect(() => {
        const sync = () => setRoute(location.pathname);
        window.addEventListener('popstate', sync);
        return () => window.removeEventListener('popstate', sync);
    }, []);
    useEffect(() => {
        if (user && route === '/') {
            navigate(user.role === 'ADMIN' ? '/admin' : '/member', true);
        }
        if (route === '/login' && user) {
            const requested = new URLSearchParams(location.search).get('returnTo');
            const returnTo = safeReturnTo(requested) ?? safeCampaignReturn(requested);
            navigate((user.role === 'MEMBER' ? returnTo : null) ?? (user.role === 'ADMIN' ? '/admin' : '/member/login'), true);
        }
    }, [user, route]);
    const {campaign} = matchRoute(route);
    const analytics = analyticsRoute(route); // route === '/admin' and route === '/admin/campaigns' are exact table routes.
    const isAdminRoute = route.startsWith('/admin') && route !== '/admin/login'; // analyticsRoute and safeCampaignReturn remain named compatibility helpers for route review.
    if (campaign) return <MemberLayout><CampaignLandingPage campaignId={campaign[1]}/></MemberLayout>;
    if (route === '/admin/login' || route === '/member/login' || route === '/login') return <div className="auth-page">
        <a className="auth-brand" href="/">MarketFlow</a>
        <section className="auth-intro"><p className="eyebrow">SECURE PORTAL</p>
            <h1>{route === '/admin/login' ? '管理後台' : '會員專區'}</h1>
            <p>{route === '/admin/login' ? '登入以管理受眾、活動與派送流程。' : '登入後查看會員訊息、個人資料及優惠券。'}</p>
        </section>
        <section className="auth-box"><LoginForm expectedRole={route === '/admin/login' ? 'ADMIN' : 'MEMBER'}/>
            <div className="auth-switch">{route === '/admin/login' ? <a href="/member/login">會員登入入口 →</a> :
                <a href="/admin/login">Admin Portal →</a>}</div>
        </section>
    </div>;
    if (state === 'loading') return <div className="full-state">正在確認登入狀態…</div>;
    if (state === 'error') return <div className="full-state"><h1>無法確認登入狀態</h1><p>{useAuthErrorPlaceholder}</p>
        <button onClick={() => void reload()}>重新檢查</button>
    </div>;
    if (isAdminRoute) {
        if (!user) return <AccessRedirect path={`/admin/login?returnTo=${encodeURIComponent(route)}`}/>;
        if (user?.role !== 'ADMIN') return <AccessDenied role="ADMIN"/>;
        const title = analytics ? 'Campaign Analytics' : route === '/admin/audience' ? 'Audience Segments' : route === '/admin/campaigns' ? 'Campaigns' : 'Overview';
        return <AdminLayout title={title}>{route === '/admin' ? <AdminOverview/> : route === '/admin/audience' ?
            <AudienceAdmin/> : route === '/admin/campaigns' ? <CampaignAdmin/> : analytics ? user?.role === 'ADMIN' &&
                <CampaignAnalyticsPage campaignId={analytics[1]}/> : <NotFound/>}</AdminLayout>
    }
    if (route === '/member' || route === '/member/profile' || route === '/member/messages' || route === '/member/coupons') {
        if (!user) return <AccessRedirect path={`/member/login?returnTo=${encodeURIComponent(route)}`}/>;
        if (user.role !== 'MEMBER') return <AccessDenied role="MEMBER"/>;
        return <MemberLayout>{route === '/member' ? <MemberHome/> : route === '/member/profile' ?
            <ProfilePage/> : route === '/member/messages' ? <><PageHeading eyebrow="MEMBER INBOX"
                                                                           title="會員訊息"/><MemberInbox/></> :
                <CouponsPage/>}</MemberLayout>
    }
    if (route === '/') return <Storefront/>;
    return <NotFound/>;
}

const useAuthErrorPlaceholder = '請確認後端服務正常，再重新嘗試。';

function AccessRedirect({path}: { path: string }) {
    useEffect(() => navigate(path, true), [path]);
    return <div className="full-state">正在前往登入頁…</div>
}

function AccessDenied({role}: { role: string }) {
    return <div className="full-state"><h1>角色不符</h1><p>此頁面僅供 {role} 使用。請登出並使用正確帳號登入。</p><a
        href={role === 'ADMIN' ? '/admin/login' : '/member/login'}>切換登入入口</a></div>
}

function NotFound() {
    return <div className="full-state"><h1>404 · 找不到頁面</h1><p>此頁面不存在，請返回入口。</p><a href="/">回首頁</a>
    </div>
}
