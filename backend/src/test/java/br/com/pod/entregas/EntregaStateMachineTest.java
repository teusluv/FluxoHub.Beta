package br.com.pod.entregas;

import br.com.pod.domain.entrega.Entrega;
import br.com.pod.domain.entrega.StatusEntrega;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários da máquina de estados de {@link Entrega#transicaoValida}.
 *
 * <p>Sem Spring, sem banco — puro domínio.
 * Cobre todas as transições válidas e inválidas documentadas em {@link StatusEntrega}.
 *
 * <p>Máquina de estados:
 * <pre>
 * PENDENTE → EM_ROTA → ENTREGUE_SEM_CANHOTO → ENTREGUE_COM_CANHOTO
 *    │              │              │
 *    └──────────────┴──────────────┴──► DIVERGENCIA (terminal parcial)
 * DIVERGENCIA → (nenhuma transição válida)
 * </pre>
 */
@DisplayName("Máquina de Estados — Entrega.transicaoValida()")
class EntregaStateMachineTest {

    // =========================================================================
    // TRANSIÇÕES VÁLIDAS
    // =========================================================================

    @Nested
    @DisplayName("Transições VÁLIDAS (devem retornar true)")
    class TransicoesValidas {

        @Test
        @DisplayName("PENDENTE → EM_ROTA ✓")
        void pendente_paraEmRota_valida() {
            var entrega = entregaComStatus(StatusEntrega.PENDENTE);
            assertThat(entrega.transicaoValida(StatusEntrega.EM_ROTA)).isTrue();
        }

        @Test
        @DisplayName("PENDENTE → DIVERGENCIA ✓ (ADMIN pode marcar divergência a qualquer ponto)")
        void pendente_paraDivergencia_valida() {
            var entrega = entregaComStatus(StatusEntrega.PENDENTE);
            assertThat(entrega.transicaoValida(StatusEntrega.DIVERGENCIA)).isTrue();
        }

        @Test
        @DisplayName("EM_ROTA → ENTREGUE_SEM_CANHOTO ✓")
        void emRota_paraEntregue_semCanhoto_valida() {
            var entrega = entregaComStatus(StatusEntrega.EM_ROTA);
            assertThat(entrega.transicaoValida(StatusEntrega.ENTREGUE_SEM_CANHOTO)).isTrue();
        }

        @Test
        @DisplayName("EM_ROTA → DIVERGENCIA ✓")
        void emRota_paraDivergencia_valida() {
            var entrega = entregaComStatus(StatusEntrega.EM_ROTA);
            assertThat(entrega.transicaoValida(StatusEntrega.DIVERGENCIA)).isTrue();
        }

        @Test
        @DisplayName("ENTREGUE_SEM_CANHOTO → ENTREGUE_COM_CANHOTO ✓ (upload do canhoto confirma)")
        void entregue_semCanhoto_paraComCanhoto_valida() {
            var entrega = entregaComStatus(StatusEntrega.ENTREGUE_SEM_CANHOTO);
            assertThat(entrega.transicaoValida(StatusEntrega.ENTREGUE_COM_CANHOTO)).isTrue();
        }

        @Test
        @DisplayName("ENTREGUE_SEM_CANHOTO → DIVERGENCIA ✓")
        void entregue_semCanhoto_paraDivergencia_valida() {
            var entrega = entregaComStatus(StatusEntrega.ENTREGUE_SEM_CANHOTO);
            assertThat(entrega.transicaoValida(StatusEntrega.DIVERGENCIA)).isTrue();
        }

        @Test
        @DisplayName("ENTREGUE_COM_CANHOTO → DIVERGENCIA ✓ (ADMIN pode reabrir mesmo entregue)")
        void entregue_comCanhoto_paraDivergencia_valida() {
            var entrega = entregaComStatus(StatusEntrega.ENTREGUE_COM_CANHOTO);
            assertThat(entrega.transicaoValida(StatusEntrega.DIVERGENCIA)).isTrue();
        }
    }

    // =========================================================================
    // TRANSIÇÕES INVÁLIDAS
    // =========================================================================

    @Nested
    @DisplayName("Transições INVÁLIDAS (devem retornar false)")
    class TransicoesInvalidas {

        // ─── De PENDENTE ──────────────────────────────────────────────────────

        @Test
        @DisplayName("PENDENTE → ENTREGUE_SEM_CANHOTO ✗ (deve passar por EM_ROTA)")
        void pendente_paraEntregue_semCanhoto_invalida() {
            var entrega = entregaComStatus(StatusEntrega.PENDENTE);
            assertThat(entrega.transicaoValida(StatusEntrega.ENTREGUE_SEM_CANHOTO)).isFalse();
        }

        @Test
        @DisplayName("PENDENTE → ENTREGUE_COM_CANHOTO ✗ (salto ilegal)")
        void pendente_paraEntregue_comCanhoto_invalida() {
            var entrega = entregaComStatus(StatusEntrega.PENDENTE);
            assertThat(entrega.transicaoValida(StatusEntrega.ENTREGUE_COM_CANHOTO)).isFalse();
        }

        @Test
        @DisplayName("PENDENTE → PENDENTE ✗ (idempotência de estado não é transição)")
        void pendente_paraPendente_invalida() {
            var entrega = entregaComStatus(StatusEntrega.PENDENTE);
            assertThat(entrega.transicaoValida(StatusEntrega.PENDENTE)).isFalse();
        }

        // ─── De EM_ROTA ───────────────────────────────────────────────────────

        @Test
        @DisplayName("EM_ROTA → PENDENTE ✗ (sem regressão de status)")
        void emRota_paraPendente_invalida() {
            var entrega = entregaComStatus(StatusEntrega.EM_ROTA);
            assertThat(entrega.transicaoValida(StatusEntrega.PENDENTE)).isFalse();
        }

        @Test
        @DisplayName("EM_ROTA → ENTREGUE_COM_CANHOTO ✗ (deve passar por ENTREGUE_SEM_CANHOTO)")
        void emRota_paraEntregue_comCanhoto_invalida() {
            var entrega = entregaComStatus(StatusEntrega.EM_ROTA);
            assertThat(entrega.transicaoValida(StatusEntrega.ENTREGUE_COM_CANHOTO)).isFalse();
        }

        @Test
        @DisplayName("EM_ROTA → EM_ROTA ✗")
        void emRota_paraEmRota_invalida() {
            var entrega = entregaComStatus(StatusEntrega.EM_ROTA);
            assertThat(entrega.transicaoValida(StatusEntrega.EM_ROTA)).isFalse();
        }

        // ─── De ENTREGUE_COM_CANHOTO ──────────────────────────────────────────

        @Test
        @DisplayName("ENTREGUE_COM_CANHOTO → PENDENTE ✗ (sem regressão)")
        void entregue_comCanhoto_paraPendente_invalida() {
            var entrega = entregaComStatus(StatusEntrega.ENTREGUE_COM_CANHOTO);
            assertThat(entrega.transicaoValida(StatusEntrega.PENDENTE)).isFalse();
        }

        @Test
        @DisplayName("ENTREGUE_COM_CANHOTO → EM_ROTA ✗")
        void entregue_comCanhoto_paraEmRota_invalida() {
            var entrega = entregaComStatus(StatusEntrega.ENTREGUE_COM_CANHOTO);
            assertThat(entrega.transicaoValida(StatusEntrega.EM_ROTA)).isFalse();
        }

        @Test
        @DisplayName("ENTREGUE_COM_CANHOTO → ENTREGUE_SEM_CANHOTO ✗ (regressão)")
        void entregue_comCanhoto_paraEntregue_semCanhoto_invalida() {
            var entrega = entregaComStatus(StatusEntrega.ENTREGUE_COM_CANHOTO);
            assertThat(entrega.transicaoValida(StatusEntrega.ENTREGUE_SEM_CANHOTO)).isFalse();
        }

        @Test
        @DisplayName("ENTREGUE_COM_CANHOTO → ENTREGUE_COM_CANHOTO ✗")
        void entregue_comCanhoto_paraComCanhoto_invalida() {
            var entrega = entregaComStatus(StatusEntrega.ENTREGUE_COM_CANHOTO);
            assertThat(entrega.transicaoValida(StatusEntrega.ENTREGUE_COM_CANHOTO)).isFalse();
        }

        // ─── De DIVERGENCIA (estado terminal) ─────────────────────────────────

        @Test
        @DisplayName("DIVERGENCIA → PENDENTE ✗ (terminal — nenhuma saída)")
        void divergencia_paraPendente_invalida() {
            var entrega = entregaComStatus(StatusEntrega.DIVERGENCIA);
            assertThat(entrega.transicaoValida(StatusEntrega.PENDENTE)).isFalse();
        }

        @Test
        @DisplayName("DIVERGENCIA → EM_ROTA ✗ (terminal)")
        void divergencia_paraEmRota_invalida() {
            var entrega = entregaComStatus(StatusEntrega.DIVERGENCIA);
            assertThat(entrega.transicaoValida(StatusEntrega.EM_ROTA)).isFalse();
        }

        @Test
        @DisplayName("DIVERGENCIA → ENTREGUE_SEM_CANHOTO ✗ (terminal)")
        void divergencia_paraEntregue_semCanhoto_invalida() {
            var entrega = entregaComStatus(StatusEntrega.DIVERGENCIA);
            assertThat(entrega.transicaoValida(StatusEntrega.ENTREGUE_SEM_CANHOTO)).isFalse();
        }

        @Test
        @DisplayName("DIVERGENCIA → ENTREGUE_COM_CANHOTO ✗ (terminal)")
        void divergencia_paraEntregue_comCanhoto_invalida() {
            var entrega = entregaComStatus(StatusEntrega.DIVERGENCIA);
            assertThat(entrega.transicaoValida(StatusEntrega.ENTREGUE_COM_CANHOTO)).isFalse();
        }

        @Test
        @DisplayName("DIVERGENCIA → DIVERGENCIA ✗ (terminal — não pode re-divergir)")
        void divergencia_paraDivergencia_invalida() {
            var entrega = entregaComStatus(StatusEntrega.DIVERGENCIA);
            assertThat(entrega.transicaoValida(StatusEntrega.DIVERGENCIA)).isFalse();
        }
    }

    // =========================================================================
    // Helper
    // =========================================================================

    /**
     * Cria uma entrega mínima com o status especificado.
     * Não usa banco — instancia diretamente via Lombok Builder.
     */
    private static Entrega entregaComStatus(StatusEntrega status) {
        return Entrega.builder()
                .status(status)
                .numeroNotaFiscal("NF-STATE-TEST")
                .clienteNome("Cliente QA")
                .build();
    }
}
