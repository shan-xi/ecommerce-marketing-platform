import {useEffect, useState} from 'react';
import {fetchBackendHealth, type HealthStatus} from '../shared/lib/healthApi';
import {LoginForm} from '../features/auth/LoginForm';
import {useAuth} from '../features/auth/AuthProvider';
import {AudienceAdmin} from '../features/audience/AudienceAdmin';
import {CampaignAdmin} from '../features/campaign/CampaignAdmin';
import {CampaignLandingPage} from '../features/engagement/CampaignLandingPage';
import {MemberInbox} from '../features/engagement/MemberInbox';

type ConnectionState = 'checking' | 'connected' | 'unavailable';
const campaignRoute = (path: string) => path.match(/^\/campaigns\/([0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})$/i);

function safeCampaignReturn(path: string | null) {
    return path && /^\/campaigns\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(path) ? path : null;
}

export function App() {
    const {user} = useAuth();
    const [route, setRoute] = useState(window.location.pathname);
    useEffect(() => {
        const syncRoute = () => setRoute(window.location.pathname);
        window.addEventListener('popstate', syncRoute);
        return () => window.removeEventListener('popstate', syncRoute);
    }, []);
    useEffect(() => {
        if (!user) return;
        if (route === '/login') {
            const requested = safeCampaignReturn(new URLSearchParams(window.location.search).get('returnTo'));
            const destination = requested ?? (user.role === 'ADMIN' ? '/admin' : '/member/messages');
            window.history.replaceState(null, '', destination);
            setRoute(destination);
        } else if (route === '/' && user.role === 'ADMIN') {
            window.history.replaceState(null, '', '/admin');
            setRoute('/admin');
        } else if (route === '/' && user.role === 'MEMBER') {
            window.history.replaceState(null, '', '/member/messages');
            setRoute('/member/messages');
        }
    }, [user, route]);

    const [connectionState, setConnectionState] = useState<ConnectionState>('checking');
    const [healthStatus, setHealthStatus] = useState<HealthStatus>('UNKNOWN');
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    useEffect(() => {
        let mounted = true;
        fetchBackendHealth().then((health) => {
            if (!mounted) return;
            setHealthStatus(health.status);
            setConnectionState(health.status === 'UP' ? 'connected' : 'unavailable');
        }).catch((error: unknown) => {
            if (!mounted) return;
            setConnectionState('unavailable');
            setErrorMessage(error instanceof Error ? error.message : 'Unknown connection error');
        });
        return () => {
            mounted = false;
        };
    }, []);

    const campaign = campaignRoute(route);
    const isLogin = route === '/login';
    const isAdminRoute = route === '/admin' || route === '/admin/audience' || route === '/admin/campaigns';
    return <main className="page-shell">
        {!campaign && <section className="hero" aria-labelledby="page-title">
            <p className="eyebrow">PHASE 7 + PHASE 8 · MEMBER CAMPAIGN EXPERIENCE</p>
            <h1 id="page-title">行銷活動與優惠券平台</h1>
            <p className="subtitle">E-commerce Campaign &amp; Coupon Engagement Platform</p>
            <p className="intro">Phase 7 Publish、Pause 與 Delivery Status，加上 Phase 8
                公開活動頁面、會員優惠券領取與活動互動追蹤。</p>
        </section>}
        {(!campaign && !isLogin) && <section className="status-card" aria-live="polite">
            <div>
                <p className="card-label">Backend connection</p>
                <h2>{connectionState === 'checking' ? '正在檢查後端連線…' : connectionState === 'connected' ? '後端連線正常' : '目前無法連線至後端'}</h2>
                <p className="status-detail">{connectionState === 'connected' ? `Health status: ${healthStatus}` : connectionState === 'checking' ? 'GET /actuator/health' : (errorMessage ?? `Health status: ${healthStatus}`)}</p>
            </div>
            <span className={`status-indicator status-${connectionState}`} aria-hidden="true"/></section>}
        {isLogin &&
            <p className="notice-message">請使用會員帳號登入；登入後會返回原活動頁面，系統不會自動送出領取操作。</p>}
        {(isLogin || isAdminRoute || route === '/') && <LoginForm/>}
        {user?.role === 'ADMIN' &&
            <nav className="route-nav" aria-label="主要功能"><a href="/admin">Audience Segments</a><a
                href="/admin/campaigns">Campaigns</a></nav>}
        {user?.role === 'MEMBER' &&
            <nav className="route-nav" aria-label="會員功能"><a href="/member/messages">會員訊息匣</a></nav>}
        {(route === '/admin' || route === '/admin/audience') && user?.role === 'ADMIN' && <AudienceAdmin/>}
        {route === '/admin/campaigns' && user?.role === 'ADMIN' && <CampaignAdmin/>}
        {campaign && <CampaignLandingPage campaignId={campaign[1]}/>}
        {route === '/member/messages' && user?.role === 'MEMBER' && <MemberInbox/>}
        {route === '/member/messages' && user?.role !== 'MEMBER' &&
            <p className="error-message">此頁面僅供會員使用。</p>}
        {isAdminRoute && user && user.role !== 'ADMIN' && <p className="error-message">此頁面僅供管理員使用。</p>}
        {route === '/' && user?.role === 'MEMBER' &&
            <section className="experience-shell"><h2>會員專區</h2><p>前往會員訊息匣查看已送達的活動訊息。</p><a
                href="/member/messages">開啟會員訊息匣</a></section>}
        <footer className="footer"><span>Development baseline: v1.5.0 · Phase 8</span><span>Backend · Spring Boot / Frontend · React + TypeScript</span>
        </footer>
    </main>;
}
