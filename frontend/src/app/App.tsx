import { useEffect, useState } from 'react';
import { fetchBackendHealth, type HealthStatus } from '../shared/lib/healthApi';

type ConnectionState = 'checking' | 'connected' | 'unavailable';

export function App() {
  const [connectionState, setConnectionState] = useState<ConnectionState>('checking');
  const [healthStatus, setHealthStatus] = useState<HealthStatus>('UNKNOWN');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    fetchBackendHealth()
      .then((health) => {
        if (!isMounted) return;
        setHealthStatus(health.status);
        setConnectionState(health.status === 'UP' ? 'connected' : 'unavailable');
      })
      .catch((error: unknown) => {
        if (!isMounted) return;
        setConnectionState('unavailable');
        setErrorMessage(error instanceof Error ? error.message : 'Unknown connection error');
      });

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <main className="page-shell">
      <section className="hero" aria-labelledby="page-title">
        <p className="eyebrow">PHASE 1 · REPOSITORY FOUNDATION</p>
        <h1 id="page-title">行銷活動與優惠券平台</h1>
        <p className="subtitle">
          E-commerce Campaign &amp; Coupon Engagement Platform
        </p>
        <p className="intro">
          專案基礎環境已建立。管理員、會員與活動頁面功能將依核准的實作階段逐步開發。
        </p>
      </section>

      <section className="status-card" aria-live="polite">
        <div>
          <p className="card-label">Backend connection</p>
          <h2>
            {connectionState === 'checking' && '正在檢查後端連線…'}
            {connectionState === 'connected' && '後端連線正常'}
            {connectionState === 'unavailable' && '目前無法連線至後端'}
          </h2>
          <p className="status-detail">
            {connectionState === 'connected' && `Health status: ${healthStatus}`}
            {connectionState === 'checking' && 'GET /actuator/health'}
            {connectionState === 'unavailable' && (errorMessage ?? `Health status: ${healthStatus}`)}
          </p>
        </div>
        <span className={`status-indicator status-${connectionState}`} aria-hidden="true" />
      </section>

      <footer className="footer">
        <span>Development baseline: v1.5.0</span>
        <span>Backend · Spring Boot / Frontend · React + TypeScript</span>
      </footer>
    </main>
  );
}
