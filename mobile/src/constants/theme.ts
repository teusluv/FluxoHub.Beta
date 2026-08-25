/**
 * FLUXOHUB DESIGN SYSTEM v2 — "Logistics Mission Control"
 * =========================================================
 *
 * Fonte da Verdade: Stitch Export (stitch_fluxohub_mobile_design_system/DESIGN.md)
 * Gerado em: 2026-08-22
 *
 * FILOSOFIA: Nocturno, Robusto, Alta Confiança.
 * Estética "Brutalist Modificado" — terminal de missão crítica.
 * Motorista em campo, sol forte, uma mão só. Zero rounded corners.
 * Fontes técnicas (Geist + JetBrains Mono) para precisão de dados.
 *
 * ═══════════════════════════════════════════════════════════════
 * PALETA — Material You Dark Scheme para Logística
 * ═══════════════════════════════════════════════════════════════
 */

// ─── CORES ─────────────────────────────────────────────────────
export const COLORS = {
  // Superfícies (do mais escuro ao mais claro)
  background:               '#03122e',  // Base — espaço profundo
  surfaceLowest:            '#000d29',  // Mais profundo que o fundo
  surfaceLow:               '#0c1b37',  // Divisão sutil
  surface:                  '#101f3b',  // Cards e containers padrão
  surfaceHigh:              '#1b2946',  // Containers elevados
  surfaceHighest:           '#273452',  // Máximo antes do bright
  surfaceBright:            '#2b3956',  // Elementos de destaque em superfície
  surfaceElevated:          '#182136',  // Modais, bottom sheets flutuantes
  surfaceVariant:           '#273452',  // Superfície com variação semântica

  // Primário — lavanda-azul (ação, marca, destaque)
  primary:                  '#b5c4ff',  // Cor de ação principal
  onPrimary:                '#00297b',  // Texto sobre primary
  primaryContainer:         '#648aff',  // Gradiente de botão (from → to)
  onPrimaryContainer:       '#00236d',  // Texto sobre primaryContainer
  inversePrimary:           '#1a53d6',  // Versão light do primary

  // Secundário — lilás (indicadores, secondary actions)
  secondary:                '#d0bcff',
  onSecondary:              '#3c0091',
  secondaryContainer:       '#571bc1',
  onSecondaryContainer:     '#c4abff',

  // Terciário — cinza-azulado (elementos neutros)
  tertiary:                 '#c2c6db',
  onTertiary:               '#2b3040',
  tertiaryContainer:        '#8c90a4',
  onTertiaryContainer:      '#242939',

  // Superfície sobre fundos
  onBackground:             '#d8e2ff',  // Texto principal
  onSurface:                '#d8e2ff',  // Texto sobre superfície
  onSurfaceVariant:         '#c3c5d7',  // Texto secundário/rótulos
  inverseSurface:           '#d8e2ff',
  inverseOnSurface:         '#22304d',
  surfaceTint:              '#b5c4ff',

  // Contornos e bordas
  outline:                  '#8d90a0',  // Borda estrutural visível
  outlineVariant:           '#434654',  // Borda sutil (separadores)

  // Semânticos de status — preservados para legibilidade de campo
  success:                  '#2ECC71',  // Entregue — verde semáforo
  successDark:              '#1A8A4A',  // Verde pressed
  warning:                  '#F5A623',  // Pendente — âmbar colete
  warningDark:              '#C07D0B',  // Âmbar pressed
  danger:                   '#E74C3C',  // Divergência/crítico — choca
  dangerDark:               '#A93226',  // Vermelho pressed
  errorText:                '#ffb4ab',  // Texto de mensagem de erro (pastel)
  onError:                  '#690005',
  errorContainer:           '#93000a',
  onErrorContainer:         '#ffdad6',

  // Aliases semânticos de status (para selos/badges)
  pendente:                 '#F5A623',
  emRota:                   '#b5c4ff',  // usa o primary novo
  confirmado:               '#2ECC71',
  problema:                 '#E74C3C',

  // Aliases de retrocompatibilidade — NÃO REMOVER (usados em telas existentes)
  primary_compat:           '#b5c4ff',  // novo primary
  primaryDark:              '#00297b',
  accent:                   '#2ECC71',
  blue:                     '#b5c4ff',  // mapeado para o novo primary
  blueDark:                 '#00297b',
  green:                    '#2ECC71',
  greenDark:                '#1A8A4A',
  red:                      '#E74C3C',
  redDark:                  '#A93226',
  amber:                    '#F5A623',
  amberDark:                '#C07D0B',

  // Border aliases
  border:                   '#434654',  // = outlineVariant
  borderStrong:             '#8d90a0',  // = outline

  // Texto aliases
  textPrimary:              '#d8e2ff',  // = onSurface
  textSecondary:            '#c3c5d7',  // = onSurfaceVariant
  textMuted:                '#8d90a0',  // = outline
} as const;

