'use client';

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';

export interface Canhoto {
  id: string;
  entregaId: string;
  numeroNotaFiscal: string;
  clienteNome: string;
  motoristaNome: string;
  urlImagem: string; // This holds the relative storage key or signed URL
  sincronizadoEm: string;
  capturadoEm: string;
  deviceId: string;
  confiancaOcr: number | null;
  valido: boolean;
}

interface Entrega {
  id: string;
  numeroNotaFiscal: string;
  clienteNome: string;
  clienteDocumento: string;
  motoristaNome: string;
  status: string;
  dataEntregaReal: string | null;
}

export default function CanhotosPage() {
  const router = useRouter();
  const [entregas, setEntregas] = useState<Entrega[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Canhoto Modal
  const [selectedEntrega, setSelectedEntrega] = useState<Entrega | null>(null);
  const [canhotoLoading, setCanhotoLoading] = useState(false);
  const [canhotoInfo, setCanhotoInfo] = useState<any>(null);
  const [canhotoUrl, setCanhotoUrl] = useState<string | null>(null);
  const [rotation, setRotation] = useState(0);

  // Debounce search
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchQuery);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  // Fetch only delivered/problematic deliveries that might have canhotos
  useEffect(() => {
    fetchEntregas();
  }, [debouncedSearch]);

  async function fetchEntregas() {
    setLoading(true);
    setError('');
    const token = localStorage.getItem('token');
    if (!token) {
      router.push('/login');
      return;
    }

    try {
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
      // Filter only those with canhotos or entregues to facilitate slip searching
      const list = data.content || [];
      setEntregas(list);
    } catch (err: any) {
      setError(err.message || 'Erro de conexão.');
    } finally {
      setLoading(false);
    }
  }

  async function handleOpenCanhoto(entrega: Entrega) {
    setSelectedEntrega(entrega);
    setCanhotoLoading(true);
    setCanhotoUrl(null);
    setCanhotoInfo(null);
    setRotation(0); // Reset rotation

    const token = localStorage.getItem('token');
    try {
      // 1. Get canhotos for this delivery
      const resCanhotos = await fetch(`http://localhost:8080/api/v1/canhotos/entrega/${entrega.id}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!resCanhotos.ok) throw new Error('Erro ao obter dados do canhoto.');
      const dataCanhotos = await resCanhotos.json();
      if (!dataCanhotos || dataCanhotos.length === 0) {
        throw new Error('Nenhum canhoto anexado a esta entrega.');
      }
      
      const canhoto = dataCanhotos[0];
      setCanhotoInfo(canhoto);

      // 2. Request Signed URL
      const resUrl = await fetch(`http://localhost:8080/api/v1/canhotos/${canhoto.id}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!resUrl.ok) throw new Error('Erro ao assinar chave do MinIO/S3.');
      const dataUrl = await resUrl.json();
      setCanhotoUrl(dataUrl.urlImagem);
    } catch (err: any) {
      alert(err.message || 'Erro ao carregar.');
      setSelectedEntrega(null);
    } finally {
      setCanhotoLoading(false);
    }
  }

  function rotateImage() {
    setRotation(r => (r + 90) % 360);
  }

  return (
    <div style={{ fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' }}>
      {/* Header */}
      <div style={{ marginBottom: 32 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
          <div style={{
            width: 4,
            height: 28,
            background: 'linear-gradient(135deg, #4f7cff 0%, #8b5cf6 100%)',
            borderRadius: 2
          }} />
          <h1 style={{ fontSize: 28, fontWeight: 900, color: '#FFFFFF', letterSpacing: '-0.5px', margin: 0 }}>
            Consulta de Canhotos
          </h1>
        </div>
        <p style={{ fontSize: 14, color: '#9CA3AF', margin: 0 }}>
          Busque por Notas Fiscais e verifique a validade jurídica dos comprovantes de entrega.
        </p>
      </div>

      {/* Main Search Block */}
      <div style={{ backgroundColor: '#1E2022', border: '1px solid #2D3035', borderRadius: 12, padding: 24 }}>
        <div style={{ marginBottom: 20 }}>
          <input
            type="text"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            placeholder="Digite o número da Nota Fiscal ou nome do cliente..."
            style={{
              width: '100%',
              height: 48,
              backgroundColor: '#27292D',
              border: '1px solid #373A40',
              borderRadius: 8,
              paddingLeft: 16,
              paddingRight: 16,
              fontSize: 15,
              color: '#FFFFFF',
              outline: 'none',
            }}
          />
        </div>

        {loading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '60px 0', color: '#9CA3AF' }}>
            Buscando entregas e comprovantes...
          </div>
        ) : error ? (
          <div style={{ color: '#EF4444', textAlign: 'center', padding: '20px 0' }}>{error}</div>
        ) : entregas.length === 0 ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '60px 0', color: '#9CA3AF' }}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 32, marginBottom: 12 }}>🔍</div>
              <div>Nenhum canhoto localizado para os termos informados.</div>
            </div>
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid #2D3035' }}>
                  {['Nota Fiscal', 'Cliente', 'Motorista', 'Data Entrega', 'Status', 'Canhoto'].map(h => (
                    <th key={h} style={{ textAlign: 'left', padding: '12px 16px', fontSize: 11, fontWeight: 700, color: '#9CA3AF', letterSpacing: '0.5px' }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {entregas.map(e => {
                  const hasCanhoto = e.status === 'ENTREGUE_COM_CANHOTO';
                  return (
                    <tr key={e.id} style={{ borderBottom: '1px solid #2D3035' }}>
                      <td style={{ padding: '16px', fontSize: 15, fontWeight: 800, color: '#FFFFFF', letterSpacing: '-0.5px' }}>
                        {e.numeroNotaFiscal}
                      </td>
                      <td style={{ padding: '16px', fontSize: 14, color: '#E5E7EB' }}>
                        {e.clienteNome}
                      </td>
                      <td style={{ padding: '16px', fontSize: 14, color: '#E5E7EB' }}>
                        {e.motoristaNome}
                      </td>
                      <td style={{ padding: '16px', fontSize: 13, color: '#E5E7EB' }}>
                        {e.dataEntregaReal ? new Date(e.dataEntregaReal).toLocaleString('pt-BR') : 'Sem data'}
                      </td>
                      <td style={{ padding: '16px' }}>
                        <span style={{
                          border: `1px solid ${hasCanhoto ? '#2ECC7140' : '#E74C3C40'}`,
                          borderRadius: 4,
                          padding: '4px 10px',
                          fontSize: 10,
                          fontWeight: 800,
                          color: hasCanhoto ? '#2ECC71' : '#E74C3C',
                          backgroundColor: hasCanhoto ? 'rgba(46, 204, 113, 0.1)' : 'rgba(231, 76, 60, 0.1)',
                        }}>
                          {hasCanhoto ? 'COM CANHOTO' : 'SEM CANHOTO'}
                        </span>
                      </td>
                      <td style={{ padding: '16px' }}>
                        {hasCanhoto ? (
                          <button
                            onClick={() => handleOpenCanhoto(e)}
                            style={{
                              backgroundColor: 'linear-gradient(135deg, #4f7cff 0%, #8b5cf6 100%)',
                              background: '#4f7cff',
                              border: 'none',
                              borderRadius: 6,
                              padding: '6px 14px',
                              color: '#FFFFFF',
                              fontWeight: 750,
                              fontSize: 12,
                              cursor: 'pointer',
                            }}
                          >
                            Visualizar
                          </button>
                        ) : (
                          <span style={{ fontSize: 12, color: '#6B7280' }}>Não disponível</span>
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

      {/* MODAL: CANHOTO VIEWER COM ZOOM, ROTAÇÃO E DOWNLOAD */}
      {selectedEntrega && (
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
            maxWidth: 650,
            backgroundColor: '#1E2022',
            border: '1px solid #2D3035',
            borderRadius: 16,
            padding: 28,
            boxShadow: '0 12px 32px rgba(0,0,0,0.5)',
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <div>
                <h2 style={{ fontSize: 18, fontWeight: 900, color: '#FFFFFF', margin: 0 }}>
                  Comprovante Digital — NF {selectedEntrega.numeroNotaFiscal}
                </h2>
                <p style={{ fontSize: 13, color: '#9CA3AF', margin: '4px 0 0 0' }}>
                  Destinatário: {selectedEntrega.clienteNome}
                </p>
              </div>
              <button
                onClick={() => setSelectedEntrega(null)}
                style={{ background: 'none', border: 'none', color: '#9CA3AF', fontSize: 24, cursor: 'pointer' }}
              >
                ×
              </button>
            </div>

            {canhotoLoading ? (
              <div style={{ display: 'flex', justifyContent: 'center', padding: '60px 0', color: '#9CA3AF' }}>
                Buscando canhoto assinado do MinIO...
              </div>
            ) : canhotoUrl ? (
              <div>
                {/* Image panel with rotation */}
                <div style={{
                  position: 'relative',
                  width: '100%',
                  height: 380,
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
                    style={{
                      maxWidth: '100%',
                      maxHeight: '100%',
                      objectFit: 'contain',
                      transform: `rotate(${rotation}deg)`,
                      transition: 'transform 0.2s',
                    }}
                  />
                  
                  {/* Rotate HUD Button */}
                  <button
                    onClick={rotateImage}
                    style={{
                      position: 'absolute',
                      bottom: 12,
                      right: 12,
                      backgroundColor: 'rgba(0, 0, 0, 0.7)',
                      border: '1px solid #373A40',
                      borderRadius: 6,
                      color: '#FFF',
                      padding: '6px 12px',
                      fontSize: 12,
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                  >
                    🔄 Rotacionar (90°)
                  </button>
                </div>

                <div style={{ backgroundColor: '#27292D', borderRadius: 8, padding: 16, fontSize: 13, color: '#E5E7EB', lineHeight: 1.6 }}>
                  <div style={{ fontWeight: 700, color: '#FFFFFF', marginBottom: 8 }}>Histórico Jurídico / Auditoria:</div>
                  <div>• <b>Data/Hora do Dispositivo:</b> {canhotoInfo?.capturadoEm ? new Date(canhotoInfo.capturadoEm).toLocaleString('pt-BR') : 'N/D'}</div>
                  <div>• <b>Data/Hora do Recebimento:</b> {canhotoInfo?.sincronizadoEm ? new Date(canhotoInfo.sincronizadoEm).toLocaleString('pt-BR') : 'N/D'}</div>
                  <div>• <b>Identificador do Dispositivo (Device):</b> <span style={{ fontFamily: 'monospace' }}>{canhotoInfo?.deviceId || 'N/D'}</span></div>
                  {canhotoInfo?.confiancaOcr && (
                    <div>• <b>Confiança OCR:</b> {(canhotoInfo.confiancaOcr * 100).toFixed(1)}%</div>
                  )}
                </div>

                <div style={{ display: 'flex', gap: 12, marginTop: 24 }}>
                  <a
                    href={canhotoUrl}
                    download={`canhoto_nf_${selectedEntrega.numeroNotaFiscal}.jpg`}
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
                    📥 Baixar Comprovante
                  </a>
                  <button
                    onClick={() => setSelectedEntrega(null)}
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
                Falha ao obter imagem do MinIO.
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
