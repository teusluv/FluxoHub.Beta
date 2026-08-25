/**
 * CanhotoScreen — Tela de Captura da Foto
 *
 * DECISÕES DE DESIGN:
 * - Botão de câmera enorme (120dp) — um toque, sem confirmação desnecessária antes
 * - Preview com DUAS ações únicas e claras: "Usar esta foto" / "Tirar de novo"
 * - Feedback imediato na confirmação (sem esperar rede) — motorista segue pra próxima entrega
 * - Sem LinearGradient decorativo — era o símbolo exato de "feito com IA"
 * - Guia de enquadramento âmbar (não azul genérico) — segue o design system
 * - Sem termos técnicos: "Não deu pra enviar agora" em vez de "Erro de upload"
 * - Vibração tátil no momento da confirmação (substitui feedback visual de "olhar pra tela")
 */
import React, { useRef, useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity,
  Alert, ActivityIndicator, Image, Vibration,
} from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import * as ImageManipulator from 'expo-image-manipulator';
import { StatusBar } from 'expo-status-bar';
import Constants from 'expo-constants';
import { useSync } from '../context/SyncContext';
import { COLORS, SPACING, RADIUS, FONT_SIZE, TOUCH } from '../constants/theme';

function getDeviceId(): string {
  return Constants.deviceId ?? Constants.sessionId ?? `device-${Date.now()}`;
}

