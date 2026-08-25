'use client';

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';

interface Motorista {
  id: string;
  nome: string;
  email: string;
}

interface Entrega {
  id: string;
  numeroNotaFiscal: string;
  chaveNfe: string;
  clienteNome: string;
  clienteDocumento: string;
  vendedorNome: string;
  motoristaNome: string;
  dataPrevistaEntrega: string;
  dataEntregaReal: string | null;
  status: string;
  observacoes: string;
  criadoEm: string;
}

export default function DashboardPage() {
  const router = useRouter();
  
  // States
  const [entregas, setEntregas] = useState<Entrega[]>([]);
  const [motoristas, setMotoristas] = useState<Motorista[]>([]);
  const [selectedStatusFilter, setSelectedStatusFilter] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  
  // Loading & Error states
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  // Form modal state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [formLoading, setFormLoading] = useState(false);
  const [formError, setFormError] = useState('');
  const [formSuccess, setFormSuccess] = useState('');
  
  // Form fields
  const [numeroNotaFiscal, setNumeroNotaFiscal] = useState('');
  const [chaveNfe, setChaveNfe] = useState('');
  const [clienteNome, setClienteNome] = useState('');
  const [clienteDocumento, setClienteDocumento] = useState('');
  const [motoristaId, setMotoristaId] = useState('');
  const [dataPrevista, setDataPrevista] = useState('');
  const [observacoes, setObservacoes] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState('');

  // Canhoto Modal Viewer
  const [selectedEntregaForCanhoto, setSelectedEntregaForCanhoto] = useState<Entrega | null>(null);
  const [canhotoLoading, setCanhotoLoading] = useState(false);
  const [canhotoUrl, setCanhotoUrl] = useState<string | null>(null);
  const [canhotoInfo, setCanhotoInfo] = useState<any>(null);

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchQuery);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  // Fetch all data
  useEffect(() => {
    fetchData();
    fetchMotoristas();
  }, [debouncedSearch]);

  // Generate new idempotency key when opening form modal
  function openFormModal() {
    // Generate UUID v4
    const uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
      const r = Math.random() * 16 | 0;
      const v = c === 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
    setIdempotencyKey(uuid);
    
    // Default dataPrevista to today (YYYY-MM-DD)
    const today = new Date().toISOString().split('T')[0];
    setDataPrevista(today);
    
    // Clear form
    setNumeroNotaFiscal('');
    setChaveNfe('');
    setClienteNome('');
    setClienteDocumento('');
    setMotoristaId('');
    setObservacoes('');
    setFormError('');
    setFormSuccess('');
    
    setIsModalOpen(true);
  }

  async function fetchData() {
    setLoading(true);
    setError('');
    const token = localStorage.getItem('token');
    if (!token) {
      router.push('/login');
      return;
    }

    try {
      // Build filter params
      let url = 'http://localhost:8080/api/v1/entregas?size=100';
      if (debouncedSearch.trim()) {
        url += `&busca=${encodeURIComponent(debouncedSearch.trim())}`;
      }

      const res = await fetch(url, {
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (res.status === 401) {
        localStorage.clear();
        router.push('/login');
        return;
      }

      if (!res.ok) throw new Error('Não foi possível carregar as entregas.');

      const data = await res.json();
      setEntregas(data.content || []);
    } catch (err: any) {
      setError(err.message || 'Erro ao conectar ao servidor.');
    } finally {
      setLoading(false);
    }
  }

  async function fetchMotoristas() {
    const token = localStorage.getItem('token');
    if (!token) return;

    try {
      const res = await fetch('http://localhost:8080/api/v1/usuarios/motoristas', {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json();
        setMotoristas(data);
      }
    } catch (err) {
      console.error('Erro ao buscar motoristas:', err);
    }
  }

  // Handle new delivery vínculo submit
  async function handleCreateEntrega(e: React.FormEvent) {
    e.preventDefault();
    if (!numeroNotaFiscal.trim() || !clienteNome.trim() || !clienteDocumento.trim() || !motoristaId) {
      setFormError('Por favor, preencha todos os campos obrigatórios (*).');
      return;
    }

    if (chaveNfe && !/^\d{44}$/.test(chaveNfe)) {
      setFormError('A chave NFe deve conter exatamente 44 dígitos numéricos.');
      return;
    }

    setFormLoading(true);
    setFormError('');
    setFormSuccess('');

    const token = localStorage.getItem('token');
    const usuarioId = localStorage.getItem('usuarioId');

    const payload = {
      numeroNotaFiscal: numeroNotaFiscal.trim(),
      chaveNfe: chaveNfe.trim() || null,
      clienteNome: clienteNome.trim(),
      clienteDocumento: clienteDocumento.trim(),
      vendedorId: usuarioId,
      motoristaId: motoristaId,
      dataPrevistaEntrega: dataPrevista || new Date().toISOString().split('T')[0],
      observacoes: observacoes.trim() || null,
      idempotencyKey: idempotencyKey
    };

    try {
      const res = await fetch('http://localhost:8080/api/v1/entregas', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      });

      if (res.status === 409) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.detail || 'Esta Nota Fiscal já está vinculada a uma entrega ativa.');
      }

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.detail || 'Não foi possível cadastrar a entrega.');
      }

      setFormSuccess('Nota fiscal vinculada com sucesso ao motorista!');
      setTimeout(() => {
        setIsModalOpen(false);
        fetchData();
      }, 1500);
    } catch (err: any) {
      setFormError(err.message || 'Falha ao conectar.');
    } finally {
      setFormLoading(false);
    }
  }

  // View Canhoto Handler
  async function handleViewCanhoto(entrega: Entrega) {
    setSelectedEntregaForCanhoto(entrega);
    setCanhotoLoading(true);
    setCanhotoUrl(null);
    setCanhotoInfo(null);

    const token = localStorage.getItem('token');
    try {
      // 1. Get canhotos for this delivery
      const resCanhotos = await fetch(`http://localhost:8080/api/v1/canhotos/entrega/${entrega.id}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!resCanhotos.ok) throw new Error('Não foi possível obter informações do canhoto.');
      const dataCanhotos = await resCanhotos.json();
      if (!dataCanhotos || dataCanhotos.length === 0) {
        throw new Error('Nenhum canhoto carregado para esta entrega.');
      }
      
      const mainCanhoto = dataCanhotos[0];
      setCanhotoInfo(mainCanhoto);

      // 2. Get Signed URL to download/view the photo safely
      const resUrl = await fetch(`http://localhost:8080/api/v1/canhotos/${mainCanhoto.id}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!resUrl.ok) throw new Error('Erro ao assinar URL do arquivo.');
      const dataUrl = await resUrl.json();
      setCanhotoUrl(dataUrl.urlImagem); // This holds the presigned URL
    } catch (err: any) {
      console.error(err);
      alert(err.message || 'Erro ao carregar comprovante.');
      setSelectedEntregaForCanhoto(null);
    } finally {
      setCanhotoLoading(false);
    }
  }

  // KPI Calculations
  const totalCount = entregas.length;
  const pendentesCount = entregas.filter(e => e.status === 'PENDENTE').length;
  const emRotaCount = entregas.filter(e => e.status === 'EM_ROTA').length;
  const entreguesCount = entregas.filter(e => e.status === 'ENTREGUE_COM_CANHOTO' || e.status === 'ENTREGUE_SEM_CANHOTO').length;
  const divergentesCount = entregas.filter(e => e.status === 'DIVERGENCIA').length;

  const filteredEntregas = entregas.filter(e => {
    if (!selectedStatusFilter) return true;
    if (selectedStatusFilter === 'ENTREGUE') {
      return e.status === 'ENTREGUE_COM_CANHOTO' || e.status === 'ENTREGUE_SEM_CANHOTO';
    }
    return e.status === selectedStatusFilter;
  });

  return (
    <div style={{ fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 32 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
            <div style={{
              width: 4,
              height: 28,
              background: 'linear-gradient(135deg, #4f7cff 0%, #8b5cf6 100%)',
              borderRadius: 2
            }} />
            <h1 style={{ fontSize: 28, fontWeight: 900, color: '#FFFFFF', letterSpacing: '-0.5px', margin: 0 }}>Dashboard Logístico</h1>
          </div>
          <p style={{ fontSize: 14, color: '#9CA3AF', margin: 0 }}>
            Gerencie vínculos de notas fiscais e acompanhe canhotos em tempo real.
          </p>
        </div>

        <button
          onClick={openFormModal}
          style={{
            background: 'linear-gradient(135deg, #4f7cff 0%, #8b5cf6 100%)',
            border: 'none',
            borderRadius: 8,
            padding: '12px 24px',
            color: '#FFFFFF',
            fontWeight: 700,
            fontSize: 14,
            cursor: 'pointer',
            boxShadow: '0 4px 12px rgba(79, 124, 255, 0.25)',
          }}
        >
          + ATRIBUIR NOTA FISCAL
        </button>
      </div>

      {/* KPIs */}
      <div style={{ display: 'flex', gap: 20, marginBottom: 32, flexWrap: 'wrap' }}>
        {[
          { label: 'TODAS ENTREGAS', count: totalCount, filter: null, color: '#9CA3AF' },
          { label: 'PENDENTES', count: pendentesCount, filter: 'PENDENTE', color: '#F5A623' },
          { label: 'EM ROTA', count: emRotaCount, filter: 'EM_ROTA', color: '#3498DB' },
          { label: 'ENTREGUES (COM/SEM)', count: entreguesCount, filter: 'ENTREGUE', color: '#2ECC71' },
          { label: 'DIVERGÊNCIAS', count: divergentesCount, filter: 'DIVERGENCIA', color: '#E74C3C' }
        ].map((kpi, idx) => {
          const active = selectedStatusFilter === kpi.filter;
          return (
            <div
              key={idx}
              onClick={() => setSelectedStatusFilter(kpi.filter)}
              style={{
                backgroundColor: '#1E2022',
                border: active ? `2px solid ${kpi.color}` : '1px solid #2D3035',
                borderRadius: 12,
                padding: '20px 24px',
                flex: 1,
                minWidth: 160,
                cursor: 'pointer',
                transition: 'all 0.15s',
                boxShadow: active ? `0 4px 16px ${kpi.color}15` : 'none',
              }}
            >
              <p style={{ fontSize: 11, fontWeight: 700, color: '#9CA3AF', letterSpacing: '0.5px', margin: '0 0 8px 0' }}>
                {kpi.label}
              </p>
              <p style={{ fontSize: 32, fontWeight: 900, color: kpi.color, margin: 0, lineHeight: 1 }}>
                {kpi.count}
              </p>
            </div>
          );
        })}
      </div>

      {/* Main Area */}
      <div style={{ backgroundColor: '#1E2022', border: '1px solid #2D3035', borderRadius: 12, padding: 24 }}>
        {/* Table Filter / Search bar */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, gap: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 3, height: 16, backgroundColor: '#4f7cff', borderRadius: 1 }} />
            <h2 style={{ fontSize: 16, fontWeight: 700, color: '#FFFFFF', margin: 0 }}>Entregas da Filial</h2>
          </div>

          <div style={{ width: 320 }}>
            <input
              type="text"
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              placeholder="Buscar por Nota Fiscal ou Cliente..."
              style={{
                width: '100%',
                height: 40,
                backgroundColor: '#27292D',
                border: '1px solid #373A40',
                borderRadius: 8,
                paddingLeft: 12,
                paddingRight: 12,
                fontSize: 14,
                color: '#FFFFFF',
                outline: 'none',
              }}
            />
          </div>
        </div>

        {/* Loading and Empty states */}
        {loading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '60px 0', color: '#9CA3AF' }}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ marginBottom: 12, fontSize: 24 }}>🔄</div>
              <div>Carregando fila de entregas...</div>
            </div>
          </div>
        ) : error ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '40px 0', color: '#EF4444' }}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ marginBottom: 12, fontSize: 24 }}>⚠️</div>
              <div>{error}</div>
              <button onClick={fetchData} style={{ marginTop: 12, padding: '8px 16px', backgroundColor: '#27292D', border: '1px solid #373A40', borderRadius: 6, color: '#FFF', cursor: 'pointer' }}>Tentar Novamente</button>
            </div>
          </div>
        ) : filteredEntregas.length === 0 ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '80px 0', color: '#9CA3AF' }}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 36, marginBottom: 12 }}>📦</div>
              <div style={{ fontWeight: 600, color: '#FFFFFF' }}>Nenhuma entrega encontrada</div>
              <div style={{ fontSize: 13, marginTop: 4 }}>Não há registros pendentes com os filtros atuais.</div>
            </div>
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid #2D3035' }}>
                  {['Nota Fiscal', 'Cliente', 'Motorista', 'Previsão', 'Status', 'Ações'].map(h => (
                    <th key={h} style={{ textAlign: 'left', padding: '12px 16px', fontSize: 11, fontWeight: 700, color: '#9CA3AF', letterSpacing: '0.5px' }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filteredEntregas.map((entrega) => {
                  let statusColor = '#9CA3AF';
                  let statusBg = 'rgba(156, 163, 175, 0.1)';
                  
                  if (entrega.status === 'PENDENTE') {
                    statusColor = '#F5A623';
                    statusBg = 'rgba(245, 166, 35, 0.1)';
                  } else if (entrega.status === 'EM_ROTA') {
                    statusColor = '#3498DB';
                    statusBg = 'rgba(52, 152, 219, 0.1)';
                  } else if (entrega.status === 'ENTREGUE_COM_CANHOTO') {
                    statusColor = '#2ECC71';
                    statusBg = 'rgba(46, 204, 113, 0.1)';
                  } else if (entrega.status === 'ENTREGUE_SEM_CANHOTO') {
                    statusColor = '#1ABC9C';
                    statusBg = 'rgba(26, 188, 156, 0.1)';
                  } else if (entrega.status === 'DIVERGENCIA') {
                    statusColor = '#E74C3C';
                    statusBg = 'rgba(231, 76, 60, 0.1)';
                  }

                  const hasCanhoto = entrega.status === 'ENTREGUE_COM_CANHOTO';

                  return (
                    <tr key={entrega.id} style={{ borderBottom: '1px solid #2D3035', transition: 'background-color 0.15s' }}>
                      <td style={{ padding: '16px', fontSize: 15, fontWeight: 800, color: '#FFFFFF', letterSpacing: '-0.5px' }}>
                        {entrega.numeroNotaFiscal}
                      </td>
                      <td style={{ padding: '16px', fontSize: 14, color: '#E5E7EB' }}>
                        <div>{entrega.clienteNome}</div>
                        <div style={{ fontSize: 11, color: '#9CA3AF', marginTop: 2 }}>Doc: {entrega.clienteDocumento}</div>
                      </td>
                      <td style={{ padding: '16px', fontSize: 14, color: '#E5E7EB' }}>
                        {entrega.motoristaNome}
                      </td>
                      <td style={{ padding: '16px', fontSize: 13, color: '#E5E7EB' }}>
                        {new Date(entrega.dataPrevistaEntrega + 'T00:00:00').toLocaleDateString('pt-BR')}
                      </td>
                      <td style={{ padding: '16px' }}>
                        <span style={{
                          border: `1px solid ${statusColor}40`,
                          borderRadius: 4,
                          padding: '4px 10px',
                          fontSize: 10,
                          fontWeight: 800,
                          color: statusColor,
                          letterSpacing: '0.5px',
                          backgroundColor: statusBg,
                          display: 'inline-block',
                        }}>
                          {entrega.status.replace(/_/g, ' ')}
                        </span>
                      </td>
                      <td style={{ padding: '16px' }}>
                        {hasCanhoto ? (
                          <button
                            onClick={() => handleViewCanhoto(entrega)}
                            style={{
                              backgroundColor: 'rgba(79, 124, 255, 0.1)',
                              border: '1px solid rgba(79, 124, 255, 0.2)',
                              borderRadius: 6,
                              padding: '6px 12px',
                              color: '#4f7cff',
                              fontWeight: 700,
                              fontSize: 12,
                              cursor: 'pointer',
                            }}
                          >
                            👁 Ver Canhoto
                          </button>
                        ) : (
                          <span style={{ fontSize: 12, color: '#6B7280' }}>Sem comprovante</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* MODAL: NOVA ATRIBUIÇÃO DE NOTA FISCAL */}
      {isModalOpen && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.75)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000,
        }}>
          <div style={{
            width: '100%',
            maxWidth: 500,
            backgroundColor: '#1E2022',
            border: '1px solid #2D3035',
            borderRadius: 16,
            padding: 32,
            boxShadow: '0 12px 32px rgba(0,0,0,0.5)',
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
              <h2 style={{ fontSize: 20, fontWeight: 900, color: '#FFFFFF', margin: 0 }}>Atribuir Nova Nota Fiscal</h2>
              <button onClick={() => setIsModalOpen(false)} style={{ background: 'none', border: 'none', color: '#9CA3AF', fontSize: 20, cursor: 'pointer' }}>×</button>
            </div>

            {formError && (
              <div style={{ backgroundColor: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.2)', borderRadius: 8, padding: 12, color: '#EF4444', fontSize: 13, marginBottom: 16 }}>
                {formError}
              </div>
            )}

            {formSuccess && (
              <div style={{ backgroundColor: 'rgba(46, 204, 113, 0.1)', border: '1px solid rgba(46, 204, 113, 0.2)', borderRadius: 8, padding: 12, color: '#2ECC71', fontSize: 13, marginBottom: 16 }}>
                {formSuccess}
              </div>
            )}

            <form onSubmit={handleCreateEntrega} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ display: 'flex', gap: 16 }}>
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <label style={{ fontSize: 10, fontWeight: 700, color: '#9CA3AF', letterSpacing: '0.5px' }}>Nº DA NF *</label>
                  <input
                    type="text"
                    value={numeroNotaFiscal}
                    onChange={e => setNumeroNotaFiscal(e.target.value)}
                    placeholder="Ex: 10452"
                    required
                    style={{ height: 40, backgroundColor: '#27292D', border: '1px solid #373A40', borderRadius: 8, paddingLeft: 12, paddingRight: 12, color: '#FFF', fontSize: 14 }}
                  />
                </div>
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <label style={{ fontSize: 10, fontWeight: 700, color: '#9CA3AF', letterSpacing: '0.5px' }}>PREVISÃO DE ENTREGA *</label>
                  <input
                    type="date"
                    value={dataPrevista}
                    onChange={e => setDataPrevista(e.target.value)}
                    required
                    style={{ height: 40, backgroundColor: '#27292D', border: '1px solid #373A40', borderRadius: 8, paddingLeft: 12, paddingRight: 12, color: '#FFF', fontSize: 14 }}
                  />
                </div>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <label style={{ fontSize: 10, fontWeight: 700, color: '#9CA3AF', letterSpacing: '0.5px' }}>CHAVE NFE (OPCIONAL - 44 DÍGITOS)</label>
                <input
                  type="text"
                  maxLength={44}
                  value={chaveNfe}
                  onChange={e => setChaveNfe(e.target.value.replace(/\D/g, ''))}
                  placeholder="00000000000000000000000000000000000000000000"
                  style={{ height: 40, backgroundColor: '#27292D', border: '1px solid #373A40', borderRadius: 8, paddingLeft: 12, paddingRight: 12, color: '#FFF', fontSize: 13 }}
                />
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <label style={{ fontSize: 10, fontWeight: 700, color: '#9CA3AF', letterSpacing: '0.5px' }}>CLIENTE DESTINATÁRIO *</label>
                <input
                  type="text"
                  value={clienteNome}
                  onChange={e => setClienteNome(e.target.value)}
                  placeholder="Nome do cliente/estabelecimento"
                  required
                  style={{ height: 40, backgroundColor: '#27292D', border: '1px solid #373A40', borderRadius: 8, paddingLeft: 12, paddingRight: 12, color: '#FFF', fontSize: 14 }}
                />
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <label style={{ fontSize: 10, fontWeight: 700, color: '#9CA3AF', letterSpacing: '0.5px' }}>CPF/CNPJ CLIENTE *</label>
                <input
                  type="text"
                  value={clienteDocumento}
                  onChange={e => setClienteDocumento(e.target.value)}
                  placeholder="Apenas números"
                  required
                  style={{ height: 40, backgroundColor: '#27292D', border: '1px solid #373A40', borderRadius: 8, paddingLeft: 12, paddingRight: 12, color: '#FFF', fontSize: 14 }}
                />
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <label style={{ fontSize: 10, fontWeight: 700, color: '#9CA3AF', letterSpacing: '0.5px' }}>MOTORISTA RESPONSÁVEL *</label>
                <select
                  value={motoristaId}
                  onChange={e => setMotoristaId(e.target.value)}
                  required
                  style={{ height: 40, backgroundColor: '#27292D', border: '1px solid #373A40', borderRadius: 8, paddingLeft: 12, paddingRight: 12, color: '#FFF', fontSize: 14 }}
                >
                  <option value="">Selecione o motorista...</option>
                  {motoristas.map(m => (
                    <option key={m.id} value={m.id}>{m.nome}</option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <label style={{ fontSize: 10, fontWeight: 700, color: '#9CA3AF', letterSpacing: '0.5px' }}>OBSERVAÇÕES</label>
                <textarea
                  value={observacoes}
                  onChange={e => setObservacoes(e.target.value)}
                  placeholder="Ex: Cuidado com mercadoria frágil"
                  style={{ height: 60, backgroundColor: '#27292D', border: '1px solid #373A40', borderRadius: 8, padding: 12, color: '#FFF', fontSize: 14, resize: 'none' }}
                />
              </div>

              <div style={{ display: 'flex', gap: 12, marginTop: 12 }}>
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  style={{ flex: 1, height: 40, backgroundColor: 'transparent', border: '1px solid #373A40', borderRadius: 8, color: '#FFF', cursor: 'pointer', fontWeight: 600 }}
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={formLoading}
                  style={{
                    flex: 1,
                    height: 40,
                    background: 'linear-gradient(135deg, #4f7cff 0%, #8b5cf6 100%)',
                    border: 'none',
                    borderRadius: 8,
                    color: '#FFF',
                    fontWeight: 700,
                    cursor: 'pointer'
                  }}
                >
                  {formLoading ? 'Enviando...' : 'Confirmar Envio'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: CANHOTO VIEWER */}
      {selectedEntregaForCanhoto && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.85)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000,
        }}>
          <div style={{
            width: '90%',
            maxWidth: 600,
            backgroundColor: '#1E2022',
            border: '1px solid #2D3035',
            borderRadius: 16,
            padding: 24,
            boxShadow: '0 12px 32px rgba(0,0,0,0.5)',
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <div>
                <h2 style={{ fontSize: 18, fontWeight: 900, color: '#FFFFFF', margin: 0 }}>
                  Canhoto NF {selectedEntregaForCanhoto.numeroNotaFiscal}
                </h2>
                <p style={{ fontSize: 13, color: '#9CA3AF', margin: '4px 0 0 0' }}>
                  Cliente: {selectedEntregaForCanhoto.clienteNome}
                </p>
              </div>
              <button
                onClick={() => setSelectedEntregaForCanhoto(null)}
                style={{ background: 'none', border: 'none', color: '#9CA3AF', fontSize: 24, cursor: 'pointer' }}
              >
                ×
              </button>
            </div>

            {canhotoLoading ? (
              <div style={{ display: 'flex', justifyContent: 'center', padding: '60px 0', color: '#9CA3AF' }}>
                Carregando comprovante...
              </div>
            ) : canhotoUrl ? (
              <div>
                <div style={{
                  position: 'relative',
                  width: '100%',
                  height: 350,
                  backgroundColor: '#0F1011',
                  borderRadius: 8,
                  overflow: 'hidden',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  marginBottom: 16,
                  border: '1px solid #2D3035'
                }}>
                  <img
                    src={canhotoUrl}
                    alt="Canhoto"
                    style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
                  />
                </div>

                <div style={{ backgroundColor: '#27292D', borderRadius: 8, padding: 16, fontSize: 13, color: '#E5E7EB', lineHeight: 1.6 }}>
                  <div style={{ fontWeight: 700, color: '#FFFFFF', marginBottom: 8 }}>Métricas de Auditoria:</div>
                  <div>• <b>Data de Envio:</b> {canhotoInfo?.sincronizadoEm ? new Date(canhotoInfo.sincronizadoEm).toLocaleString('pt-BR') : 'N/D'}</div>
                  <div>• <b>Hora Captura (device):</b> {canhotoInfo?.capturadoEm ? new Date(canhotoInfo.capturadoEm).toLocaleString('pt-BR') : 'N/D'}</div>
                  <div>• <b>Device ID:</b> {canhotoInfo?.deviceId || 'N/D'}</div>
                  {canhotoInfo?.confiancaOcr && (
                    <div>• <b>Confiança OCR:</b> {(canhotoInfo.confiancaOcr * 100).toFixed(1)}%</div>
                  )}
                </div>

                <div style={{ display: 'flex', gap: 12, marginTop: 20 }}>
                  <a
                    href={canhotoUrl}
                    target="_blank"
                    rel="noreferrer"
                    style={{
                      flex: 1,
                      height: 40,
                      backgroundColor: 'transparent',
                      border: '1px solid #4f7cff',
                      borderRadius: 8,
                      color: '#4f7cff',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 700,
                      fontSize: 13,
                      textDecoration: 'none',
                    }}
                  >
                    Ver Tamanho Cheio
                  </a>
                  <button
                    onClick={() => setSelectedEntregaForCanhoto(null)}
                    style={{
                      flex: 1,
                      height: 40,
                      background: 'linear-gradient(135deg, #4f7cff 0%, #8b5cf6 100%)',
                      border: 'none',
                      borderRadius: 8,
                      color: '#FFF',
                      fontWeight: 700,
                      cursor: 'pointer'
                    }}
                  >
                    Fechar
                  </button>
                </div>
              </div>
            ) : (
              <div style={{ color: '#EF4444', textAlign: 'center', padding: '20px 0' }}>
                Erro ao carregar o arquivo do comprovante.
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
