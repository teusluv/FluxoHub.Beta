package br.com.pod.domain.entrega;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Parâmetros de filtro para listagem de entregas.
 * Todos os campos são opcionais — null significa "sem filtro para este campo".
 *
 * <p>Construído a partir dos request params do controller.
 * Registros nulos geram {@code conjunction()} no Specification, não WHERE 1=1.
 */
public record EntregaFiltro(
        UUID vendedorId,
        UUID motoristaId,
        StatusEntrega status,
        LocalDate dataInicio,
        LocalDate dataFim,
        /** Texto livre: busca em numero_nota_fiscal e cliente_nome simultaneamente */
        String busca
) {
    /** Instância sem filtros — retorna todos os registros da filial */
    public static EntregaFiltro vazio() {
        return new EntregaFiltro(null, null, null, null, null, null);
    }
}