// ─── ESPAÇAMENTO — Grid de 4px (idêntico ao Stitch) ───────────
export const SPACING = {
  xs:   4,
  sm:   8,
  md:   16,
  lg:   24,
  xl:   32,
  xxl:  48,
  xxxl: 64,
} as const;

// ─── RAIOS DE BORDA — Sharp (0) conforme Stitch "Brutalist" ───
// Exceção: bottom sheets usam 2px no topo apenas
export const RADIUS = {
  none:  0,   // Padrão — todos os elementos (sharp corners)
  sm:    0,   // Inputs — sharp
  md:    0,   // Cards — sharp
  lg:    0,   // Cards principais — sharp
  xl:    0,   // Selos — sharp (estilo carimbo)
  sheet: 2,   // Bottom sheets — 2px topo apenas (distingue de modal)
} as const;

// ─── ALVOS DE TOQUE — Campo, acima do padrão WCAG ─────────────
// Stitch recomenda 52px; mantemos 56 mínimo para segurança de campo
export const TOUCH = {
  min:     56,   // Mínimo absoluto de campo
  default: 56,   // Botões padrão (Stitch diz 52, mantemos 56 por segurança)
  large:   64,   // Botão CTA
  huge:    120,  // Botão de câmera (tela de captura)
} as const;

// ─── TIPOGRAFIA — Geist (headlines) + JetBrains Mono (dados) ──
// Stitch especifica famílias técnicas; usamos mapeamento para React Native
// Em produção: instalar @expo-google-fonts/geist e @expo-google-fonts/jetbrains-mono
export const FONT_SIZE = {
  // Mapeamento Stitch → escala numérica
  display:      28,  // Stitch display — contadores KPI, métricas top-level
  headline1:    22,  // Stitch headline-1 — título de tela
  headline2:    18,  // Stitch headline-2 — título de card
  body:         16,  // Stitch body — texto padrão
  bodySmall:    14,  // Stitch body-small — metadados, NF code
  caption:      12,  // Stitch caption — rótulos, badges

  // Aliases de retrocompatibilidade
  xs:    12,   // = caption
  sm:    14,   // = bodySmall
  md:    16,   // = body
  lg:    18,   // = headline2
  xl:    22,   // = headline1
  xxl:   28,   // = display
  hero:  48,   // Número grande — tela de fim de rota
} as const;

export const LINE_HEIGHT = {
  display:   34,
  headline1: 28,
  headline2: 24,
  body:      24,
  bodySmall: 20,
  caption:   16,
} as const;

export const FONT_WEIGHT = {
  regular:    '400',
  medium:     '500',
  semibold:   '600',
  bold:       '700',
} as const;

// Famílias de fonte — Stitch: Geist (titulos) + JetBrains Mono (dados)
// RN: instale as fontes via expo-font; como fallback usa System
export const FONT = {
  display:   'Geist-Bold',         // Headlines e títulos
  headline:  'Geist-SemiBold',     // Subtítulos
  body:      'JetBrainsMono-Regular',  // Corpo, dados, NF numbers
  bodySemi:  'JetBrainsMono-SemiBold', // Botões (uppercase + semibold)
  caption:   'JetBrainsMono-Medium',   // Rótulos e badges
  // Fallback para quando as fontes ainda não estiverem carregadas
  fallback:  'System',
} as const;

// ─── GRADIENTE DE MARCA ─────────────────────────────────────────
// Usado em botões primários e elementos de destaque
export const GRADIENT = {
  brand: ['#b5c4ff', '#648aff'],  // primary → primaryContainer (Stitch)
  brandStart: '#b5c4ff',
  brandEnd:   '#648aff',
} as const;

// ─── ELEVAÇÃO — Tonal Layering (sem sombras, bordas definem depth) ──
// Profundidade comunicada por camadas tonais + bordas de 1px
export const ELEVATION = {
  base:        COLORS.background,     // Camada base
  surface:     COLORS.surface,        // Cards e containers
  elevated:    COLORS.surfaceElevated,// Modais e bottom sheets
  highest:     COLORS.surfaceHighest, // Elementos flutuantes mais altos
} as const;
