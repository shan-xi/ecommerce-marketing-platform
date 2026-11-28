export type Route = {
    path: string;
    name: string;
    portal: 'admin' | 'member' | 'public' | 'auth';
    role?: 'ADMIN' | 'MEMBER'
};
export const routes: Route[] = [
    {path: '/admin/login', name: 'Admin Login', portal: 'auth', role: 'ADMIN'},
    {path: '/member/login', name: 'Member Login', portal: 'auth', role: 'MEMBER'},
    {path: '/admin', name: 'Overview', portal: 'admin', role: 'ADMIN'},
    {path: '/admin/audience', name: 'Audience Segments', portal: 'admin', role: 'ADMIN'},
    {path: '/admin/campaigns', name: 'Campaigns', portal: 'admin', role: 'ADMIN'},
    {path: '/member', name: 'Member Overview', portal: 'member', role: 'MEMBER'},
    {path: '/member/profile', name: 'Profile', portal: 'member', role: 'MEMBER'},
    {path: '/member/messages', name: 'Messages', portal: 'member', role: 'MEMBER'},
    {path: '/member/coupons', name: 'Coupons', portal: 'member', role: 'MEMBER'},
    {path: '/', name: 'Storefront', portal: 'public'}
];

export function navigate(path: string, replace = false) {
    if (!path.startsWith('/') || path.startsWith('//') || path.includes('\\')) return;
    if (replace) history.replaceState(null, '', path); else history.pushState(null, '', path);
    window.dispatchEvent(new PopStateEvent('popstate'));
}

export function safeReturnTo(value: string | null, role?: 'ADMIN' | 'MEMBER') {
    if (!value || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return null;
    if (/^\/campaigns\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)) return value;
    const memberRoutes = ['/member', '/member/profile', '/member/messages', '/member/coupons'];
    const adminRoutes = ['/admin', '/admin/audience', '/admin/campaigns'];
    const analytics = /^\/admin\/campaigns\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\/analytics$/i.test(value);
    if (role === 'ADMIN') return adminRoutes.includes(value) || analytics ? value : null;
    if (role === 'MEMBER') return memberRoutes.includes(value) ? value : null;
    return memberRoutes.includes(value) || adminRoutes.includes(value) || analytics ? value : null;
}

export function matchRoute(path: string) {
    const analytics = path.match(/^\/admin\/campaigns\/([0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\/analytics$/i);
    const campaign = path.match(/^\/campaigns\/([0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})$/i);
    return {analytics, campaign, route: routes.find(r => r.path === path)};
}
