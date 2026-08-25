/**
 * HomeScreen — Tela "Entregas de Hoje"
 *
 * DECISÕES DE DESIGN:
 * - Pendentes SEMPRE no topo, confirmados no fim (motorista foca no que falta)
 * - Cada card tem altura generosa (min 72dp) — alvo de toque seguro com dedo grosso
 * - O "Selo" é o elemento de assinatura: borda quadrada + CAPS, imita carimbo físico
 * - Zero emoji como decoração — ações têm texto, não ícone ambíguo sozinho
 * - Barra de offline discreta mas permanente — nunca bloqueia ação
 * - Texto de estado vazio é funcional, não celebratório ("Tudo entregue hoje")
 * - "Pendente", "Enviando", "Confirmado" — sem jargão técnico visível
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  View, Text, FlatList, StyleSheet, TouchableOpacity,
  RefreshControl, ActivityIndicator, Alert, Animated,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useAuth } from '../context/AuthContext';
import { useSync } from '../context/SyncContext';
import { apiGet } from '../services/api';
import { ENDPOINTS } from '../constants/api';
import { Entrega, StatusEntrega } from '../types';
import { COLORS, SPACING, RADIUS, FONT_SIZE, TOUCH } from '../constants/theme';

// ─── Configuração de status com linguagem do motorista (sem jargão) ───────────
const STATUS_CONFIG: Record<StatusEntrega, {
  label: string;        // Linguagem do campo — sem "upload", "sync", "cache"
  seloColor: string;    // Cor do texto do selo
  seloBorder: string;   // Cor da borda do selo
  seloBg: string;       // Fundo do selo
  barColor: string;     // Cor da barra lateral esquerda do card
}> = {
  PENDENTE: {
    label:      'PENDENTE',
    seloColor:  COLORS.amber,
    seloBorder: COLORS.amber,
    seloBg:     '#F5A62310',
    barColor:   COLORS.amber,
  },
  EM_ROTA: {
    label:      'EM ROTA',
    seloColor:  COLORS.blue,
    seloBorder: COLORS.blue,
    seloBg:     '#3498DB10',
    barColor:   COLORS.blue,
  },
  ENTREGUE_SEM_CANHOTO: {
    label:      'SEM COMPROVANTE',
    seloColor:  COLORS.amber,
    seloBorder: COLORS.amber,
    seloBg:     '#F5A62310',
    barColor:   COLORS.amber,
  },
  ENTREGUE_COM_CANHOTO: {
    label:      'CONFIRMADO',
    seloColor:  COLORS.green,
    seloBorder: COLORS.green,
    seloBg:     '#2ECC7110',
    barColor:   COLORS.green,
  },
  DIVERGENCIA: {
    label:      'VER C/ SUPERVISOR',
    seloColor:  COLORS.red,
    seloBorder: COLORS.red,
    seloBg:     '#E74C3C10',
    barColor:   COLORS.red,
  },
};

// ─── O Selo — elemento de assinatura visual do app ────────────────────────────
// Imita um carimbo físico de conferência de mercadoria.
// Borda quadrada (RADIUS.xl = 4), texto em CAPS, fonte densa.
// Reconhecível como "desse app" mesmo sem o logo.
function Selo({ status, isPendingSync }: { status: StatusEntrega; isPendingSync?: boolean }) {
  if (isPendingSync) {
    return (
      <View style={[styles.selo, {
        borderColor: COLORS.blue,
        backgroundColor: '#3498DB10',
      }]}>
        <Text style={[styles.seloText, { color: COLORS.blue }]}>
          ENVIANDO
        </Text>
      </View>
    );
  }

  const cfg = STATUS_CONFIG[status];
  return (
    <View style={[styles.selo, {
      borderColor: cfg.seloBorder,
      backgroundColor: cfg.seloBg,
    }]}>
      <Text style={[styles.seloText, { color: cfg.seloColor }]}>
        {cfg.label}
      </Text>
    </View>
  );
}

// ─── Card de entrega ───────────────────────────────────────────────────────────
// Altura mínima garantida pelo paddingVertical generoso.
// Barra lateral esquerda comunica status de relance (cor = estado).
// NF em destaque (grande, tabular) — a informação mais escaneada.
function EntregaCard({
  entrega,
  isPendingSync,
  onPress,
}: {
  entrega: Entrega;
  isPendingSync: boolean;
  onPress: () => void;
}) {
  const cfg = STATUS_CONFIG[entrega.status];
  const barColor = isPendingSync ? COLORS.blue : cfg.barColor;
  const isConfirmado = entrega.status === 'ENTREGUE_COM_CANHOTO';

  return (
    <TouchableOpacity
      style={[styles.card, isConfirmado && styles.cardConfirmado]}
      onPress={onPress}
      activeOpacity={0.75}
      accessibilityLabel={`Entrega ${entrega.numeroNotaFiscal} para ${entrega.clienteNome}, status ${isPendingSync ? 'enviando' : cfg.label}`}
    >
      {/* Barra lateral — comunica status de relance, sem precisar ler */}
      <View style={[styles.cardBar, { backgroundColor: barColor }]} />

      <View style={styles.cardBody}>
        {/* Linha 1: NF (grande, escaneável) */}
        <Text style={[styles.nfNumero, isConfirmado && styles.textoConfirmado]}>
          {entrega.numeroNotaFiscal}
        </Text>

        {/* Linha 2: Cliente */}
        <Text
          style={[styles.clienteNome, isConfirmado && styles.textoConfirmado]}
          numberOfLines={1}
        >
          {entrega.clienteNome}
        </Text>

        {/* Linha 3: Data prevista (se houver) */}
        {entrega.dataPrevistaEntrega && !isConfirmado && (
          <Text style={styles.dataPrevista}>
            Prazo: {new Date(entrega.dataPrevistaEntrega).toLocaleDateString('pt-BR')}
          </Text>
        )}
      </View>

      {/* Selo — o elemento de assinatura */}
      <View style={styles.cardSelo}>
        <Selo status={entrega.status} isPendingSync={isPendingSync} />
        <Text style={styles.cardChevron}>›</Text>
      </View>
    </TouchableOpacity>
  );
}

