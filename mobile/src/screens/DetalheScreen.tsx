import React, { useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity,
  ScrollView, Alert, ActivityIndicator,
} from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useSync } from '../context/SyncContext';
import { Entrega, StatusEntrega } from '../types';
import { COLORS, SPACING, RADIUS } from '../constants/theme';

const PROXIMOS_STATUS: Partial<Record<StatusEntrega, StatusEntrega>> = {
  PENDENTE: 'EM_ROTA',
  EM_ROTA: 'ENTREGUE_SEM_CANHOTO',
};

const STATUS_LABELS: Partial<Record<StatusEntrega, string>> = {
  EM_ROTA: '🚚 Iniciar Rota',
  ENTREGUE_SEM_CANHOTO: '✅ Marcar como Entregue',
};

function InfoRow({ label, value }: { label: string; value?: string | null }) {
  if (!value) return null;
  return (
    <View style={styles.infoRow}>
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={styles.infoValue}>{value}</Text>
    </View>
  );
}

export default function DetalheScreen({ route, navigation }: any) {
  const { entrega: entregaInicial }: { entrega: Entrega } = route.params;
  const [entrega, setEntrega] = useState(entregaInicial);
  const [loading, setLoading] = useState(false);
  const { adicionarNaFila, isOnline } = useSync();

  const proximoStatus = PROXIMOS_STATUS[entrega.status];
  const btnLabel = STATUS_LABELS[proximoStatus as StatusEntrega];

  async function mudarStatus() {
    if (!proximoStatus) return;
    Alert.alert(
      'Confirmar',
      `Deseja mudar para: ${proximoStatus.replace(/_/g, ' ')}?`,
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Confirmar',
          onPress: async () => {
            setLoading(true);
            const payload = { novoStatus: proximoStatus };
            // Offline-first: enfileira e tenta sincronizar
            await adicionarNaFila({
              tipo: 'STATUS',
              entregaId: entrega.id,
              payload,
            });
            // Otimistic update local
            setEntrega(prev => ({ ...prev, status: proximoStatus }));
            setLoading(false);
          },
        },
      ]
    );
  }

  function irParaCanhoto() {
    navigation.navigate('Canhoto', { entrega });
  }

  return (
    <View style={styles.container}>
      <StatusBar style="light" />

      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={{ color: COLORS.primary, fontSize: 17 }}>‹ Voltar</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Entrega</Text>
        <View style={{ width: 70 }} />
      </View>

      <ScrollView contentContainerStyle={styles.scroll}>
        {/* Status card */}
        <View style={styles.statusCard}>
          <Text style={styles.nf}>NF {entrega.numeroNotaFiscal}</Text>
          <Text style={styles.status}>{entrega.status.replace(/_/g, ' ')}</Text>
        </View>

        {/* Informações */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Cliente</Text>
          <InfoRow label="Nome" value={entrega.clienteNome} />
          <InfoRow label="Data prevista" value={
            entrega.dataPrevistaEntrega
              ? new Date(entrega.dataPrevistaEntrega).toLocaleDateString('pt-BR')
              : null
          } />
          <InfoRow label="Observações" value={entrega.observacoes} />
        </View>

        {entrega.vendedorNome && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Vendedor</Text>
            <InfoRow label="Nome" value={entrega.vendedorNome} />
          </View>
        )}

        {/* Ações */}
        <View style={styles.actions}>
          {/* Botão de mudar status */}
          {btnLabel && (
            <TouchableOpacity onPress={mudarStatus} disabled={loading} style={styles.btnWrapper}>
              <LinearGradient
                colors={[COLORS.primary, COLORS.accent]}
                start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }}
                style={styles.btn}
              >
                {loading
                  ? <ActivityIndicator color="#fff" />
                  : <Text style={styles.btnText}>{btnLabel}</Text>
                }
              </LinearGradient>
            </TouchableOpacity>
          )}

          {/* Botão de captura de canhoto */}
          {(entrega.status === 'EM_ROTA' || entrega.status === 'ENTREGUE_SEM_CANHOTO') && (
            <TouchableOpacity style={styles.btnSecondary} onPress={irParaCanhoto}>
              <Text style={styles.btnSecondaryText}>📷 Fotografar Canhoto</Text>
            </TouchableOpacity>
          )}
        </View>

        {!isOnline && (
          <Text style={styles.offlineHint}>
            📵 Sem conexão. As alterações serão sincronizadas automaticamente quando voltar online.
          </Text>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: COLORS.background },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: SPACING.md, paddingTop: 56, paddingBottom: SPACING.md,
    borderBottomWidth: 1, borderBottomColor: COLORS.border,
  },
  backBtn: { width: 70 },
  headerTitle: { fontSize: 17, fontWeight: '700', color: COLORS.textPrimary },
  scroll: { padding: SPACING.lg, paddingBottom: SPACING.xxl },
  statusCard: {
    backgroundColor: COLORS.surface, borderRadius: RADIUS.lg,
    padding: SPACING.lg, marginBottom: SPACING.lg,
    alignItems: 'center', borderWidth: 1, borderColor: COLORS.border,
  },
  nf: { fontSize: 13, color: COLORS.textSecondary, fontWeight: '600' },
  status: { fontSize: 20, fontWeight: '800', color: COLORS.textPrimary, marginTop: SPACING.xs, textAlign: 'center' },
  section: {
    backgroundColor: COLORS.surface, borderRadius: RADIUS.lg,
    padding: SPACING.md, marginBottom: SPACING.md,
    borderWidth: 1, borderColor: COLORS.border,
  },
  sectionTitle: { fontSize: 13, fontWeight: '700', color: COLORS.primary, marginBottom: SPACING.sm },
  infoRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: SPACING.xs },
  infoLabel: { fontSize: 13, color: COLORS.textSecondary, flex: 1 },
  infoValue: { fontSize: 13, color: COLORS.textPrimary, fontWeight: '600', flex: 2, textAlign: 'right' },
  actions: { marginTop: SPACING.md, gap: SPACING.md },
  btnWrapper: {},
  btn: { height: 52, borderRadius: RADIUS.md, alignItems: 'center', justifyContent: 'center' },
  btnText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  btnSecondary: {
    height: 52, borderRadius: RADIUS.md, alignItems: 'center', justifyContent: 'center',
    borderWidth: 1.5, borderColor: COLORS.primary,
  },
  btnSecondaryText: { color: COLORS.primary, fontSize: 16, fontWeight: '700' },
  offlineHint: { color: COLORS.warning, fontSize: 13, textAlign: 'center', marginTop: SPACING.lg, lineHeight: 20 },
});
