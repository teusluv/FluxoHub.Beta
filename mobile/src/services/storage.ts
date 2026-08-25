import * as SecureStore from 'expo-secure-store';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { AuthState, SyncItem } from '../types';

const KEYS = {
  AUTH: 'pod_auth',
  SYNC_QUEUE: 'pod_sync_queue',
};

export async function saveAuth(auth: AuthState): Promise<void> {
  await SecureStore.setItemAsync(KEYS.AUTH, JSON.stringify(auth));
}

export async function loadAuth(): Promise<AuthState | null> {
  const raw = await SecureStore.getItemAsync(KEYS.AUTH);
  return raw ? JSON.parse(raw) : null;
}

export async function clearAuth(): Promise<void> {
  await SecureStore.deleteItemAsync(KEYS.AUTH);
}

// Fila de sincronização offline (AsyncStorage pois não é dado sensível)
export async function getSyncQueue(): Promise<SyncItem[]> {
  const raw = await AsyncStorage.getItem(KEYS.SYNC_QUEUE);
  return raw ? JSON.parse(raw) : [];
}

export async function addToSyncQueue(item: Omit<SyncItem, 'id' | 'tentativas' | 'criadoEm'>): Promise<void> {
  const queue = await getSyncQueue();
  queue.push({
    ...item,
    id: `${Date.now()}-${Math.random()}`,
    tentativas: 0,
    criadoEm: new Date().toISOString(),
  });
  await AsyncStorage.setItem(KEYS.SYNC_QUEUE, JSON.stringify(queue));
}

export async function removeSyncItem(id: string): Promise<void> {
  const queue = await getSyncQueue();
  await AsyncStorage.setItem(
    KEYS.SYNC_QUEUE,
    JSON.stringify(queue.filter(i => i.id !== id))
  );
}

/** Atualiza o número de tentativas de um item — usado para backoff exponencial. */
export async function updateSyncItemRetries(id: string, tentativas: number): Promise<void> {
  const queue = await getSyncQueue();
  const updated = queue.map(i => i.id === id ? { ...i, tentativas } : i);
  await AsyncStorage.setItem(KEYS.SYNC_QUEUE, JSON.stringify(updated));
}

