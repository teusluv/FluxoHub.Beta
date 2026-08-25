import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useAuth } from '../context/AuthContext';
import { useSync } from '../context/SyncContext';
import { apiGet, apiPost } from '../services/api';
import { ENDPOINTS } from '../constants/api';
import { NotaEntrega, TipoNota, Page } from '../types';
import { COLORS, SPACING, RADIUS, FONT_SIZE } from '../constants/theme';

interface NotasSectionProps {
  entregaId: string;
}

const TIPO_CONFIG: Record<TipoNota, { label: string; color: string; bg: string }> = {
  GERAL: {
    label: 'GERAL',
    color: '#b5c4ff',
    bg: '#b5c4ff20',
  },
  INSTRUCAO_ENTREGA: {
    label: 'INSTRUÇÃO',
    color: COLORS.warning,
    bg: '#F5A62320',
  },
  DIVERGENCIA: {
    label: 'DIVERGÊNCIA',
    color: COLORS.danger,
    bg: '#E74C3C20',
  },
  INTERNA: {
    label: 'INTERNA',
    color: '#d0bcff',
    bg: '#d0bcff20',
  },
};

function formatarTempoRelativo(dataIso: string): string {
  try {
    const data = new Date(dataIso);
    const agora = new Date();
    const diffMs = agora.getTime() - data.getTime();
    const diffSeg = Math.floor(diffMs / 1000);
    const diffMin = Math.floor(diffSeg / 60);
    const diffHoras = Math.floor(diffMin / 60);
    const diffDias = Math.floor(diffHoras / 24);

    if (diffMin < 1) return 'agora';
    if (diffMin < 60) return `há ${diffMin} min`;
    if (diffHoras < 24) return `há ${diffHoras}h`;
    if (diffDias === 1) return 'ontem';
    if (diffDias < 7) return `há ${diffDias} dias`;
    return data.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
  } catch {
    return '';
  }
}