// ─── Barra de offline discreta ─────────────────────────────────────────────────
// Presente mas não alarmante. Nunca bloqueia ação.
// Linguagem humana: "sem internet" e "suas fotos estão salvas".
function BarraOffline({ isOnline, pendingCount }: { isOnline: boolean; pendingCount: number }) {
  if (isOnline && pendingCount === 0) return null;

  const msg = !isOnline
    ? `Sem internet — suas fotos estão salvas e serão enviadas quando a conexão voltar`
    : `${pendingCount} foto${pendingCount > 1 ? 's' : ''} aguardando envio`;

  const bg = !isOnline ? '#E74C3C18' : '#F5A62318';
  const cor = !isOnline ? COLORS.red : COLORS.amber;

  return (
    <View style={[styles.barraOffline, { backgroundColor: bg, borderColor: cor + '40' }]}>
      <View style={[styles.barraOfflineDot, { backgroundColor: cor }]} />
      <Text style={[styles.barraOfflineText, { color: cor }]}>{msg}</Text>
    </View>
  );
}

// ─── Tela Principal ────────────────────────────────────────────────────────────
export default function HomeScreen({ navigation }: any) {
  const { auth, logout } = useAuth();
  const { pendingCount, pendingItems, isOnline } = useSync();
  const [entregas, setEntregas] = useState<Entrega[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const hoje = new Date().toISOString().split('T')[0];

  const carregar = useCallback(async () => {
    try {
      const data = await apiGet<Entrega[]>(`${ENDPOINTS.dia}?data=${hoje}`);
      // Pendentes no topo, confirmados no fim
      const ordenadas = [...data].sort((a, b) => {
        const prioridade = (s: StatusEntrega) =>
          s === 'PENDENTE' || s === 'EM_ROTA' || s === 'ENTREGUE_SEM_CANHOTO' ? 0 : 1;
        return prioridade(a.status) - prioridade(b.status);
      });
      setEntregas(ordenadas);
    } catch (e) {
      // offline — mantém dados anteriores em cache
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [hoje]);

  useEffect(() => { carregar(); }, [carregar]);

  const total     = entregas.length;
  const feitas    = entregas.filter(e => e.status === 'ENTREGUE_COM_CANHOTO').length;
  const pendentes = total - feitas;

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={COLORS.amber} />
        <Text style={styles.loadingText}>Carregando suas entregas...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <StatusBar style="light" />

      {/* ── Header ──────────────────────────────────────────────── */}
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          {/* Identificação sem saudação de app de consumidor */}
          <Text style={styles.headerNome}>
            {auth?.nome.split(' ')[0] ?? 'Motorista'}
          </Text>
          <Text style={styles.headerFilial} numberOfLines={1}>
            {auth?.filialNome ?? ''}
          </Text>
        </View>
        <TouchableOpacity
          style={styles.sairBtn}
          onPress={() => Alert.alert('Sair', 'Deseja encerrar a sessão?', [
            { text: 'Cancelar', style: 'cancel' },
            { text: 'Sair', style: 'destructive', onPress: logout },
          ])}
          accessibilityLabel="Sair do aplicativo"
        >
          <Text style={styles.sairText}>SAIR</Text>
        </TouchableOpacity>
      </View>

      {/* ── Barra offline ───────────────────────────────────────── */}
      <BarraOffline isOnline={isOnline} pendingCount={pendingCount} />

      {/* ── Painel do dia — checklist de prancheta ──────────────── */}
      {/*
        Elemento de assinatura secundário: este painel parece um
        cabeçalho de prancheta de conferência — linha grossa no topo,
        dois contadores grandes, sem decoração.
      */}
      <View style={styles.painelDia}>
        <View style={styles.painelBarra} />
        <View style={styles.painelNumeros}>
          <View style={styles.painelItem}>
            <Text style={[styles.painelNum, { color: COLORS.amber }]}>{pendentes}</Text>
            <Text style={styles.painelLabel}>PARA ENTREGAR</Text>
          </View>
          <View style={styles.painelDivisor} />
          <View style={styles.painelItem}>
            <Text style={[styles.painelNum, { color: COLORS.green }]}>{feitas}</Text>
            <Text style={styles.painelLabel}>CONFIRMADAS</Text>
          </View>
          <View style={styles.painelDivisor} />
          <View style={styles.painelItem}>
            <Text style={[styles.painelNum, { color: COLORS.textPrimary }]}>{total}</Text>
            <Text style={styles.painelLabel}>TOTAL HOJE</Text>
          </View>
        </View>
      </View>

      {/* ── Lista de entregas ────────────────────────────────────── */}
      <FlatList
        data={entregas}
        keyExtractor={e => e.id}
        contentContainerStyle={styles.lista}
        renderItem={({ item }) => (
          <EntregaCard
            entrega={item}
            isPendingSync={pendingItems.some(i => i.entregaId === item.id)}
            onPress={() => navigation.navigate('Detalhe', { entrega: item })}
          />
        )}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => { setRefreshing(true); carregar(); }}
            tintColor={COLORS.amber}
            colors={[COLORS.amber]}
          />
        }
        ListEmptyComponent={
          <View style={styles.vazio}>
            <Text style={styles.vazioTitulo}>Tudo entregue hoje.</Text>
            <Text style={styles.vazioSubtitulo}>
              Nenhuma entrega pendente para esta data.{'\n'}
              Puxe para baixo para atualizar.
            </Text>
          </View>
        }
      />
    </View>
  );
}

