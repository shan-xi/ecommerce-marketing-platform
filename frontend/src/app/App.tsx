import {useEffect, useState} from 'react';
import {fetchBackendHealth, type HealthStatus} from '../shared/lib/healthApi';
import {LoginForm} from '../features/auth/LoginForm';
import {useAuth} from '../features/auth/AuthProvider';
import {AudienceAdmin} from '../features/audience/AudienceAdmin';
import {CampaignAdmin} from '../features/campaign/CampaignAdmin';

type ConnectionState = 'checking' | 'connected' | 'unavailable';

export function App() {
    const {user} = useAuth();
    const [route, setRoute] = useState(window.location.pathname);
    useEffect(() => {
        if (user?.role === 'ADMIN' && route === '/') {
            const destination = '/admin';
            window.history.replaceState(null, '', destination);
            setRoute(destination);
        }
    }, [user, route]);
    useEffect(() => {
        const syncRoute = () => setRoute(window.location.pathname);
        window.addEventListener('popstate', syncRoute);
        return () => window.removeEventListener('popstate', syncRoute);
    }, []);
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

    return <main className="page-shell">
        <section className="hero" aria-labelledby="page-title">
            <p className="eyebrow">PHASE 6 · COUPON CONFIGURATION & CLAIM BACKEND</p>
            <h1 id="page-title">行銷活動與優惠券平台</h1>
            <p className="subtitle">E-commerce Campaign &amp; Coupon Engagement Platform</p>
            <p className="intro">Phase 6 在 Campaign 工作區加入 Coupon
                建立、列表與編輯；後端提供安全的優惠券領取、冪等處理與領取紀錄。</p>
        </section>
        <section className="status-card" aria-live="polite">
            <div>
                <p className="card-label">Backend connection</p>
                <h2>{connectionState === 'checking' ? '正在檢查後端連線…'
                    : connectionState === 'connected' ? '後端連線正常' : '目前無法連線至後端'}</h2>
                <p className="status-detail">{connectionState === 'connected' ? `Health status: ${healthStatus}`
                    : connectionState === 'checking' ? 'GET /actuator/health' : (errorMessage ?? `Health status: ${healthStatus}`)}</p>
            </div>
            <span className={`status-indicator status-${connectionState}`} aria-hidden="true"/>
        </section>
        <LoginForm/>
        {user?.role === 'ADMIN' &&
            <nav className="route-nav" aria-label="主要功能"><a href="/admin">Audience Segments</a><a
                href="/admin/campaigns">Campaigns</a></nav>}
        {(route === '/admin' || route === '/admin/audience') && user?.role === 'ADMIN' && <AudienceAdmin/>}
        {route === '/admin/campaigns' && user?.role === 'ADMIN' && <CampaignAdmin/>}
        {user && route === '/admin' && user.role !== 'ADMIN' && <p className="error-message">此頁面僅供管理員使用。</p>}
        <footer className="footer">
            <span>Development baseline: v1.5.0</span>
            <span>Backend · Spring Boot / Frontend · React + TypeScript</span>
        </footer>
    </main>;
}
