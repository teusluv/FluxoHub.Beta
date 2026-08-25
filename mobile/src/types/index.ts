export type Papel = 'MOTORISTA' | 'VENDEDOR' | 'ADMIN';

export type StatusEntrega =
  | 'PENDENTE'
  | 'EM_ROTA'
  | 'ENTREGUE_SEM_CANHOTO'
  | 'ENTREGUE_COM_CANHOTO'
  | 'DIVERGENCIA';

export interface AuthState {
  accessToken: string;
  refreshToken: string;
  usuarioId: string;
  nome: string;
  email: string;
  papel: Papel;
  filialId: string;
  filialNome: string;
  accessExpiryMs: number;
}

export interface Entrega {
  id: string;
  filialId: string;
  filialNome: string;
  numeroNotaFiscal: string;
  chaveNfe: string | null;
  clienteNome: string;
  clienteDocumento: string | null;
  vendedorId: string | null;
  vendedorNome: string | null;
  motoristaId: string | null;
  motoristaNome: string | null;
  dataPrevistaEntrega: string | null;
  dataEntregaReal: string | null;
  status: StatusEntrega;
  latitude: number | null;
  longitude: number | null;
  observacoes: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// Fila de sync offline
export interface SyncItem {
  id: string;         // UUID local
  tipo: 'STATUS' | 'CANHOTO';
  entregaId: string;
  payload: object;
  tentativas: number;
  criadoEm: string;
}

// Canhoto retornado pelo backend
export interface Canhoto {
  id: string;
  entregaId: string;
  urlImagem: string;        // URL pré-assinada (15min de validade)
  textoOcrExtraido: string | null;
  confiancaOcr: number | null;
  necessitaRevisao: boolean;
  valido: boolean;
  motivoInvalidacao: string | null;
  capturadoEm: string;
  sincronizadoEm: string;
  deviceId: string;
}