// ─── Estilos ──────────────────────────────────────────────────────────────────
const styles = StyleSheet.create({
  container:        { flex: 1, backgroundColor: COLORS.background },
  center:           { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: COLORS.background },
  loadingText:      { color: COLORS.textSecondary, marginTop: SPACING.md, fontSize: FONT_SIZE.md },

  // Header — sem saudação festiva, identidade direta
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: SPACING.lg,
    paddingTop: 56,
    paddingBottom: SPACING.md,
    backgroundColor: COLORS.background,
  },
  headerLeft:       { flex: 1 },
  headerNome:       { fontSize: FONT_SIZE.xl, fontWeight: '800', color: COLORS.textPrimary, letterSpacing: -0.5 },
  headerFilial:     { fontSize: FONT_SIZE.sm, color: COLORS.textSecondary, marginTop: 2 },
  sairBtn:          {
    paddingHorizontal: SPACING.md,
    paddingVertical: SPACING.sm,
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: RADIUS.sm,
  },
  sairText:         { fontSize: FONT_SIZE.xs, fontWeight: '700', color: COLORS.textMuted, letterSpacing: 1 },

  // Barra offline — SYSTEM_OFFLINE estilo terminal (Stitch spec)
  barraOffline: {
    marginHorizontal: 0,
    marginBottom: SPACING.sm,
    borderRadius: RADIUS.none,
    paddingHorizontal: SPACING.md,
    paddingVertical: SPACING.sm,
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: 1,
  },
  barraOfflineDot:  { width: 6, height: 6, borderRadius: 3, marginRight: SPACING.sm },
  barraOfflineText: { fontSize: FONT_SIZE.xs, fontWeight: '600', flex: 1, lineHeight: 16, letterSpacing: 0.5 },

  // Painel do dia — cabeçalho de prancheta (elemento de assinatura)
  painelDia: {
    marginHorizontal: SPACING.lg,
    marginBottom: SPACING.lg,
    backgroundColor: COLORS.surface,
    borderRadius: RADIUS.none,
    borderWidth: 1,
    borderColor: COLORS.outlineVariant,
    overflow: 'hidden',
  },
  painelBarra:      { height: 3, backgroundColor: COLORS.primary, width: '100%' },
  painelNumeros:    { flexDirection: 'row', paddingVertical: SPACING.lg, paddingHorizontal: SPACING.md },
  painelItem:       { flex: 1, alignItems: 'center' },
  painelNum:        { fontSize: FONT_SIZE.xxl, fontWeight: '900', letterSpacing: -1, lineHeight: 36 },
  painelLabel:      { fontSize: FONT_SIZE.xs, fontWeight: '700', color: COLORS.textMuted, marginTop: 4, letterSpacing: 0.5 },
  painelDivisor:    { width: 1, backgroundColor: COLORS.outlineVariant, marginVertical: 4 },

  // Lista
  lista:            { paddingHorizontal: SPACING.lg, paddingBottom: SPACING.xxl, paddingTop: SPACING.xs },

  // Card — SHARP corners conforme Stitch zero-radius
  card: {
    flexDirection: 'row',
    backgroundColor: COLORS.surface,
    borderRadius: RADIUS.none,
    marginBottom: SPACING.sm,
    borderWidth: 1,
    borderColor: COLORS.outlineVariant,
    overflow: 'hidden',
    minHeight: TOUCH.min,
  },
  cardConfirmado:   { opacity: 0.6 },
  cardBar:          { width: 4, minHeight: TOUCH.min },
  cardBody:         { flex: 1, paddingVertical: SPACING.md, paddingHorizontal: SPACING.md, justifyContent: 'center' },
  nfNumero: {
    fontSize: FONT_SIZE.bodySmall,
    fontWeight: '900',
    color: COLORS.outline,
    letterSpacing: 2,
    lineHeight: 20,
  },
  clienteNome:      { fontSize: FONT_SIZE.headline2, color: COLORS.onSurface, marginTop: 4, fontWeight: '600' },
  textoConfirmado:  { color: COLORS.textMuted },
  dataPrevista:     { fontSize: FONT_SIZE.xs, color: COLORS.textMuted, marginTop: 4 },
  cardSelo:         {
    paddingVertical: SPACING.md,
    paddingRight: SPACING.md,
    alignItems: 'flex-end',
    justifyContent: 'center',
    gap: 6,
  },
  cardChevron:      { fontSize: 20, color: COLORS.textMuted, lineHeight: 22 },

  // Selo — SHARP (radius 0) — carimbo físico (Stitch: rectangular solid badge)
  selo: {
    borderWidth: 1,
    borderRadius: RADIUS.none,
    paddingHorizontal: SPACING.xs,
    paddingVertical: 2,
  },
  seloText: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.5,
  },


  // Estado vazio — funcional, não celebratório
  vazio:            { paddingTop: SPACING.xxl, paddingHorizontal: SPACING.xl, alignItems: 'center' },
  vazioTitulo:      { fontSize: FONT_SIZE.lg, fontWeight: '700', color: COLORS.textPrimary, textAlign: 'center' },
  vazioSubtitulo:   { fontSize: FONT_SIZE.sm, color: COLORS.textMuted, textAlign: 'center', marginTop: SPACING.sm, lineHeight: 20 },
});
