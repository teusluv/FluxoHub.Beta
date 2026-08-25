package br.com.pod.domain.entrega;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request para criação de entrega.
 *
 * <p>Campos de segurança importantes:
 * <ul>
 *   <li>{@code numeroNotaFiscal}: stripped de espaços e caracteres especiais
 *       no service para evitar injection via chave de busca</li>
 *   <li>{@code chaveNfe}: validado como 44 dígitos numéricos se fornecido</li>
 *   <li>IDs de usuários (vendedor/motorista) são validados no service
 *       para garantir que pertencem à mesma filial</li>
 * </ul>
 */
public record CriarEntregaRequest(

        @NotBlank(message = "Número da nota fiscal é obrigatório")
        @Size(max = 50, message = "Número da nota fiscal deve ter no máximo 50 caracteres")
        String numeroNotaFiscal,

        @Pattern(regexp = "\\d{44}", message = "Chave NFe deve ter exatamente 44 dígitos numéricos")
        String chaveNfe,

        @NotBlank(message = "Nome do cliente é obrigatório")
        @Size(max = 200, message = "Nome do cliente deve ter no máximo 200 caracteres")
        String clienteNome,

        @Size(max = 20, message = "Documento do cliente deve ter no máximo 20 caracteres")
        String clienteDocumento,

        UUID vendedorId,
        UUID motoristaId,

        LocalDate dataPrevistaEntrega,

        BigDecimal latitude,
        BigDecimal longitude,

        @Size(max = 1000, message = "Observações devem ter no máximo 1000 caracteres")
        String observacoes,

        String idempotencyKey
) {}
