'use client';
import { C } from '@/lib/tokens';

function Selo({ label, cor }: { label: string; cor: string }) {
  return (
    <span style={{
      border: `1.5px solid ${cor}`, borderRadius: 3,
      padding: '3px 8px', fontSize: 10, fontWeight: 800,
      color: cor, letterSpacing: 0.5, backgroundColor: `${cor}12`,
    }}>
      {label}
    </span>
  );
}

const motoristas = [
  { nome: 'João Carlos',    confirmadas: 45, status: 'EM ROTA',   corStatus: '#2ECC71' },
  { nome: 'Pedro Silva',    confirmadas: 38, status: 'OFFLINE',   corStatus: '#607080' },
  { nome: 'Marcos Antônio', confirmadas: 35, status: 'EM ROTA',   corStatus: '#2ECC71' },
  { nome: 'Felipe Mendes',  confirmadas: 31, status: 'DESCANSO',  corStatus: '#F5A623' },
  { nome: 'Carlos Eduardo', confirmadas: 28, status: 'EM ROTA',   corStatus: '#2ECC71' },
];

export default function MotoristasPage() {
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 32 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
            <div style={{ width: 4, height: 28, backgroundColor: C.amber, borderRadius: 2 }} />
            <h1 style={{ fontSize: 28, fontWeight: 900, color: C.textPrimary, letterSpacing: -0.5 }}>Motoristas</h1>
          </div>
          <p style={{ fontSize: 14, color: C.textSecondary, marginLeft: 16 }}>Controle, ranking e monitoramento da frota.</p>
        </div>
        <button style={{
          backgroundColor: C.amber, color: C.bg, border: 'none',
          borderRadius: 8, padding: '12px 20px', fontWeight: 800,
          fontSize: 13, letterSpacing: 0.5, cursor: 'pointer',
        }}>
          + NOVO MOTORISTA
        </button>
      </div>

      {/* Ranking */}
      <div style={{ backgroundColor: C.surface, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
        {/* Barra topo âmbar — elemento de assinatura */}
        <div style={{ height: 3, backgroundColor: C.amber }} />
        <div style={{ padding: 24 }}>
          <h2 style={{ fontSize: 15, fontWeight: 700, color: C.textPrimary, marginBottom: 20 }}>
            Ranking de Eficiência — Hoje
          </h2>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: `1px solid ${C.border}` }}>
                {['#', 'Nome', 'Confirmadas', 'Status', ''].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '8px 12px', fontSize: 11, fontWeight: 700, color: C.textMuted, letterSpacing: 1 }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {motoristas.map((m, i) => (
                <tr key={i} style={{ borderBottom: `1px solid ${C.border}` }}>
                  <td style={{ padding: '16px 12px', fontSize: 18, fontWeight: 900, color: i === 0 ? C.amber : C.textMuted }}>
                    {i + 1}
                  </td>
                  <td style={{ padding: '16px 12px', fontSize: 15, fontWeight: 700, color: C.textPrimary }}>{m.nome}</td>
                  <td style={{ padding: '16px 12px', fontSize: 24, fontWeight: 900, color: C.green, letterSpacing: -1 }}>
                    {m.confirmadas}
                  </td>
                  <td style={{ padding: '16px 12px' }}>
                    <Selo label={m.status} cor={m.corStatus} />
                  </td>
                  <td style={{ padding: '16px 12px', textAlign: 'right' }}>
                    <button style={{ background: 'none', border: `1px solid ${C.border}`, borderRadius: 6, padding: '6px 14px', color: C.textSecondary, fontSize: 12, cursor: 'pointer', fontWeight: 600 }}>
                      Ver Detalhes
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
