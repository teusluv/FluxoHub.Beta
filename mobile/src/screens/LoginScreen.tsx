/**
 * LoginScreen — Acesso ao sistema
 *
 * DECISÕES DE DESIGN:
 * - Sem logo decorativo grande — o app é ferramenta, não marca de consumidor
 * - Campos grandes (altura 56dp), texto grande ao sol
 * - Estado offline explicado claramente antes do botão de entrar
 * - Sessão persistida: se já logou antes, app abre direto (sem re-login offline)
 * - Botão de entrar ocupa largura total — target de toque máximo
 */
import React, { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity,
  StyleSheet, KeyboardAvoidingView, Platform,
  ActivityIndicator, Alert, ScrollView,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useAuth } from '../context/AuthContext';
import { useSync } from '../context/SyncContext';
import { COLORS, SPACING, RADIUS, FONT_SIZE, TOUCH } from '../constants/theme';
import { API_BASE_URL, updateApiBaseUrl } from '../constants/api';
import * as SecureStore from 'expo-secure-store';

export default function LoginScreen() {
  const { login } = useAuth();
  const { isOnline } = useSync();
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [loading, setLoading] = useState(false);
  const [senhaVísivel, setSenhaVísivel] = useState(false);
  
  const [showConfig, setShowConfig] = useState(false);
  const [customUrl, setCustomUrl] = useState(API_BASE_URL);

  async function handleSaveConfig() {
    try {
      const urlLimpa = customUrl.trim();
      if (urlLimpa) {
        await SecureStore.setItemAsync('custom_api_url', urlLimpa);
        updateApiBaseUrl(urlLimpa);
        Alert.alert('Servidor Atualizado', `Endereço alterado para:\n${urlLimpa}\n\nO app agora usará este IP.`);
      } else {
        await SecureStore.deleteItemAsync('custom_api_url');
        updateApiBaseUrl(null);
        Alert.alert('Configuração Limpa', 'Retornando ao IP padrão detectado pelo Expo.');
      }
      setShowConfig(false);
    } catch {
      Alert.alert('Erro', 'Não foi possível salvar a configuração.');
    }
  }

  async function handleLogin() {
    if (!email.trim() || !senha) {
      Alert.alert('Preencha os campos', 'Digite seu e-mail e senha para continuar.');
      return;
    }

    if (!isOnline) {
      Alert.alert(
        'Sem conexão',
        'Para entrar pela primeira vez, é preciso ter internet.\n\nSe você já entrou antes neste celular, feche e abra o app novamente.',
      );
      return;
    }

    setLoading(true);
    try {
      await login(email.trim().toLowerCase(), senha);
    } catch (e: any) {
      Alert.alert('Não foi possível entrar', 'Verifique seu e-mail e senha e tente de novo.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <StatusBar style="light" />
      <ScrollView
        contentContainerStyle={styles.scroll}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >

        {/* ── Identificação do app ────────────────────────────── */}
        {/*
          Sem logo ilustrativo genérico. Só nome + função.
          A barra âmbar acima do título é o elemento de assinatura
          aparecendo já na primeira tela.
        */}
        <View style={styles.topo}>
          <View style={styles.topoLinha} />
          <Text style={styles.topoApp}>FluxoHub</Text>
          <Text style={styles.topoFuncao}>REGISTRO DE ENTREGAS</Text>
        </View>

        {/* ── Aviso de offline ────────────────────────────────── */}
        {!isOnline && (
          <View style={styles.avisoOffline}>
            <View style={styles.avisoOfflineDot} />
            <Text style={styles.avisoOfflineText}>
              Sem internet — conecte-se para entrar
            </Text>
          </View>
        )}

        {/* ── Formulário ──────────────────────────────────────── */}
        <View style={styles.form}>
          <View style={styles.campo}>
            <Text style={styles.campoLabel}>E-MAIL</Text>
            <TextInput
              style={styles.input}
              placeholder="seu@email.com"
              placeholderTextColor={COLORS.textMuted}
              value={email}
              onChangeText={setEmail}
              keyboardType="email-address"
              autoCapitalize="none"
              autoCorrect={false}
              returnKeyType="next"
              editable={!loading}
              accessibilityLabel="Campo de e-mail"
            />
          </View>

          <View style={styles.campo}>
            <Text style={styles.campoLabel}>SENHA</Text>
            <View style={styles.senhaWrapper}>
              <TextInput
                style={[styles.input, styles.senhaInput]}
                placeholder="••••••••"
                placeholderTextColor={COLORS.textMuted}
                value={senha}
                onChangeText={setSenha}
                secureTextEntry={!senhaVísivel}
                returnKeyType="done"
                onSubmitEditing={handleLogin}
                editable={!loading}
                accessibilityLabel="Campo de senha"
              />
              <TouchableOpacity
                style={styles.senhaToggle}
                onPress={() => setSenhaVísivel(v => !v)}
                accessibilityLabel={senhaVísivel ? 'Ocultar senha' : 'Mostrar senha'}
              >
                <Text style={styles.senhaToggleText}>
                  {senhaVísivel ? 'OCULTAR' : 'MOSTRAR'}
                </Text>
              </TouchableOpacity>
            </View>
          </View>

          {/* Botão principal — largura total, 72dp de altura */}
          <TouchableOpacity
            style={[styles.btnEntrar, (!isOnline || loading) && styles.btnDesativado]}
            onPress={handleLogin}
            disabled={loading}
            accessibilityLabel="Entrar no sistema"
          >
            {loading
              ? <ActivityIndicator color={COLORS.background} />
              : <Text style={styles.btnEntrarText}>ENTRAR</Text>
            }
          </TouchableOpacity>
        </View>

        {/* Rodapé discreto com configuração de servidor */}
        <Text style={styles.rodape}>FluxoHub · Canhotos Digitais</Text>
        <Text style={[styles.rodape, { marginTop: 8, fontSize: 10, opacity: 0.5 }]}>
          Conectado em: {API_BASE_URL}
        </Text>

        {showConfig ? (
          <View style={styles.configContainer}>
            <Text style={styles.configLabel}>CUSTOMLIZAR ENDEREÇO DO SERVIDOR (IP)</Text>
            <TextInput
              style={styles.configInput}
              placeholder="Ex: http://192.168.0.5:8080"
              placeholderTextColor={COLORS.textMuted}
              value={customUrl}
              onChangeText={setCustomUrl}
              autoCapitalize="none"
              autoCorrect={false}
            />
            <View style={styles.configBotoes}>
              <TouchableOpacity style={styles.configBtnSalvar} onPress={handleSaveConfig}>
                <Text style={styles.configBtnSalvarTexto}>SALVAR</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.configBtnCancelar} onPress={() => setShowConfig(false)}>
                <Text style={styles.configBtnCancelarTexto}>CANCELAR</Text>
              </TouchableOpacity>
            </View>
          </View>
        ) : (
          <TouchableOpacity onPress={() => setShowConfig(true)} style={{ marginTop: SPACING.lg, alignSelf: 'center' }}>
            <Text style={styles.configLink}>ALTERAR IP DO SERVIDOR</Text>
          </TouchableOpacity>
        )}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container:          { flex: 1, backgroundColor: COLORS.background },
  scroll:             { flexGrow: 1, justifyContent: 'center', paddingHorizontal: SPACING.lg, paddingVertical: SPACING.xxl },

  // Topo — linha azul-lavanda (cor Stitch primary) como elemento de assinatura
  topo:               { marginBottom: SPACING.xl },
  topoLinha:          { height: 3, width: 48, backgroundColor: COLORS.primary, marginBottom: SPACING.lg },  // SHARP, primary novo
  topoApp:            { fontSize: 36, fontWeight: '900', color: COLORS.onSurface, letterSpacing: -1 },
  topoFuncao:         { fontSize: FONT_SIZE.xs, fontWeight: '700', color: COLORS.textMuted, letterSpacing: 2, marginTop: 4 },

  // Aviso offline — "SYSTEM_OFFLINE" estilo terminal (Stitch)
  avisoOffline: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#E74C3C18',
    borderWidth: 1,
    borderColor: COLORS.danger + '40',
    borderRadius: RADIUS.none,   // SHARP
    padding: SPACING.md,
    marginBottom: SPACING.lg,
  },
  avisoOfflineDot:    { width: 8, height: 8, borderRadius: 4, backgroundColor: COLORS.danger, marginRight: SPACING.sm },
  avisoOfflineText:   { fontSize: FONT_SIZE.xs, color: COLORS.danger, fontWeight: '700', flex: 1, letterSpacing: 0.5 },

  // Formulário
  form:               { gap: SPACING.lg },
  campo:              {},
  campoLabel:         { fontSize: FONT_SIZE.xs, fontWeight: '700', color: COLORS.textMuted, letterSpacing: 1, marginBottom: SPACING.xs },
  input: {
    height: TOUCH.min,            // 56dp — acima do mínimo Stitch de 52
    backgroundColor: COLORS.surface,
    borderWidth: 1,
    borderColor: COLORS.outlineVariant,
    borderRadius: RADIUS.none,   // SHARP
    paddingHorizontal: SPACING.md,
    fontSize: FONT_SIZE.md,
    color: COLORS.onSurface,
  },

  // Senha com toggle
  senhaWrapper:       { flexDirection: 'row', alignItems: 'center' },
  senhaInput:         { flex: 1, borderTopRightRadius: 0, borderBottomRightRadius: 0, borderRightWidth: 0 },
  senhaToggle: {
    height: TOUCH.min,
    paddingHorizontal: SPACING.md,
    backgroundColor: COLORS.surface,
    borderWidth: 1,
    borderColor: COLORS.outlineVariant,
    borderTopRightRadius: RADIUS.none,
    borderBottomRightRadius: RADIUS.none,
    justifyContent: 'center',
  },
  senhaToggleText:    { fontSize: FONT_SIZE.xs, fontWeight: '700', color: COLORS.textMuted, letterSpacing: 0.5 },

  // Botão entrar — gradiente brand conforme Stitch (primary → primaryContainer)
  btnEntrar: {
    height: TOUCH.large,
    backgroundColor: COLORS.primaryContainer,  // fallback sem LinearGradient
    borderRadius: RADIUS.none,   // SHARP
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: SPACING.sm,
  },
  btnDesativado:      { opacity: 0.5 },
  btnEntrarText:      { fontSize: FONT_SIZE.md, fontWeight: '900', color: COLORS.onPrimary, letterSpacing: 2 },

  rodape:             { textAlign: 'center', fontSize: FONT_SIZE.xs, color: COLORS.textMuted, marginTop: SPACING.xxl, letterSpacing: 1 },
  
  // Configurações do servidor customizado
  configContainer: {
    marginTop: SPACING.xl,
    padding: SPACING.md,
    backgroundColor: COLORS.surface,
    borderRadius: RADIUS.none,   // SHARP
    borderWidth: 1,
    borderColor: COLORS.outlineVariant,
  },
  configLabel: {
    fontSize: 10,
    fontWeight: '700',
    color: COLORS.textMuted,
    letterSpacing: 1,
    marginBottom: SPACING.xs,
  },
  configInput: {
    height: TOUCH.min,
    backgroundColor: COLORS.surfaceLow,
    borderWidth: 1,
    borderColor: COLORS.outlineVariant,
    borderRadius: RADIUS.none,   // SHARP
    paddingHorizontal: SPACING.md,
    fontSize: FONT_SIZE.md,
    color: COLORS.onSurface,
    marginBottom: SPACING.sm,
  },
  configBotoes: {
    flexDirection: 'row',
    gap: SPACING.sm,
  },
  configBtnSalvar: {
    flex: 1,
    height: TOUCH.min,
    backgroundColor: COLORS.primaryContainer,
    borderRadius: RADIUS.none,   // SHARP
    alignItems: 'center',
    justifyContent: 'center',
  },
  configBtnSalvarTexto: {
    fontSize: FONT_SIZE.sm,
    fontWeight: '800',
    color: COLORS.onPrimary,
  },
  configBtnCancelar: {
    flex: 1,
    height: TOUCH.min,
    borderWidth: 1,
    borderColor: COLORS.outlineVariant,
    borderRadius: RADIUS.none,   // SHARP
    alignItems: 'center',
    justifyContent: 'center',
  },
  configBtnCancelarTexto: {
    fontSize: FONT_SIZE.sm,
    fontWeight: '800',
    color: COLORS.onSurfaceVariant,
  },
  configLink: {
    fontSize: FONT_SIZE.sm,
    fontWeight: '700',
    color: COLORS.primary,
    letterSpacing: 0.5,
    textDecorationLine: 'underline' as const,
  },
});

