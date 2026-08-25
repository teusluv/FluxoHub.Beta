package br.com.pod.domain.entrega;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request para alteração de status de uma entrega.
 * O Service valida se a transição é permitida pela máquina de estados.
 */
public record AtualizarStatusRequest(

        @NotNull(message = "Novo status é obrigatório")
        StatusEntrega novoStatus,

        @Size(max = 500, message = "Observação deve ter no máximo 500 caracteres")
        String observacao
) {}
