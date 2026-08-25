package br.com.pod.auditoria;

import br.com.pod.domain.filial.Filial;
import br.com.pod.domain.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Evento de auditoria — registro imutável de toda ação sobre entidades críticas.
 *
 * <p><strong>Esta tabela é append-only.</strong> Nenhum método do repositório
 * deve permitir UPDATE ou DELETE. O service de auditoria só expõe o método
 * {@code registrar()} — sem método de deleção ou atualização.
 *
 * <p>O campo {@code detalhes} (JSONB) armazena snapshot dos dados relevantes:
 * status anterior, status novo, motivo, dados do canhoto, etc.
 * Usa {@link JdbcTypeCode} do Hibernate 6 para serialização automática via Jackson.
 *
 * <p>Ações padronizadas ({@code acao}):
 * <ul>
 *   <li>ENTREGA_CRIADA</li>
 *   <li>ENTREGA_STATUS_ALTERADO</li>
 *   <li>CANHOTO_CAPTURADO</li>
 *   <li>CANHOTO_SINCRONIZADO</li>
 *   <li>CANHOTO_INVALIDADO</li>
 *   <li>OCR_CONCLUIDO</li>
 *   <li>OCR_NECESSITA_REVISAO</li>
 * </ul>
 */
@Entity
@Table(
    name = "eventos_auditoria",
    indexes = {
        @Index(name = "idx_auditoria_entidade",    columnList = "entidade, entidade_id"),
        @Index(name = "idx_auditoria_usuario",     columnList = "usuario_id, criado_em"),
        @Index(name = "idx_auditoria_filial_data", columnList = "filial_id, criado_em")
    }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Filial relacionada ao evento. Null para eventos de sistema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filial_id")
    private Filial filial;

    @Column(nullable = false, length = 50)
    private String entidade;

    @Column(name = "entidade_id", nullable = false)
    private UUID entidadeId;

    @Column(nullable = false, length = 50)
    private String acao;

    /** Usuário que realizou a ação. Null para ações de sistema (OCR, sync automático). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /**
     * Dados contextuais do evento em JSONB.
     * Hibernate 6 serializa o Map<String, Object> automaticamente via Jackson.
     * Exemplos de conteúdo:
     * - {"statusAnterior": "EM_ROTA", "statusNovo": "ENTREGUE_SEM_CANHOTO"}
     * - {"confiancaOcr": 0.85, "textoExtraido": "NF 12345"}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> detalhes;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @PrePersist
    void prePersist() {
        criadoEm = OffsetDateTime.now();
    }

    // NÃO há setters — a entidade é imutável após criação (append-only)
    // NÃO há @PreUpdate — esta entidade nunca é atualizada
}
