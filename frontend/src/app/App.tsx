import {useEffect, useState} from 'react';
import {fetchBackendHealth, type HealthStatus} from '../shared/lib/healthApi';
import {LoginForm} from '../features/auth/LoginForm';

type ConnectionState = 'checking' | 'connected' | 'unavailable';

export function App() {
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
            <p className="eyebrow">PHASE 3 · IDENTITY AND SECURITY</p>
            <h1 id="page-title">行銷活動與優惠券平台</h1>
            <p className="subtitle">E-commerce Campaign &amp; Coupon Engagement Platform</p>
            <p className="intro">Phase 3 建立登入、工作階段與存取控制基礎。管理員與會員的業務功能將依核准階段逐步開發。</p>
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
        <footer className="footer">
            <span>Development baseline: v1.5.0</span>
            <span>Backend · Spring Boot / Frontend · React + TypeScript</span>
        </footer>
    </main>;
}
