'use client';

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // Se já estiver logado, redireciona
    if (localStorage.getItem('token')) {
      router.push('/');
    }
  }, [router]);

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault();
    if (!email.trim() || !password.trim()) {
      setError('Por favor, preencha todos os campos.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const res = await fetch('http://localhost:8080/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, senha: password }),
      });

      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.detail || 'E-mail ou senha incorretos.');
      }

      const data = await res.json();
      
      // Salva no localStorage
      localStorage.setItem('token', data.accessToken);
      localStorage.setItem('refreshToken', data.refreshToken);
      localStorage.setItem('usuarioNome', data.nome);
      localStorage.setItem('usuarioPapel', data.papel);
      localStorage.setItem('usuarioId', data.usuarioId);
      localStorage.setItem('filialId', data.filialId);
      localStorage.setItem('filialNome', data.filialNome);

      router.push('/');
    } catch (err: any) {
      setError(err.message || 'Falha de conexão com o servidor.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      backgroundColor: '#131517',
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    }}>
      <div style={{
        width: '100%',
        maxWidth: 420,
        backgroundColor: '#1E2022',
        border: '1px solid #2D3035',
        borderRadius: 16,
        padding: 40,
        boxShadow: '0 8px 24px rgba(0, 0, 0, 0.4)',
      }}>
        {/* Logo/Header */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 48,
            height: 48,
            borderRadius: 12,
            background: 'linear-gradient(135deg, #4f7cff 0%, #8b5cf6 100%)',
            marginBottom: 16,
            fontWeight: 'bold',
            fontSize: 24,
            color: '#fff',
          }}>
            H
          </div>
          <h1 style={{
            fontSize: 24,
            fontWeight: 800,
            color: '#FFFFFF',
            letterSpacing: '-0.5px',
            margin: 0,
          }}>
            Acesse o FluxoHub
          </h1>
          <p style={{
            fontSize: 13,
            color: '#9CA3AF',
            marginTop: 6,
          }}>
            Painel Administrativo do Vendedor
          </p>
        </div>

        {error && (
          <div style={{
            backgroundColor: 'rgba(239, 68, 68, 0.1)',
            border: '1px solid rgba(239, 68, 68, 0.2)',
            borderRadius: 8,
            padding: '12px 16px',
            color: '#EF4444',
            fontSize: 13,
            fontWeight: 600,
            marginBottom: 24,
            textAlign: 'center',
          }}>
            {error}
          </div>
        )}

        <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <label style={{ fontSize: 11, fontWeight: 700, color: '#9CA3AF', letterSpacing: '0.5px' }}>E-MAIL</label>
            <input
              type="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder="vendedor@pod.local"
              disabled={loading}
              style={{
                height: 48,
                backgroundColor: '#27292D',
                border: '1px solid #373A40',
                borderRadius: 8,
                paddingLeft: 16,
                paddingRight: 16,
                fontSize: 14,
                color: '#FFFFFF',
                outline: 'none',
              }}
            />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <label style={{ fontSize: 11, fontWeight: 700, color: '#9CA3AF', letterSpacing: '0.5px' }}>SENHA</label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="••••••••"
              disabled={loading}
              style={{
                height: 48,
                backgroundColor: '#27292D',
                border: '1px solid #373A40',
                borderRadius: 8,
                paddingLeft: 16,
                paddingRight: 16,
                fontSize: 14,
                color: '#FFFFFF',
                outline: 'none',
              }}
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            style={{
              height: 48,
              background: 'linear-gradient(135deg, #4f7cff 0%, #8b5cf6 100%)',
              border: 'none',
              borderRadius: 8,
              color: '#FFFFFF',
              fontWeight: 700,
              fontSize: 14,
              cursor: 'pointer',
              marginTop: 10,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {loading ? 'Entrando...' : 'ENTRAR NO PAINEL'}
          </button>
        </form>
      </div>
    </div>
  );
}
