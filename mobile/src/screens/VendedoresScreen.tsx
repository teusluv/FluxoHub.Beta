import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  ActivityIndicator,
  TextInput,
  TouchableOpacity,
  Modal,
  Image,
  Dimensions
} from 'react-native';
import { COLORS, SPACING, RADIUS, FONT_SIZE } from '../constants/theme';
import { useAuth } from '../context/AuthContext';
import { apiGet } from '../services/api';
import { API_BASE_URL } from '../constants/api';

interface Entrega {
  id: string;
  numeroNotaFiscal: string;
  clienteNome: string;
  clienteDocumento: string;
  motoristaNome: string;
  status: string;
  dataPrevistaEntrega: string;
  dataEntregaReal: string | null;
}

export default function VendedoresScreen() {
  const { auth, logout } = useAuth();
  const [entregas, setEntregas] = useState<Entrega[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [search, setSearch] = useState('');
  
  // Canhoto Preview Modal
  const [previewEntrega, setPreviewEntrega] = useState<Entrega | null>(null);
  const [canhotoLoading, setCanhotoLoading] = useState(false);
  const [canhotoUrl, setCanhotoUrl] = useState<string | null>(null);

  useEffect(() => {
    carregarEntregas();
  }, []);

  async function carregarEntregas() {
    if (!auth?.usuarioId) return;
    setLoading(true);
    try {
      // Query deliveries assigned to this seller
      const data: any = await apiGet(`/api/v1/entregas?vendedorId=${auth.usuarioId}&size=50`);
      setEntregas(data.content || []);
    } catch (e) {
      console.warn('Erro ao carregar entregas:', e);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  function handleRefresh() {
    setRefreshing(true);
    carregarEntregas();
  }

  async function handleVisualizarCanhoto(entrega: Entrega) {
    setPreviewEntrega(entrega);
    setCanhotoLoading(true);
    setCanhotoUrl(null);
    try {
      // Fetch the delivery's canhotos
      const dataCanhotos: any = await apiGet(`/api/v1/canhotos/entrega/${entrega.id}`);
      if (!dataCanhotos || dataCanhotos.length === 0) {
        throw new Error('Nenhum canhoto anexado.');
      }
      
      const canhotoId = dataCanhotos[0].id;
      // Get signed URL
      const dataUrl: any = await apiGet(`/api/v1/canhotos/${canhotoId}`);
      setCanhotoUrl(dataUrl.urlImagem);
    } catch (e) {
      console.warn('Erro ao carregar canhoto:', e);
      alert('Não foi possível carregar o canhoto digital desta entrega.');
      setPreviewEntrega(null);
    } finally {
      setCanhotoLoading(false);
    }
  }

  const filteredEntregas = entregas.filter(e => 
    e.numeroNotaFiscal.toLowerCase().includes(search.toLowerCase()) ||
    e.clienteNome.toLowerCase().includes(search.toLowerCase())
  );

  const emTransitoCount = entregas.filter(e => e.status === 'EM_ROTA' || e.status === 'PENDENTE').length;

  return (
    <View style={styles.container}>
      {/* Topo / Header */}
      <View style={styles.header}>
        <View style={{ flex: 1 }}>
          <Text style={styles.headerTitle}>Painel do Vendedor</Text>
          <Text style={styles.headerSubtitle}>{auth?.filialNome || 'Filial Matriz'}</Text>
        </View>
        <TouchableOpacity onPress={logout} style={styles.logoutBtn}>
          <Text style={styles.logoutText}>SAIR</Text>
        </TouchableOpacity>
      </View>

      {/* Caixa Informativa */}
      <View style={styles.cardInfo}>
        <Text style={styles.infoTitle}>
          {emTransitoCount > 0 
            ? `Você tem ${emTransitoCount} pedido(s) em trânsito!`
            : 'Todos os seus pedidos foram processados!'}
        </Text>
        <Text style={styles.infoSubtitle}>
          Rastreie a rota, verifique o status de entrega e consulte os canhotos assinados abaixo.
        </Text>
      </View>

      {/* Barra de Pesquisa */}
      <View style={styles.searchContainer}>
        <TextInput
          style={styles.searchInput}
          placeholder="Buscar por Nota Fiscal ou Cliente..."
          placeholderTextColor={COLORS.textMuted}
          value={search}
          onChangeText={setSearch}
        />
      </View>

      {/* Fila de Entregas */}
      {loading && !refreshing ? (
        <ActivityIndicator size="large" color={COLORS.primary} style={{ marginTop: 40 }} />
      ) : (
        <FlatList
          data={filteredEntregas}
          keyExtractor={(item) => item.id}
          refreshing={refreshing}
          onRefresh={handleRefresh}
          ListEmptyComponent={
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyText}>Nenhuma entrega encontrada.</Text>
            </View>
          }
          renderItem={({ item }) => {
            let statusColor: string = COLORS.textMuted;
            let statusLabel = item.status;
            
            if (item.status === 'PENDENTE') {
              statusColor = COLORS.warning;
              statusLabel = 'AGUARDANDO';
            } else if (item.status === 'EM_ROTA') {
              statusColor = '#3498DB';
              statusLabel = 'EM ROTA';
            } else if (item.status === 'ENTREGUE_COM_CANHOTO') {
              statusColor = COLORS.success;
              statusLabel = 'ENTREGUE COM CANHOTO';
            } else if (item.status === 'ENTREGUE_SEM_CANHOTO') {
              statusColor = '#1ABC9C';
              statusLabel = 'ENTREGUE SEM CANHOTO';
            } else if (item.status === 'DIVERGENCIA') {
              statusColor = COLORS.danger;
              statusLabel = 'DIVERGÊNCIA';
            }

            const hasCanhoto = item.status === 'ENTREGUE_COM_CANHOTO';

            return (
              <View style={styles.listItem}>
                <View style={{ flex: 1 }}>
                  <View style={styles.listRow}>
                    <Text style={styles.nfText}>NF {item.numeroNotaFiscal}</Text>
                    <Text style={[styles.statusText, { color: statusColor }]}>{statusLabel}</Text>
                  </View>
                  <Text style={styles.clienteText}>{item.clienteNome}</Text>
                  <Text style={styles.motoristaText}>Motorista: {item.motoristaNome || 'Não atribuído'}</Text>
                </View>

                {hasCanhoto && (
                  <TouchableOpacity
                    style={styles.btnCanhoto}
                    onPress={() => handleVisualizarCanhoto(item)}
                  >
                    <Text style={styles.btnCanhotoText}>VER CANHOTO</Text>
                  </TouchableOpacity>
                )}
              </View>
            );
          }}
          contentContainerStyle={{ padding: SPACING.lg, paddingBottom: 80 }}
        />
      )}

      {/* MODAL: VIEW CANHOTO */}
      <Modal
        visible={previewEntrega !== null}
        transparent={true}
        animationType="fade"
        onRequestClose={() => setPreviewEntrega(null)}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>Canhoto NF {previewEntrega?.numeroNotaFiscal}</Text>
              <TouchableOpacity onPress={() => setPreviewEntrega(null)}>
                <Text style={styles.closeText}>Fechar</Text>
              </TouchableOpacity>
            </View>

            {canhotoLoading ? (
              <ActivityIndicator size="large" color={COLORS.primary} style={{ marginVertical: 40 }} />
            ) : canhotoUrl ? (
              <View style={{ alignItems: 'center' }}>
                <Image
                  source={{ uri: canhotoUrl }}
                  style={styles.canhotoImage}
                  resizeMode="contain"
                />
                <Text style={{ color: COLORS.textMuted, fontSize: 11, marginTop: 12 }}>
                  Métricas de auditoria verificadas por RLS.
                </Text>
              </View>
            ) : (
              <Text style={{ color: COLORS.danger, textAlign: 'center', marginVertical: 20 }}>
                Erro ao recuperar imagem.
              </Text>
            )}
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: COLORS.background },
  header: {
    backgroundColor: COLORS.surface,
    paddingTop: 60, paddingBottom: 20, paddingHorizontal: SPACING.lg,
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    borderBottomWidth: 1, borderBottomColor: COLORS.border,
  },
  headerTitle: { fontSize: 20, fontWeight: 'bold', color: COLORS.textPrimary },
  headerSubtitle: { fontSize: 13, color: COLORS.textSecondary, marginTop: 4 },
  logoutBtn: { padding: 8 },
  logoutText: { color: COLORS.danger, fontWeight: '900', fontSize: 13, letterSpacing: 0.5 },
  cardInfo: {
    margin: SPACING.lg, padding: SPACING.lg,
    backgroundColor: 'rgba(79, 124, 255, 0.1)', borderRadius: RADIUS.lg,
    borderWidth: 1, borderColor: '#4f7cff40',
  },
  infoTitle: { color: '#4f7cff', fontWeight: 'bold', fontSize: 15, marginBottom: 4 },
  infoSubtitle: { color: COLORS.textSecondary, fontSize: 12, lineHeight: 16 },
  
  searchContainer: { paddingHorizontal: SPACING.lg, marginBottom: SPACING.sm },
  searchInput: {
    height: 44,
    backgroundColor: COLORS.surface,
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: RADIUS.md,
    paddingHorizontal: SPACING.md,
    color: COLORS.textPrimary,
    fontSize: 14,
  },

  listItem: {
    backgroundColor: COLORS.surface,
    padding: SPACING.lg, borderRadius: RADIUS.md,
    marginBottom: SPACING.md, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    borderWidth: 1, borderColor: COLORS.border,
  },
  listRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  nfText: { color: COLORS.textPrimary, fontWeight: 'bold', fontSize: 16 },
  clienteText: { color: COLORS.textSecondary, fontSize: 13, marginTop: 4 },
  motoristaText: { color: COLORS.textMuted, fontSize: 11, marginTop: 4 },
  statusText: { fontWeight: '900', fontSize: 10, letterSpacing: 0.5 },
  
  btnCanhoto: {
    backgroundColor: 'rgba(79, 124, 255, 0.1)',
    borderWidth: 1,
    borderColor: '#4f7cff30',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: RADIUS.sm,
    marginLeft: 12,
  },
  btnCanhotoText: { color: '#4f7cff', fontSize: 10, fontWeight: 'bold' },

  emptyContainer: { alignItems: 'center', marginTop: 40 },
  emptyText: { color: COLORS.textMuted, fontSize: 14 },

  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.85)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalContent: {
    width: '90%',
    backgroundColor: COLORS.surface,
    borderRadius: RADIUS.lg,
    padding: 24,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  modalHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 },
  modalTitle: { fontSize: 16, fontWeight: 'bold', color: COLORS.textPrimary },
  closeText: { color: COLORS.textMuted, fontWeight: 'bold' },
  canhotoImage: {
    width: '100%',
    height: Dimensions.get('window').height * 0.45,
    backgroundColor: '#000',
    borderRadius: RADIUS.md,
  }
});
