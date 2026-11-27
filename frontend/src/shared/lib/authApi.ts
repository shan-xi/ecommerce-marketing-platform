export type AuthUser = { userId: string; role: 'ADMIN' | 'MEMBER' };
export type LoginInput = { email: string; password: string };

export class AuthApiError extends Error {
    constructor(
        message: string,
        readonly status: number,
        readonly code?: string,
        readonly traceId?: string,
    ) {
        super(message);
        this.name = 'AuthApiError';
    }
}

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';
let refreshInFlight: Promise<boolean> | null = null;

function readCookie(name: string): string | null {
    const prefix = `${name}=`;
    const item = document.cookie.split('; ').find((part) => part.startsWith(prefix));
    return item ? decodeURIComponent(item.slice(prefix.length)) : null;
}

async function ensureCsrf(): Promise<string> {
    const response = await fetch(`${API_BASE}/api/v1/auth/csrf`, {credentials: 'include'});
    if (!response.ok) throw new AuthApiError('Unable to initialize CSRF protection', response.status);
    const payload = (await response.json()) as { data?: { csrfToken?: string } };
    return readCookie('XSRF-TOKEN') ?? payload.data?.csrfToken ?? '';
}

function withCsrf(headers?: HeadersInit): Headers {
    const result = new Headers(headers);
    const token = readCookie('XSRF-TOKEN');
    if (token) result.set('X-CSRF-TOKEN', token);
    return result;
}

async function refreshSession(): Promise<boolean> {
    if (refreshInFlight) return refreshInFlight;
    refreshInFlight = (async () => {
        try {
            const csrf = await ensureCsrf();
            const response = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
                method: 'POST', credentials: 'include', headers: {'X-CSRF-TOKEN': csrf},
            });
            return response.ok;
        } catch {
            return false;
        } finally {
            refreshInFlight = null;
        }
    })();
    return refreshInFlight;
}

export async function apiRequest<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
    const method = (init.method ?? 'GET').toUpperCase();
    const headers = new Headers(init.headers);
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
        const csrf = await ensureCsrf();
        headers.set('X-CSRF-TOKEN', csrf || readCookie('XSRF-TOKEN') || '');
    }
    headers.set('Accept', 'application/json');
    const response = await fetch(`${API_BASE}${path}`, {
        ...init, headers: withCsrf(headers), credentials: 'include',
    });
    if (response.status === 401 && retry && path !== '/api/v1/auth/login'
        && path !== '/api/v1/auth/refresh' && path !== '/api/v1/auth/logout') {
        if (await refreshSession()) return apiRequest<T>(path, init, false);
    }
    if (!response.ok) {
        const body = await response.json().catch(() => null) as {
            detail?: string;
            title?: string;
            code?: string;
            traceId?: string
        } | null;
        throw new AuthApiError(
            body?.detail ?? body?.title ?? `Request failed (${response.status})`,
            response.status,
            body?.code,
            body?.traceId,
        );
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
}

export async function login(input: LoginInput): Promise<AuthUser> {
    const result = await apiRequest<{ data: AuthUser }>('/api/v1/auth/login', {
        method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(input),
    });
    return result.data;
}

export async function getCurrentUser(): Promise<AuthUser> {
    const result = await apiRequest<{ data: AuthUser }>('/api/v1/auth/me');
    return result.data;
}

export async function logout(): Promise<void> {
    await apiRequest<void>('/api/v1/auth/logout', {method: 'POST'}, false);
}
