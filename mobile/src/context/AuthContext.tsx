import React, { createContext, useContext, useEffect, useState } from 'react';
import { AuthState } from '../types';
import { loadAuth, saveAuth, clearAuth } from '../services/storage';
import { API_BASE_URL } from '../constants/api';
import { setUnauthorizedHandler, fetchComTimeout } from '../services/api';

interface AuthContextValue {
  auth: AuthState | null;
  loading: boolean;
  login: (email: string, senha: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [auth, setAuth] = useState<AuthState | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadAuth().then(stored => {
      setAuth(stored);
      setLoading(false);
    });
    setUnauthorizedHandler(() => {
      setAuth(null);
    });
  }, []);

  async function login(email: string, senha: string) {
    const res = await fetchComTimeout(`${API_BASE_URL}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, senha }),
    });
    if (!res.ok) {
      const err = await res.json();
      throw new Error(err.detail || 'Email ou senha incorretos');
    }
    const data = await res.json();
    const authState: AuthState = {
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      accessExpiryMs: Date.now() + data.accessExpiryMs,
      usuarioId: data.usuarioId,
      nome: data.nome,
      email: data.email,
      papel: data.papel,
      filialId: data.filialId,
      filialNome: data.filialNome,
    };
    await saveAuth(authState);
    setAuth(authState);
  }

  async function logout() {
    if (auth?.refreshToken) {
      try {
        await fetchComTimeout(`${API_BASE_URL}/api/v1/auth/logout`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: auth.refreshToken }),
        });
      } catch { /* ignora erro de rede no logout */ }
    }
    await clearAuth();
    setAuth(null);
  }

  return (
    <AuthContext.Provider value={{ auth, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth deve ser usado dentro de AuthProvider');
  return ctx;
}
