package br.com.pod.domain.entrega;

import br.com.pod.domain.filial.Filial;
import br.com.pod.domain.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade de entrega — núcleo do domínio POD.
 *
 * <p><strong>Multi-tenant:</strong> toda entrega pertence a uma {@link Filial}.
 * O filtro de tenant é aplicado na camada de serviço via {@link EntregaSpec#porFilial}
 * usando o {@link br.com.pod.shared.tenant.TenantContext}. Optamos por
 * Specification explícita em vez de Hibernate @Filter para maior testabilidade
 * e clareza — queries sempre mostram o filtro de filial explicitamente.
 *
 * <p><strong>chave_nfe:</strong> campo opcional de 44 dígitos (chave de acesso da NFe).
 * Permite integração futura com ERP fiscal sem redesenhar o schema.
 * Não obrigatório pois algumas entregas podem ser de itens sem NFe eletrônica.
 *
 * <p><strong>status:</strong> segue a máquina de estados definida em {@link StatusEntrega}.
 * Transições inválidas são rejeitadas pelo {@link EntregaService}.
 */
@Entity
@Table(
    name = "entregas",
    indexes = {
        @Index(name = "idx_entregas_nota_filial", columnList = "filial_id, numero_nota_fiscal"),
        @Index(name = "idx_entregas_cliente",     columnList = "filial_id, cliente_nome"),
        @Index(name = "idx_entregas_vendedor",    columnList = "vendedor_id, status"),
        @Index(name = "idx_entregas_motorista",   columnList = "motorista_id, status"),
        @Index(name = "idx_entregas_data_status", columnList = "filial_id, data_prevista_entrega, status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Entrega {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filial_id", nullable = false)
    private Filial filial;

    @Column(name = "numero_nota_fiscal", nullable = false, length = 50)
    private String numeroNotaFiscal;

    /**
     * Chave de acesso NFe — 44 dígitos numéricos, opcional.
     * Formato: cUF + AAMM + CNPJ + mod + serie + nNF + tpEmis + cNF + cDV
     */
    @Column(name = "chave_nfe", length = 44)
    private String chaveNfe;

    @Column(name = "cliente_nome", nullable = false, length = 200)
    private String clienteNome;

    @Column(name = "cliente_documento", length = 20)
    private String clienteDocumento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id")
    private Usuario vendedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motorista_id")
    private Usuario motorista;

    @Column(name = "data_prevista_entrega")
    private LocalDate dataPrevistaEntrega;

    @Column(name = "data_entrega_real")
    private OffsetDateTime dataEntregaReal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private StatusEntrega status = StatusEntrega.PENDENTE;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        criadoEm = OffsetDateTime.now();
        atualizadoEm = criadoEm;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = OffsetDateTime.now();
    }

    /**
     * Verifica se a transição de status é válida.
     * ADMIN pode usar DIVERGENCIA a partir de qualquer estado.
     */
    public boolean transicaoValida(StatusEntrega novoStatus) {
        return switch (this.status) {
            case PENDENTE              -> novoStatus == StatusEntrega.EM_ROTA
                                      || novoStatus == StatusEntrega.DIVERGENCIA;
            case EM_ROTA               -> novoStatus == StatusEntrega.ENTREGUE_SEM_CANHOTO
                                      || novoStatus == StatusEntrega.DIVERGENCIA;
            case ENTREGUE_SEM_CANHOTO  -> novoStatus == StatusEntrega.ENTREGUE_COM_CANHOTO
                                      || novoStatus == StatusEntrega.DIVERGENCIA;
            case ENTREGUE_COM_CANHOTO  -> novoStatus == StatusEntrega.DIVERGENCIA;
            case DIVERGENCIA           -> false; // estado terminal — só ADMIN pode reabrir (via operação específica)
        };
    }
}
