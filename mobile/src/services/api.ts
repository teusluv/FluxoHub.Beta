import { loadAuth, saveAuth, clearAuth } from './storage';
import { API_BASE_URL } from '../constants/api';

let _onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(handler: () => void) {
  _onUnauthorized = handler;
}

/** Fetch com timeout de 10s — app não fica pendurado se servidor não responder */
export async function fetchComTimeout(url: string, options?: RequestInit): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10_000); // 10 segundos
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } catch (e: any) {
    if (e.name === 'AbortError') {
      throw new Error('Sem resposta do servidor. Verifique sua conexão e tente de novo.');
    }
    throw e;
  } finally {
    clearTimeout(timer);
  }
}

async function getValidToken(): Promise<string | null> {
  const auth = await loadAuth();
  if (!auth) return null;

  // Verificar se o access token ainda é válido (com 60s de margem)
  const tokenAge = Date.now() - (auth.accessExpiryMs - 60_000);
  if (tokenAge > 0) {
    // Tentar refresh
    try {
      const res = await fetchComTimeout(`${API_BASE_URL}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: auth.refreshToken }),
      });
      if (res.ok) {
        const data = await res.json();
        await saveAuth({ ...auth, accessToken: data.accessToken, accessExpiryMs: Date.now() + data.accessExpiryMs });
        return data.accessToken;
      } else {
        await clearAuth();
        _onUnauthorized?.();
        return null;
      }
    } catch {
      // Sem rede — retorna token atual (pode ter expirado, mas tentamos offline)
      return auth.accessToken;
    }
  }
  return auth.accessToken;
}

export async function apiGet<T>(path: string): Promise<T> {
  const token = await getValidToken();
  const res = await fetchComTimeout(`${API_BASE_URL}${path}`, {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Accept': 'application/json',
    },
  });
  if (!res.ok) throw new ApiError(res.status, await res.json());
  return res.json();
}

export async function apiPost<T>(path: string, body: object): Promise<T> {
  const token = await getValidToken();
  const res = await fetchComTimeout(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new ApiError(res.status, await res.json());
  return res.json();
}

export async function apiPatch<T>(path: string, body: object): Promise<T> {
  const token = await getValidToken();
  const res = await fetchComTimeout(`${API_BASE_URL}${path}`, {
    method: 'PATCH',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new ApiError(res.status, await res.json());
  return res.json();
}

export class ApiError extends Error {
  constructor(public status: number, public body: unknown) {
    super(`API Error ${status}`);
  }
}