export default function NotasSection({ entregaId }: NotasSectionProps) {
  const { auth } = useAuth();
  const { isOnline, pendingItems, adicionarNaFila } = useSync();

  const [notas, setNotas] = useState<NotaEntrega[]>([]);
  const [loading, setLoading] = useState(true);
  const [enviando, setEnviando] = useState(false);

  // Form states
  const [tipoSelecionado, setTipoSelecionado] = useState<TipoNota>('GERAL');
  const [conteudo, setConteudo] = useState('');

  const podeCriar = auth?.papel === 'VENDEDOR' || auth?.papel === 'ADMIN';

  const carregarNotas = useCallback(async () => {
    if (!entregaId) return;
    try {
      const data = await apiGet<Page<NotaEntrega>>(`${ENDPOINTS.notas(entregaId)}?size=50`);
      setNotas(data.content || []);
    } catch (e) {
      console.warn('Erro ao carregar notas da entrega:', e);
    } finally {
      setLoading(false);
    }
  }, [entregaId]);

  useEffect(() => {
    carregarNotas();
  }, [carregarNotas]);

  // Identificar notas pendentes na fila offline do dispositivo
  const notasPendentesLocais: NotaEntrega[] = pendingItems
    .filter(item => item.tipo === 'NOTA' && item.entregaId === entregaId)
    .map(item => {
      const p = item.payload as { conteudo: string; tipo: TipoNota; idempotencyKey?: string };
      return {
        id: item.id,
        entregaId,
        autorId: auth?.usuarioId || '',
        autorNome: auth?.nome || 'Você',
        autorPapel: auth?.papel || 'VENDEDOR',
        filialId: auth?.filialId || '',
        tipo: p.tipo || 'GERAL',
        conteudo: p.conteudo || '',
        idempotencyKey: p.idempotencyKey,
        criadoEm: item.criadoEm || new Date().toISOString(),
        isPendingSync: true,
      };
    });

  // Combinar notas remotas com notas locais pendentes
  const todasNotas = [
    ...notasPendentesLocais,
    ...notas.filter(n => !notasPendentesLocais.some(p => p.idempotencyKey && p.idempotencyKey === n.idempotencyKey)),
  ];

  async function handleAdicionarNota() {
    const textoLimpo = conteudo.trim();
    if (!textoLimpo) {
      Alert.alert('Nota vazia', 'Por favor, digite o conteúdo da nota antes de salvar.');
      return;
    }

    setEnviando(true);

    // Gera chave de idempotência única (UUID)
    const idempotencyKey = 'nota-' + Date.now() + '-' + Math.random().toString(36).substring(2, 9);
    const payload = {
      conteudo: textoLimpo,
      tipo: tipoSelecionado,
      idempotencyKey,
    };

    try {
      if (isOnline) {
        // Envia direto se online
        const novaNota = await apiPost<NotaEntrega>(ENDPOINTS.notas(entregaId), payload);
        setNotas(prev => [novaNota, ...prev]);
        setConteudo('');
      } else {
        // Enfileira offline
        await adicionarNaFila({
          tipo: 'NOTA',
          entregaId,
          payload,
        });
        setConteudo('');
        Alert.alert(
          'Salvo offline',
          'A nota foi registrada localmente e será sincronizada assim que a internet for restabelecida.'
        );
      }
    } catch (err: any) {
      // Se falhou por rede, salva na fila offline como fallback
      await adicionarNaFila({
        tipo: 'NOTA',
        entregaId,
        payload,
      });
      setConteudo('');
      Alert.alert(
        'Salvo na fila',
        'Não foi possível contatar o servidor agora. A nota foi guardada na fila de envio.'
      );
    } finally {
      setEnviando(false);
    }
  }

  return (
    <View style={styles.container}>
      {/* Cabeçalho da Seção */}
      <View style={styles.headerRow}>
        <View style={styles.headerTitleWrap}>
          <Text style={styles.sectionTitle}>Notas & Observações</Text>
          {todasNotas.length > 0 && (
            <View style={styles.badgeCount}>
              <Text style={styles.badgeCountText}>{todasNotas.length}</Text>
            </View>
          )}
        </View>
        <TouchableOpacity onPress={carregarNotas} hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}>
          <Text style={styles.refreshLink}>Atualizar</Text>
        </TouchableOpacity>
      </View>

      {/* Formulário de Criação (Apenas Vendedor e Admin) */}
      {podeCriar ? (
        <View style={styles.formCard}>
          <Text style={styles.formLabel}>Nova observação:</Text>

          {/* Chips de Categoria */}
          <View style={styles.chipsContainer}>
            {(['GERAL', 'INSTRUCAO_ENTREGA', 'DIVERGENCIA', 'INTERNA'] as TipoNota[]).map(tipo => {
              const cfg = TIPO_CONFIG[tipo];
              const isSelected = tipoSelecionado === tipo;
              return (
                <TouchableOpacity
                  key={tipo}
                  style={[
                    styles.chip,
                    isSelected && { backgroundColor: cfg.color, borderColor: cfg.color },
                  ]}
                  onPress={() => setTipoSelecionado(tipo)}
                  activeOpacity={0.7}
                >
                  <Text
                    style={[
                      styles.chipText,
                      isSelected ? { color: '#03122e', fontWeight: '800' } : { color: COLORS.textSecondary },
                    ]}
                  >
                    {cfg.label}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>

          {/* Input de Texto Multiline */}
          <TextInput
            style={styles.textInput}
            placeholder="Ex: Cliente pediu entrega após 14h, portão lateral..."
            placeholderTextColor={COLORS.textMuted}
            value={conteudo}
            onChangeText={setConteudo}
            multiline
            numberOfLines={3}
            maxLength={2000}
          />

          {/* Botão Salvar com Gradiente */}
          <TouchableOpacity
            style={styles.btnWrapper}
            onPress={handleAdicionarNota}
            disabled={enviando || !conteudo.trim()}
            activeOpacity={0.85}
          >
            <LinearGradient
              colors={conteudo.trim() ? [COLORS.primaryContainer, COLORS.primary] : [COLORS.surfaceHighest, COLORS.surfaceHigh]}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 0 }}
              style={styles.btnSubmit}
            >
              {enviando ? (
                <ActivityIndicator size="small" color="#fff" />
              ) : (
                <Text
                  style={[
                    styles.btnSubmitText,
                    !conteudo.trim() && { color: COLORS.textMuted },
                  ]}
                >
                  ADICIONAR NOTA
                </Text>
              )}
            </LinearGradient>
          </TouchableOpacity>
        </View>
      ) : (
        <View style={styles.readOnlyBanner}>
          <Text style={styles.readOnlyText}>
            🔒 Modo de leitura: apenas vendedores e administradores podem adicionar notas operacionais.
          </Text>
        </View>
      )}

      {/* Lista de Notas */}
      {loading ? (
        <View style={styles.loadingWrap}>
          <ActivityIndicator size="small" color={COLORS.primary} />
          <Text style={styles.loadingText}>Carregando notas...</Text>
        </View>
      ) : todasNotas.length === 0 ? (
        <View style={styles.emptyCard}>
          <Text style={styles.emptyTitle}>Nenhuma nota registrada</Text>
          <Text style={styles.emptySubtitle}>
            {podeCriar
              ? 'Use o campo acima para adicionar instruções, restrições de entrega ou alertas operacionais.'
              : 'Nenhuma observação foi adicionada a esta entrega até o momento.'}
          </Text>
        </View>
      ) : (
        <View style={styles.listWrap}>
          {todasNotas.map(nota => {
            const cfg = TIPO_CONFIG[nota.tipo] || TIPO_CONFIG.GERAL;
            const cargo = nota.autorPapel === 'ADMIN' ? 'Administrador' : 'Vendedor';
            return (
              <View
                key={nota.id}
                style={[
                  styles.notaCard,
                  nota.isPendingSync && styles.notaCardPending,
                ]}
              >
                {/* Linha de topo: Tipo + Tempo / Pendente */}
                <View style={styles.notaHeader}>
                  <View style={[styles.tipoBadge, { backgroundColor: cfg.bg, borderColor: cfg.color }]}>
                    <Text style={[styles.tipoBadgeText, { color: cfg.color }]}>
                      {cfg.label}
                    </Text>
                  </View>

                  <View style={styles.notaHeaderRight}>
                    {nota.isPendingSync ? (
                      <View style={styles.pendingBadge}>
                        <Text style={styles.pendingBadgeText}>⏳ PENDENTE DE ENVIO</Text>
                      </View>
                    ) : (
                      <Text style={styles.tempoText}>{formatarTempoRelativo(nota.criadoEm)}</Text>
                    )}
                  </View>
                </View>

                {/* Conteúdo da Nota */}
                <Text style={styles.notaConteudo}>{nota.conteudo}</Text>

                {/* Rodapé: Autor e Cargo */}
                <View style={styles.notaFooter}>
                  <Text style={styles.autorText}>
                    {nota.autorNome} <Text style={styles.cargoText}>({cargo})</Text>
                  </Text>
                </View>
              </View>
            );
          })}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginTop: SPACING.md,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: SPACING.sm,
  },
  headerTitleWrap: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: SPACING.xs,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: COLORS.primary,
    letterSpacing: 0.5,
  },
  badgeCount: {
    backgroundColor: COLORS.surfaceHighest,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 10,
  },
  badgeCountText: {
    color: COLORS.textPrimary,
    fontSize: 11,
    fontWeight: '700',
  },
  refreshLink: {
    color: COLORS.primary,
    fontSize: 12,
    fontWeight: '600',
  },
  formCard: {
    backgroundColor: COLORS.surface,
    borderRadius: RADIUS.lg,
    padding: SPACING.md,
    marginBottom: SPACING.md,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  formLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: COLORS.textSecondary,
    marginBottom: SPACING.xs,
  },
  chipsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: SPACING.xs,
    marginBottom: SPACING.sm,
  },
  chip: {
    paddingVertical: 6,
    paddingHorizontal: 10,
    borderRadius: 4,
    borderWidth: 1,
    borderColor: COLORS.border,
    backgroundColor: COLORS.surfaceHigh,
  },
  chipText: {
    fontSize: 11,
    fontWeight: '700',
  },
  textInput: {
    backgroundColor: COLORS.surfaceLowest,
    borderColor: COLORS.border,
    borderWidth: 1,
    borderRadius: RADIUS.md,
    color: COLORS.textPrimary,
    padding: SPACING.sm,
    fontSize: 14,
    textAlignVertical: 'top',
    minHeight: 70,
    marginBottom: SPACING.sm,
  },
  btnWrapper: {
    borderRadius: RADIUS.md,
    overflow: 'hidden',
  },
  btnSubmit: {
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  btnSubmitText: {
    color: '#00297b',
    fontSize: 14,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  readOnlyBanner: {
    backgroundColor: COLORS.surfaceLow,
    borderColor: COLORS.border,
    borderWidth: 1,
    borderRadius: RADIUS.md,
    padding: SPACING.sm,
    marginBottom: SPACING.md,
  },
  readOnlyText: {
    color: COLORS.textMuted,
    fontSize: 12,
    lineHeight: 16,
  },
  loadingWrap: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: SPACING.sm,
    paddingVertical: SPACING.md,
  },
  loadingText: {
    color: COLORS.textMuted,
    fontSize: 13,
  },
  emptyCard: {
    backgroundColor: COLORS.surface,
    borderRadius: RADIUS.lg,
    padding: SPACING.lg,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: COLORS.border,
    marginBottom: SPACING.md,
  },
  emptyTitle: {
    color: COLORS.textSecondary,
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 4,
  },
  emptySubtitle: {
    color: COLORS.textMuted,
    fontSize: 12,
    textAlign: 'center',
    lineHeight: 16,
  },
  listWrap: {
    gap: SPACING.sm,
  },
  notaCard: {
    backgroundColor: COLORS.surface,
    borderRadius: RADIUS.md,
    padding: SPACING.md,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  notaCardPending: {
    borderColor: COLORS.warning,
    backgroundColor: '#F5A62308',
  },
  notaHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: SPACING.xs,
  },
  tipoBadge: {
    borderWidth: 1,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  tipoBadgeText: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  notaHeaderRight: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  pendingBadge: {
    backgroundColor: '#F5A62320',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  pendingBadgeText: {
    color: COLORS.warning,
    fontSize: 10,
    fontWeight: '800',
  },
  tempoText: {
    color: COLORS.textMuted,
    fontSize: 11,
  },
  notaConteudo: {
    color: COLORS.textPrimary,
    fontSize: 14,
    lineHeight: 20,
    marginVertical: SPACING.xs,
  },
  notaFooter: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    marginTop: 4,
    borderTopWidth: 1,
    borderTopColor: COLORS.surfaceHigh,
    paddingTop: 4,
  },
  autorText: {
    color: COLORS.textSecondary,
    fontSize: 11,
    fontWeight: '600',
  },
  cargoText: {
    color: COLORS.textMuted,
    fontWeight: '400',
  },
});
