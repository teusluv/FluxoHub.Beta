import Constants from 'expo-constants';
import { Platform } from 'react-native';

const debuggerHost = Constants.expoConfig?.hostUri?.split(':')[0] || 'localhost';
const defaultBaseUrl = Platform.OS === 'web' || !debuggerHost 
  ? 'http://localhost:8080' 
  : `http://${debuggerHost}:8080`;

export let API_BASE_URL = defaultBaseUrl;

export function updateApiBaseUrl(newUrl: string | null) {
  if (newUrl && newUrl.trim()) {
    API_BASE_URL = newUrl.trim();
  } else {
    API_BASE_URL = defaultBaseUrl;
  }
}

export const ENDPOINTS = {
  login:    '/api/v1/auth/login',
  refresh:  '/api/v1/auth/refresh',
  logout:   '/api/v1/auth/logout',
  entregas: '/api/v1/entregas',
  dia:      '/api/v1/entregas/dia',
  nota:     '/api/v1/entregas/nota',
  status:   (id: string) => `/api/v1/entregas/${id}/status`,
  canhotos: '/api/v1/canhotos',
  batchSync: '/api/v1/canhotos/batch-sync',
  canhotosPorEntrega: (id: string) => `/api/v1/canhotos/entrega/${id}`,
  notas:    (entregaId: string) => `/api/v1/entregas/${entregaId}/notas`,
};
