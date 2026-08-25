package br.com.pod.domain.entrega;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resposta de entrega para o cliente.
 *
 * <p>Inclui nomes desnormalizados de vendedor e motorista para evitar
 * N+1 queries no frontend — o controller pede os dados já com JOIN.
 *
 * <p>Dados de cliente ({@code clienteDocumento}) só são retornados para
 * usuários com papel VENDEDOR ou ADMIN. MOTORISTA recebe null neste campo
 * para cumprir o princípio de menor privilégio (LGPD).
 * O mascaramento é feito no {@link EntregaService#toResponse}.
 */
public record EntregaResponse(
        UUID id,
        UUID filialId,
        String filialNome,

        String numeroNotaFiscal,
        String chaveNfe,
        String clienteNome,
        /** Null para MOTORISTA — dados pessoais restritos por papel (LGPD) */
        String clienteDocumento,

        UUID vendedorId,
        String vendedorNome,

        UUID motoristaId,
        String motoristaNome,

        LocalDate dataPrevistaEntrega,
        OffsetDateTime dataEntregaReal,
        StatusEntrega status,

        BigDecimal latitude,
        BigDecimal longitude,
        String observacoes,

        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
    /**
     * Converte a entidade para response, mascarando clienteDocumento para MOTORISTA.
     *
     * @param ocultarDocumento true se o usuário é MOTORISTA (LGPD)
     */
    public static EntregaResponse de(Entrega e, boolean ocultarDocumento) {
        return new EntregaResponse(
                e.getId(),
                e.getFilial().getId(),
                e.getFilial().getNome(),
                e.getNumeroNotaFiscal(),
                e.getChaveNfe(),
                e.getClienteNome(),
                ocultarDocumento ? null : e.getClienteDocumento(),
                e.getVendedor() != null ? e.getVendedor().getId() : null,
                e.getVendedor() != null ? e.getVendedor().getNome() : null,
                e.getMotorista() != null ? e.getMotorista().getId() : null,
                e.getMotorista() != null ? e.getMotorista().getNome() : null,
                e.getDataPrevistaEntrega(),
                e.getDataEntregaReal(),
                e.getStatus(),
                e.getLatitude(),
                e.getLongitude(),
                e.getObservacoes(),
                e.getCriadoEm(),
                e.getAtualizadoEm()
        );
    }
}
