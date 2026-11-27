import {createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useState} from 'react';
import {
    AuthApiError,
    type AuthUser,
    getCurrentUser,
    login as loginRequest,
    logout as logoutRequest
} from '../../shared/lib/authApi';

type AuthState = 'loading' | 'authenticated' | 'anonymous' | 'error';
type AuthContextValue = {
    state: AuthState;
    user: AuthUser | null;
    error: string | null;
    signIn: (email: string, password: string) => Promise<void>;
    signOut: () => Promise<void>;
    reload: () => Promise<void>;
};
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({children}: { children: ReactNode }) {
    const [state, setState] = useState<AuthState>('loading');
    const [user, setUser] = useState<AuthUser | null>(null);
    const [error, setError] = useState<string | null>(null);

    const reload = useCallback(async () => {
        setState('loading');
        try {
            const current = await getCurrentUser();
            setUser(current);
            setState('authenticated');
            setError(null);
        } catch (cause) {
            setUser(null);
            if (cause instanceof AuthApiError && cause.status === 401) {
                setState('anonymous');
                setError(null);
            } else {
                setState('error');
                setError(cause instanceof TypeError
                    ? '目前無法連線至後端，請稍後重試。'
                    : cause instanceof Error ? cause.message : '無法確認目前的登入狀態，請稍後重試。');
            }
        }
    }, []);

    useEffect(() => {
        void reload();
    }, [reload]);

    const signIn = useCallback(async (email: string, password: string) => {
        try {
            const current = await loginRequest({email, password});
            setUser(current);
            setState('authenticated');
            setError(null);
        } catch (cause) {
            const message = cause instanceof Error ? cause.message : 'Login failed';
            setError(message);
            setState('anonymous');
            throw cause;
        }
    }, []);

    const signOut = useCallback(async () => {
        setError(null);
        try {
            await logoutRequest();
            setUser(null);
            setState('anonymous');
        } catch (cause) {
            // Keep the current principal visible: the server may not have revoked the
            // refresh-token family, so do not report a successful local sign-out.
            setError(cause instanceof Error
                ? `登出失敗，無法確認伺服器 Session 已撤銷：${cause.message}`
                : '登出失敗，無法確認伺服器 Session 已撤銷。');
            throw cause;
        }
    }, []);

    const value = useMemo(() => ({state, user, error, signIn, signOut, reload}),
        [state, user, error, signIn, signOut, reload]);
    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
    const value = useContext(AuthContext);
    if (!value) throw new Error('useAuth must be used within AuthProvider');
    return value;
}
