import {type ReactNode} from 'react';
import {useAuth} from '../features/auth/AuthProvider';
import {navigate} from '../app/router';

export function MemberLayout({children}: { children: ReactNode }) {
    const {user, signOut} = useAuth();
    return <div className="storefront">
        <header className="store-header"><a className="store-brand" href="/" onClick={e => {
            e.preventDefault();
            navigate('/')
        }}><span className="brand-mark">M</span> MarketFlow</a>
            <nav><a href="/member/messages">會員訊息</a><a href="/member/coupons">我的優惠券</a>{user &&
                <a href="/member/profile">會員資料</a>}</nav>
            <div className="store-account">{user ? <><span>Hi, {user.userId.slice(0, 8)}</span>
                <button onClick={() => void signOut().then(() => navigate('/')).catch(() => {
                })}>登出
                </button>
            </> : <a className="button-link" href="/member/login">會員登入</a>}</div>
        </header>
        <main className="store-content">{children}</main>
        <footer className="store-footer"><span>MarketFlow · Campaign & Coupon</span><a href="/admin/login">Admin</a>
        </footer>
    </div>
}
