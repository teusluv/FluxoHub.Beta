/**
 * FimDeRotaScreen — Resumo do dia ao encerrar
 *
 * DECISÕES DE DESIGN:
 * - Linguagem humana, não técnica: "3 fotos ainda enviando, vamos cuidar disso"
 * - Números grandes — maior informação da tela, escaneável de relance
 * - Sem celebração exagerada — é ferramenta de trabalho, não gamificação
 * - O Selo aparece nos contadores (elemento de assinatura)
 */
import React from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useSync } from '../context/SyncContext';
import { COLORS, SPACING, RADIUS, FONT_SIZE, TOUCH } from '../constants/theme';

export default function FimDeRotaScreen({ route, navigation }: any) {
  const { totalEntregas = 0, entregasFeitas = 0 } = route.params ?? {};
  const { pendingCount } = useSync();
  const pendentes = totalEntregas - entregasFeitas;

  return (
    <View style={styles.container}>
      <StatusBar style="light" />

      <ScrollView contentContainerStyle={styles.scroll}>

        {/* Linha âmbar — elemento de assinatura */}
        <View style={styles.linhaAssinatura} />

        <Text style={styles.titulo}>Resumo da Rota</Text>
        <Text style={styles.subtitulo}>
          {new Date().toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' })}
        </Text>

        {/* Contadores principais — números grandes */}
        <View style={styles.contadores}>
          <View style={styles.contador}>
            <Text style={[styles.contadorNum, { color: COLORS.green }]}>{entregasFeitas}</Text>
            {/* Selo de assinatura nos contadores */}
            <View style={[styles.selos, { borderColor: COLORS.green }]}>
              <Text style={[styles.seloTexto, { color: COLORS.green }]}>CONFIRMADAS</Text>
            </View>
          </View>

          {pendentes > 0 && (
            <View style={styles.contador}>
              <Text style={[styles.contadorNum, { color: COLORS.amber }]}>{pendentes}</Text>
              <View style={[styles.selos, { borderColor: COLORS.amber }]}>
                <Text style={[styles.seloTexto, { color: COLORS.amber }]}>PENDENTES</Text>
              </View>
            </View>
          )}
        </View>

        {/* Status de envio — linguagem humana */}
        {pendingCount > 0 ? (
          <View style={styles.avisoEnvio}>
            <View style={[styles.avisoDot, { backgroundColor: COLORS.blue }]} />
            <Text style={styles.avisoTexto}>
              {pendingCount} foto{pendingCount > 1 ? 's' : ''} ainda enviando.
              Vamos cuidar disso assim que tiver internet.
            </Text>
          </View>
        ) : (
          <View style={styles.avisoEnvio}>
            <View style={[styles.avisoDot, { backgroundColor: COLORS.green }]} />
            <Text style={[styles.avisoTexto, { color: COLORS.green }]}>
              Tudo enviado. Você pode fechar o app.
            </Text>
          </View>
        )}

        {/* Ação de volta */}
        <TouchableOpacity
          style={styles.btnVoltar}
          onPress={() => navigation.popToTop()}
          accessibilityLabel="Voltar para a lista de entregas"
        >
          <Text style={styles.btnVoltarTexto}>VER LISTA DE ENTREGAS</Text>
        </TouchableOpacity>

      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container:        { flex: 1, backgroundColor: COLORS.background },
  scroll:           { flexGrow: 1, padding: SPACING.lg, paddingTop: 80 },

  linhaAssinatura:  { height: 4, width: 48, backgroundColor: COLORS.amber, borderRadius: 2, marginBottom: SPACING.xl },
  titulo:           { fontSize: 32, fontWeight: '900', color: COLORS.textPrimary, letterSpacing: -0.5 },
  subtitulo:        { fontSize: FONT_SIZE.sm, color: COLORS.textSecondary, marginTop: SPACING.xs, marginBottom: SPACING.xxl, textTransform: 'capitalize' },

  contadores:       { flexDirection: 'row', gap: SPACING.lg, marginBottom: SPACING.xl },
  contador:         { flex: 1, backgroundColor: COLORS.surface, borderRadius: RADIUS.lg, borderWidth: 1, borderColor: COLORS.border, padding: SPACING.lg },
  contadorNum:      { fontSize: FONT_SIZE.hero, fontWeight: '900', letterSpacing: -2, lineHeight: FONT_SIZE.hero + 4 },

  // Selos nos contadores
  selos:            { borderWidth: 1.5, borderRadius: RADIUS.xl, paddingHorizontal: 6, paddingVertical: 3, alignSelf: 'flex-start', marginTop: SPACING.sm },
  seloTexto:        { fontSize: 10, fontWeight: '800', letterSpacing: 0.5 },

  // Aviso de envio
  avisoEnvio: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: COLORS.surface,
    borderRadius: RADIUS.md,
    padding: SPACING.md,
    marginBottom: SPACING.xl,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  avisoDot:         { width: 8, height: 8, borderRadius: 4, marginRight: SPACING.sm, marginTop: 4 },
  avisoTexto:       { fontSize: FONT_SIZE.md, color: COLORS.textSecondary, flex: 1, lineHeight: 22 },

  btnVoltar: {
    height: TOUCH.large,
    borderWidth: 1.5,
    borderColor: COLORS.borderStrong,
    borderRadius: RADIUS.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  btnVoltarTexto:   { color: COLORS.textSecondary, fontWeight: '800', fontSize: FONT_SIZE.sm, letterSpacing: 1 },
});
