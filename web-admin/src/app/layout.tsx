import type { Metadata } from 'next';
import './globals.css';
import { AppLayoutWrapper } from '@/components/AppLayoutWrapper';

export const metadata: Metadata = {
  title: 'FluxoHub Admin',
  description: 'Painel Administrativo de Canhotos Digitais',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body style={{ margin: 0, padding: 0, backgroundColor: '#131517' }}>
        <AppLayoutWrapper>{children}</AppLayoutWrapper>
      </body>
    </html>
  );
}