export default function CanhotoScreen({ route, navigation }: any) {
  const { entrega } = route.params;
  const [permission, requestPermission] = useCameraPermissions();
  const [loading, setLoading] = useState(false);
  const [preview, setPreview] = useState<string | null>(null);
  const cameraRef = useRef<any>(null);
  const { adicionarNaFila } = useSync();

  // ── Permissão de câmera ──────────────────────────────────────
  if (!permission) return <View style={styles.container} />;

  if (!permission.granted) {
    return (
      <View style={styles.permissaoContainer}>
        <StatusBar style="light" />
        <View style={styles.permissaoLinha} />
        <Text style={styles.permissaoTitulo}>
          Acesso à câmera necessário
        </Text>
        <Text style={styles.permissaoTexto}>
          O app precisa da câmera para registrar os canhotos. Sem isso não é possível confirmar as entregas.
        </Text>
        <TouchableOpacity
          style={styles.permissaoBotao}
          onPress={requestPermission}
          accessibilityLabel="Permitir acesso à câmera"
        >
          <Text style={styles.permissaoBotaoTexto}>PERMITIR CÂMERA</Text>
        </TouchableOpacity>
      </View>
    );
  }

  // ── Tirar foto ───────────────────────────────────────────────
  async function tirarFoto() {
    if (!cameraRef.current || loading) return;
    setLoading(true);
    try {
      const foto = await cameraRef.current.takePictureAsync({ quality: 0.9, base64: false });
      setPreview(foto.uri);
    } catch {
      Alert.alert('Não foi possível tirar a foto', 'Tente novamente.');
    } finally {
      setLoading(false);
    }
  }

  // ── Confirmar e enfileirar ───────────────────────────────────
  async function confirmar() {
    if (!preview || loading) return;
    setLoading(true);
    try {
      // Compressão antes do enfileiramento
      const comprimida = await ImageManipulator.manipulateAsync(
        preview,
        [{ resize: { width: 1024 } }],
        { compress: 0.7, format: ImageManipulator.SaveFormat.JPEG }
      );

      await adicionarNaFila({
        tipo: 'CANHOTO',
        entregaId: entrega.id,
        payload: {
          entregaId: entrega.id,
          imagemUri: comprimida.uri,
          deviceId: getDeviceId(),
          capturedAt: new Date().toISOString(),
        },
      });

      // Vibração tátil curta — motorista não precisa olhar pra tela pra saber que deu certo
      Vibration.vibrate(80);

      // Navega imediatamente — sem esperar rede
      navigation.popToTop();
    } catch {
      Alert.alert(
        'Não deu pra salvar agora',
        'Tente registrar a foto de novo. Se continuar, fale com a central.',
      );
    } finally {
      setLoading(false);
    }
  }

  // ── Tela de preview ──────────────────────────────────────────
  // Apenas duas ações. Nenhuma outra coisa na tela.
  if (preview) {
    return (
      <View style={styles.container}>
        <StatusBar style="light" />
        <Image source={{ uri: preview }} style={styles.preview} resizeMode="cover" />

        {/* Info da entrega sobre a imagem */}
        <View style={styles.previewInfo}>
          <Text style={styles.previewNf}>{entrega.numeroNotaFiscal}</Text>
          <Text style={styles.previewCliente} numberOfLines={1}>{entrega.clienteNome}</Text>
        </View>

        {/* Ações — duas e só duas */}
        <View style={styles.previewAcoes}>
          <TouchableOpacity
            style={styles.btnRefazer}
            onPress={() => setPreview(null)}
            disabled={loading}
            accessibilityLabel="Tirar foto de novo"
          >
            <Text style={styles.btnRefazerTexto}>TIRAR DE NOVO</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.btnConfirmar, loading && { opacity: 0.7 }]}
            onPress={confirmar}
            disabled={loading}
            accessibilityLabel="Usar esta foto e confirmar entrega"
          >
            {loading
              ? <ActivityIndicator color={COLORS.background} />
              : <Text style={styles.btnConfirmarTexto}>USAR ESTA FOTO</Text>
            }
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  // ── Viewfinder (câmera) ──────────────────────────────────────
  return (
    <View style={styles.container}>
      <StatusBar style="light" />

      {/* Header minimalista — contexto da entrega */}
      <View style={styles.camHeader}>
        <TouchableOpacity
          style={styles.voltarBtn}
          onPress={() => navigation.goBack()}
          accessibilityLabel="Voltar para a lista de entregas"
        >
          <Text style={styles.voltarTexto}>‹ VOLTAR</Text>
        </TouchableOpacity>
        <View style={styles.camInfoNf}>
          <Text style={styles.camNf}>{entrega.numeroNotaFiscal}</Text>
          <Text style={styles.camCliente} numberOfLines={1}>{entrega.clienteNome}</Text>
        </View>
      </View>

      {/* Viewfinder — ocupa o máximo de espaço disponível */}
      <CameraView ref={cameraRef} style={styles.camera} facing="back">
        {/* Guia de enquadramento âmbar — segue o design system */}
        <View style={styles.guiaContainer}>
          <View style={styles.guia} />
          <Text style={styles.guiaHint}>Encaixe o canhoto aqui</Text>
        </View>
      </CameraView>

      {/* Área de captura — botão enorme, impossível de errar */}
      <View style={styles.capturaArea}>
        <TouchableOpacity
          style={[styles.capturaBotao, loading && { opacity: 0.5 }]}
          onPress={tirarFoto}
          disabled={loading}
          accessibilityLabel="Fotografar canhoto"
        >
          {loading
            ? <ActivityIndicator color={COLORS.background} size="large" />
            : <View style={styles.capturaInner} />
          }
        </TouchableOpacity>
        <Text style={styles.capturaHint}>Toque para fotografar</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container:          { flex: 1, backgroundColor: '#000' },

  // Permissão
  permissaoContainer: {
    flex: 1, backgroundColor: COLORS.background,
    alignItems: 'center', justifyContent: 'center',
    padding: SPACING.xl,
  },
  permissaoLinha:     { height: 4, width: 48, backgroundColor: COLORS.amber, marginBottom: SPACING.xl, borderRadius: 2 },
  permissaoTitulo:    { fontSize: FONT_SIZE.lg, fontWeight: '800', color: COLORS.textPrimary, textAlign: 'center', marginBottom: SPACING.md },
  permissaoTexto:     { color: COLORS.textSecondary, textAlign: 'center', lineHeight: 22, marginBottom: SPACING.xl, fontSize: FONT_SIZE.md },
  permissaoBotao:     {
    height: TOUCH.large, backgroundColor: COLORS.amber,
    borderRadius: RADIUS.md, paddingHorizontal: SPACING.xl,
    alignItems: 'center', justifyContent: 'center',
  },
  permissaoBotaoTexto:{ color: COLORS.background, fontWeight: '900', fontSize: FONT_SIZE.md, letterSpacing: 1.5 },

  // Header da câmera
  camHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: SPACING.md,
    paddingTop: 56,
    paddingBottom: SPACING.md,
    backgroundColor: '#000',
    gap: SPACING.md,
  },
  voltarBtn:          { paddingRight: SPACING.sm },
  voltarTexto:        { color: COLORS.amber, fontSize: FONT_SIZE.xs, fontWeight: '700', letterSpacing: 1 },
  camInfoNf:          { flex: 1 },
  camNf:              { fontSize: FONT_SIZE.lg, fontWeight: '900', color: COLORS.textPrimary, letterSpacing: -0.5 },
  camCliente:         { fontSize: FONT_SIZE.sm, color: COLORS.textSecondary },

  // Câmera / viewfinder
  camera:             { flex: 1 },
  guiaContainer:      { flex: 1, alignItems: 'center', justifyContent: 'center' },
  guia: {
    width: '80%',
    aspectRatio: 1.6,  // proporção A4 paisagem — formato real de canhoto
    borderWidth: 2,
    borderColor: COLORS.amber,
    borderRadius: RADIUS.md,
  },
  guiaHint:           { color: COLORS.amber, fontSize: FONT_SIZE.xs, fontWeight: '600', marginTop: SPACING.sm, letterSpacing: 0.5 },

  // Área de captura — botão enorme
  capturaArea:        { alignItems: 'center', paddingVertical: SPACING.xl, backgroundColor: '#000' },
  capturaBotao: {
    width: TOUCH.huge,
    height: TOUCH.huge,
    borderRadius: TOUCH.huge / 2,
    borderWidth: 5,
    borderColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#ffffff20',
  },
  capturaInner: {
    width: TOUCH.huge - 24,
    height: TOUCH.huge - 24,
    borderRadius: (TOUCH.huge - 24) / 2,
    backgroundColor: '#fff',
  },
  capturaHint:        { color: COLORS.textMuted, fontSize: FONT_SIZE.xs, fontWeight: '600', letterSpacing: 0.5, marginTop: SPACING.md },

  // Preview da foto
  preview:            { flex: 1, width: '100%' },
  previewInfo: {
    position: 'absolute',
    top: 56,
    left: 0,
    right: 0,
    paddingHorizontal: SPACING.lg,
    paddingVertical: SPACING.sm,
    backgroundColor: '#00000088',
  },
  previewNf:          { fontSize: FONT_SIZE.xl, fontWeight: '900', color: '#fff', letterSpacing: -0.5 },
  previewCliente:     { fontSize: FONT_SIZE.sm, color: 'rgba(255,255,255,0.8)' },

  // Ações do preview
  previewAcoes: {
    flexDirection: 'row',
    padding: SPACING.lg,
    gap: SPACING.md,
    backgroundColor: '#000',
  },
  btnRefazer: {
    flex: 1,
    height: TOUCH.large,
    borderWidth: 1.5,
    borderColor: COLORS.border,
    borderRadius: RADIUS.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  btnRefazerTexto:    { color: COLORS.textSecondary, fontWeight: '800', fontSize: FONT_SIZE.sm, letterSpacing: 1 },
  btnConfirmar: {
    flex: 2,
    height: TOUCH.large,
    backgroundColor: COLORS.amber,
    borderRadius: RADIUS.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  btnConfirmarTexto:  { color: COLORS.background, fontWeight: '900', fontSize: FONT_SIZE.md, letterSpacing: 1 },
});
