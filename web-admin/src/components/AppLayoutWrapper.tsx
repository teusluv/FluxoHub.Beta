'use client';

import React, { useEffect, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Sidebar } from './Sidebar';

export function AppLayoutWrapper({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const isLoginRoute = pathname === '/login';
    const token = localStorage.getItem('token');

    if (isLoginRoute) {
      setLoading(false);
      return;
    }

    if (!token) {
      router.push('/login');
    } else {
      setLoading(false);
    }
  }, [pathname, router]);

  const isLoginRoute = pathname === '/login';

  if (loading) {
    return (
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        backgroundColor: '#131517',
        color: '#FFFFFF',
        fontFamily: 'sans-serif',
      }}>
        <div style={{ fontSize: 16, fontWeight: 600 }}>Carregando painel...</div>
      </div>
    );
  }

  if (isLoginRoute) {
    return <>{children}</>;
  }

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: '#131517', color: '#FFFFFF' }}>
      <Sidebar />
      <main style={{ flex: 1, marginLeft: 240, padding: 40, overflowY: 'auto' }}>
        {children}
      </main>
    </div>
  );
}
