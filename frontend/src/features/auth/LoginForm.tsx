import {type FormEvent, useState} from 'react';
import {useAuth} from './AuthProvider';
import {navigate, safeReturnTo} from '../../app/router';

export function LoginForm({expectedRole = 'MEMBER'}: { expectedRole?: 'ADMIN' | 'MEMBER' }) {
    const {state, user, error, signIn, signOut} = useAuth();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [formError, setFormError] = useState<string | null>(null);
    const [logoutSubmitting, setLogoutSubmitting] = useState(false);
    const [logoutError, setLogoutError] = useState<string | null>(null);

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setSubmitting(true);
        setFormError(null);
        try {
            const signedIn = await signIn(email, password);
            if (signedIn.role !== expectedRole) {
                setFormError(`此登入入口僅接受 ${expectedRole} 角色，實際帳號為 ${signedIn.role}。請先登出，再使用正確帳號登入。`);
                return;
            }
            const returnTo = safeReturnTo(new URLSearchParams(window.location.search).get('returnTo'), expectedRole);
            navigate(returnTo ?? (expectedRole === 'ADMIN' ? '/admin' : '/member'), true);
        } catch (cause) {
            setFormError(cause instanceof Error ? cause.message : '登入失敗');
        } finally {
            setSubmitting(false);
        }
    }

    async function submitLogout() {
        setLogoutSubmitting(true);
        setLogoutError(null);
        try {
            await signOut();
        } catch (cause) {
            setLogoutError(cause instanceof Error ? cause.message : '登出失敗，請稍後重試。');
        } finally {
            setLogoutSubmitting(false);
        }
    }

    if (state === 'loading') return <section className="auth-card"><p>正在確認登入狀態…</p></section>;
    if (user && user.role !== expectedRole) return <section className="auth-card"><p className="card-label">角色不符</p>
        <h2>{user.role}</h2><p role="alert"
                               className="error-message">此登入入口僅接受 {expectedRole}，目前帳號為 {user.role}。</p><p
            className="status-detail">{user.userId}</p>
        <button type="button" onClick={() => void submitLogout()}
                disabled={logoutSubmitting}>{logoutSubmitting ? '登出中…' : '登出並切換帳號'}</button>
        {(logoutError ?? error) && <p className="error-message">{logoutError ?? error}</p>}</section>;
    if (user) return <section className="auth-card">
        <p className="card-label">已登入</p>
        <h2>{user.role}</h2><p className="status-detail">{user.userId}</p>
        <button type="button" onClick={() => void submitLogout()} disabled={logoutSubmitting}>
            {logoutSubmitting ? '登出中…' : '登出'}
        </button>
        {(logoutError ?? error) && <p className="error-message">{logoutError ?? error}</p>}
    </section>;

    return <section className="auth-card">
        <p className="card-label">SECURE ACCESS</p>
        <h2>{expectedRole === 'ADMIN' ? '管理員登入' : '會員登入'}</h2>
        <form onSubmit={submit}>
            <label htmlFor="email">Email</label>
            <input id="email" type="email" autoComplete="username" value={email}
                   onChange={(event) => setEmail(event.target.value)} required/>
            <label htmlFor="password">密碼</label>
            <input id="password" type="password" autoComplete="current-password" value={password}
                   onChange={(event) => setPassword(event.target.value)} required/>
            <button type="submit" disabled={submitting}>{submitting ? '登入中…' : '登入'}</button>
        </form>
        {(formError ?? error) && <p className="error-message">{formError ?? error}</p>}
        <p className="status-detail">登入憑證透過 HttpOnly Cookie 管理；JWT 不會存入瀏覽器儲存空間。</p>
    </section>;
}
