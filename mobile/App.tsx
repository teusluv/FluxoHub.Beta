import React, { useEffect, useState } from 'react';
import { AuthProvider } from './src/context/AuthContext';
import { SyncProvider } from './src/context/SyncContext';
import AppNavigator from './src/navigation/AppNavigator';
import * as SecureStore from 'expo-secure-store';
import { updateApiBaseUrl } from './src/constants/api';

export default function App() {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    async function init() {
      try {
        const customUrl = await SecureStore.getItemAsync('custom_api_url');
        if (customUrl) {
          updateApiBaseUrl(customUrl);
        }
      } catch (e) {
        console.warn('Failed to load custom api url', e);
      } finally {
        setReady(true);
      }
    }
    init();
  }, []);

  if (!ready) return null;

  return (
    <AuthProvider>
      <SyncProvider>
        <AppNavigator />
      </SyncProvider>
    </AuthProvider>
  );
}
