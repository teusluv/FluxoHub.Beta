import React, { createContext, useContext, useEffect, useRef, useState } from 'react';
import NetInfo from '@react-native-community/netinfo';
import { getSyncQueue, removeSyncItem, addToSyncQueue, updateSyncItemRetries } from '../services/storage';
import { apiPatch } from '../services/api';
import { API_BASE_URL, ENDPOINTS } from '../constants/api';
import { SyncItem } from '../types';
import * as SecureStore from 'expo-secure-store';

// Configuração de retry exponencial — alinhado com ADR-001
const RETRY_DELAYS_MS = [5_000, 15_000, 60_000, 5 * 60_000]; // 5s → 15s → 1min → 5min
const MAX_TENTATIVAS = 4;

interface SyncContextValue {
  pendingCount: number;
  pendingItems: SyncItem[];
  isOnline: boolean;
  adicionarNaFila: (item: Omit<SyncItem, 'id' | 'tentativas' | 'criadoEm'>) => Promise<void>;
  sincronizarAgora: () => Promise<void>;
}

const SyncContext = createContext<SyncContextValue | null>(null);

export function SyncProvider({ children }: { children: React.ReactNode }) {
  const [pendingCount, setPendingCount] = useState(0);
  const [pendingItems, setPendingItems] = useState<SyncItem[]>([]);
  const [isOnline, setIsOnline] = useState(true);
  const syncRef = useRef(false);
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const unsub = NetInfo.addEventListener(state => {
      const online = state.isConnected ?? false;
      setIsOnline(online);
      if (online) {
        // Cancela qualquer retry agendado — vai sincronizar agora
        if (retryTimerRef.current) clearTimeout(retryTimerRef.current);
        sincronizarAgora();
      }
    });
    atualizarContador();
    return unsub;
  }, []);

  async function atualizarContador() {
    const q = await getSyncQueue();
    setPendingCount(q.length);
    setPendingItems(q);
  }

  async function adicionarNaFila(item: Omit<SyncItem, 'id' | 'tentativas' | 'criadoEm'>) {
    await addToSyncQueue(item);
    await atualizarContador();
    if (isOnline) sincronizarAgora();
  }

  async function sincronizarAgora() {
    if (syncRef.current) return; // Mutex — evita sync concorrente
    syncRef.current = true;

    try {
      const queue = await getSyncQueue();
      if (queue.length === 0) return;

      console.log(`[Sync] Iniciando sync: ${queue.length} itens na fila`);

      for (const item of queue) {
        if (item.tentativas >= MAX_TENTATIVAS) {
          console.warn(`[Sync] Item ${item.id} excedeu ${MAX_TENTATIVAS} tentativas — removendo da fila`);
          await removeSyncItem(item.id);
          continue;
        }

        try {
          if (item.tipo === 'STATUS') {
            await apiPatch(ENDPOINTS.status(item.entregaId), item.payload);
          } else if (item.tipo === 'CANHOTO') {
            await uploadCanhotoMultipart(item);
          }
          await removeSyncItem(item.id);
          console.log(`[Sync] Item ${item.id} sincronizado com sucesso`);
        } catch (e: any) {
          const novasTentativas = (item.tentativas || 0) + 1;
          await updateSyncItemRetries(item.id, novasTentativas);

          // Agenda retry exponencial
          const delayMs = RETRY_DELAYS_MS[Math.min(novasTentativas - 1, RETRY_DELAYS_MS.length - 1)];
          console.warn(`[Sync] Falha no item ${item.id} (tentativa ${novasTentativas}/${MAX_TENTATIVAS}) — retry em ${delayMs / 1000}s`);

          if (isOnline) {
            retryTimerRef.current = setTimeout(() => sincronizarAgora(), delayMs);
          }
        }
      }

      await atualizarContador();
    } finally {
      syncRef.current = false;
    }
  }

  return (
    <SyncContext.Provider value={{ pendingCount, pendingItems, isOnline, adicionarNaFila, sincronizarAgora }}>
      {children}
    </SyncContext.Provider>
  );
}

export function useSync() {
  const ctx = useContext(SyncContext);
  if (!ctx) throw new Error('useSync deve ser usado dentro de SyncProvider');
  return ctx;
}

// ============================================================================
// Upload multipart real — implementação da Fase 3
// ============================================================================

async function uploadCanhotoMultipart(item: SyncItem): Promise<void> {
  const { imagemUri, entregaId, deviceId, capturedAt } = item.payload as {
    imagemUri: string;
    entregaId: string;
    deviceId: string;
    capturedAt: string;
  };

  // Recupera token JWT do storage seguro
  const authJson = await SecureStore.getItemAsync('pod_auth');
  if (!authJson) throw new Error('Não autenticado — impossível sincronizar');
  const auth = JSON.parse(authJson);

  // Monta FormData com a imagem e metadados
  const formData = new FormData();
  formData.append('entregaId', entregaId);
  formData.append('deviceId', deviceId);
  formData.append('capturedAt', capturedAt);
  formData.append('imagem', {
    uri: imagemUri,
    type: 'image/jpeg',
    name: `canhoto_${deviceId}_${Date.now()}.jpg`,
  } as any);

  const response = await fetch(`${API_BASE_URL}${ENDPOINTS.canhotos}`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${auth.accessToken}`,
      // NÃO definir Content-Type — o fetch define automaticamente com boundary correto
    },
    body: formData,
  });

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.detail || body.message || `HTTP ${response.status}`);
  }

  console.log(`[Sync] Canhoto enviado com sucesso: entrega=${entregaId}`);
}
