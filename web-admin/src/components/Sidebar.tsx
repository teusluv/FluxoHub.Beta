'use client';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import React, { useEffect, useState } from 'react';const NAV = [
  { href: '/',           label: 'Dashboard',   icon: '▦' },
  { href: '/motoristas', label: 'Motoristas',   icon: '🚚' },
  { href: '/vendedores', label: 'Canhotos',     icon: '📄' },
];
export function Sidebar() {
  const path = usePathname();
  const router = useRouter();
  const [userName, setUserName] = useState('');
  const [filialNome, setFilialNome] = useState('');

  useEffect(() => {
    setUserName(localStorage.getItem('usuarioNome') || 'Vendedor');
    setFilialNome(localStorage.getItem('filialNome') || 'Matriz');
  }, []);

  function handleLogout() {
    localStorage.clear();
    router.push('/login');
  }

  return (
    <aside style={{
      width: 240,
      height: '100vh',
      backgroundColor: '#1E2022',
      borderRight: '1px solid #2D3035',
      display: 'flex',
      flexDirection: 'column',
      position: 'fixed',
      left: 0,
      top: 0,
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    }}>
      {/* Logo */}
      <div style={{ padding: '28px 24px 20px', borderBottom: '1px solid #2D3035' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{
            width: 4,
            height: 28,
            background: 'linear-gradient(135deg, #4f7cff 0%, #8b5cf6 100%)',
            borderRadius: 2
          }} />
          <span style={{ fontSize: 22, fontWeight: 900, color: '#FFFFFF', letterSpacing: '-0.5px' }}>FluxoHub</span>
        </div>
        <p style={{ fontSize: 10, fontWeight: 700, color: '#9CA3AF', letterSpacing: 2, marginTop: 6, marginLeft: 14 }}>
          ADMIN PANEL
        </p>
      </div>

      {/* Nav */}
      <nav style={{ flex: 1, padding: '16px 12px', display: 'flex', flexDirection: 'column', gap: 4 }}>
        {NAV.map(n => {
          const active = path === n.href;
          return (
            <Link key={n.href} href={n.href} style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              padding: '12px 14px',
              borderRadius: 8,
              backgroundColor: active ? 'rgba(79, 124, 255, 0.1)' : 'transparent',
              borderLeft: active ? '3px solid #4f7cff' : '3px solid transparent',
              color: active ? '#4f7cff' : '#9CA3AF',
              fontWeight: active ? 700 : 500,
              fontSize: 14,
              textDecoration: 'none',
              transition: 'all 0.15s',
            }}>
              <span style={{ fontSize: 16 }}>{n.icon}</span>
              {n.label}
            </Link>
          );
        })}
      </nav>

      {/* User Info & Logout */}
      <div style={{ padding: '16px 20px', borderTop: '1px solid #2D3035', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div>
          <p style={{ fontSize: 13, fontWeight: 700, color: '#FFFFFF', margin: 0, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {userName}
          </p>
          <p style={{ fontSize: 11, color: '#9CA3AF', margin: '2px 0 0 0', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {filialNome}
          </p>
        </div>
        
        <button
          onClick={handleLogout}
          style={{
            height: 36,
            backgroundColor: '#27292D',
            border: '1px solid #373A40',
            borderRadius: 6,
            color: '#EF4444',
            fontWeight: 700,
            fontSize: 12,
            cursor: 'pointer',
            width: '100%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            transition: 'background-color 0.15s',
          }}
          onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(239, 68, 68, 0.1)'}
          onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#27292D'}
        >
          🗙 Sair
        </button>
      </div>
    </aside>
  );
}
