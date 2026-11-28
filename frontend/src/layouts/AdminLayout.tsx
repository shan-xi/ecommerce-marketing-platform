import {type ReactNode, useState} from 'react';
import {useAuth} from '../features/auth/AuthProvider';
import {navigate} from '../app/router';

export function AdminLayout({children, title}: { children: ReactNode; title: string }) {
    const {user, signOut} = useAuth();
    const [drawer, setDrawer] = useState(false);
    const links = [['/admin', 'Overview'], ['/admin/audience', 'Audience'], ['/admin/campaigns', 'Campaigns']];
    return <div className="admin-portal">
        <aside className={`admin-sidebar ${drawer ? 'is-open' : ''}`}>
            <div className="brand-lockup"><span className="brand-mark">M</span>
                <div><strong>MarketFlow</strong><small>Marketing Platform</small></div>
            </div>
            <p className="nav-label">WORKSPACE</p>
            <nav>{links.map(([url, label]) => <a key={url} href={url}
                                                 className={location.pathname === url ? 'active' : ''} onClick={e => {
                e.preventDefault();
                navigate(url);
                setDrawer(false)
            }}><span className="nav-dot"/>{label}</a>)}</nav>
            <div className="sidebar-foot">Campaign operations<br/><small>v0.1 · Admin Portal</small></div>
        </aside>
        <div className="admin-main">
            <header className="admin-topbar">
                <button className="mobile-menu" onClick={() => setDrawer(!drawer)} aria-label="開啟導覽">☰</button>
                <div className="crumb">Workspace <span>/</span> {title}</div>
                <div className="user-menu"><span className="avatar">A</span>
                    <div><b>{user?.role}</b><small>{user?.userId}</small></div>
                    <button className="text-button"
                            onClick={() => void signOut().then(() => navigate('/')).catch(() => {
                            })}>登出
                    </button>
                </div>
            </header>
            <main className="admin-content">{children}</main>
        </div>
        {drawer && <button className="drawer-backdrop" aria-label="關閉導覽" onClick={() => setDrawer(false)}/>}</div>
}
