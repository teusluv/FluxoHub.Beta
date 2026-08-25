package br.com.pod.domain.entrega;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Especificações JPA para consultas dinâmicas de entregas.
 *
 * <p>Cada método retorna uma {@link Specification} que pode ser composta
 * com {@code and()} e {@code or()}. Especificações nulas (parâmetro null)
 * retornam {@code conjunction()} — efetivamente sem filtro para aquele campo.
 *
 * <p><strong>Por que Specification em vez de Hibernate @Filter?</strong>
 * Specification é explícita — qualquer código que chama o repositório pode
 * ver exatamente quais filtros são aplicados. @Filter depende de estado de
 * sessão que pode ser esquecido, levando a vazamentos de dados entre filiais.
 * Com Specification, o filtro de filial é passado explicitamente pelo Service,
 * tornando impossível esquecer o filtro de tenant.
 *
 * <p>Todos os parâmetros são tratados com prepared statements pelo JPA Criteria API —
 * sem risco de SQL injection.
 */
public class EntregaSpec {

    private EntregaSpec() {}

    /**
     * Filtro de filial (tenant). Se filialId for null (admin global),
     * retorna conjunction — sem filtro, vê todas as filiais.
     */
    public static Specification<Entrega> porFilial(UUID filialId) {
        return (root, query, cb) -> filialId != null
                ? cb.equal(root.get("filial").get("id"), filialId)
                : cb.conjunction();
    }

    public static Specification<Entrega> porStatus(StatusEntrega status) {
        return (root, query, cb) -> status != null
                ? cb.equal(root.get("status"), status)
                : cb.conjunction();
    }

    public static Specification<Entrega> porVendedor(UUID vendedorId) {
        return (root, query, cb) -> vendedorId != null
                ? cb.equal(root.get("vendedor").get("id"), vendedorId)
                : cb.conjunction();
    }

    public static Specification<Entrega> porMotorista(UUID motoristaId) {
        return (root, query, cb) -> motoristaId != null
                ? cb.equal(root.get("motorista").get("id"), motoristaId)
                : cb.conjunction();
    }

    public static Specification<Entrega> dataPrevistaEntre(LocalDate inicio, LocalDate fim) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (inicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataPrevistaEntrega"), inicio));
            }
            if (fim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataPrevistaEntrega"), fim));
            }
            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Busca de texto livre em número de nota fiscal e nome do cliente.
     * Usa LIKE case-insensitive — para grandes volumes, o GIN index do PostgreSQL
     * (criado na migration V1) cobre esta query via full-text search.
     *
     * <p>O operador {@code ILIKE} seria mais eficiente, mas não é portável para
     * todos os bancos. Em produção com PostgreSQL, considerar native query com
     * {@code to_tsvector} para melhor performance em 100k+ registros.
     */
    public static Specification<Entrega> buscaTexto(String termo) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(termo)) {
                return cb.conjunction();
            }
            String pattern = "%" + termo.toLowerCase().trim() + "%";
            Join<Object, Object> vendedorJoin = root.join("vendedor", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("numeroNotaFiscal")), pattern),
                    cb.like(cb.lower(root.get("clienteNome")), pattern),
                    cb.like(cb.lower(vendedorJoin.get("nome")), pattern)
            );
        };
    }

    /**
     * Constrói a Specification completa a partir do filtro.
     * Ponto central onde o filtro de filial é sempre incluído — nunca pode ser esquecido.
     */
    public static Specification<Entrega> deFiltro(EntregaFiltro filtro, UUID filialId) {
        return Specification
                .where(porFilial(filialId))
                .and(porStatus(filtro.status()))
                .and(porVendedor(filtro.vendedorId()))
                .and(porMotorista(filtro.motoristaId()))
                .and(dataPrevistaEntre(filtro.dataInicio(), filtro.dataFim()))
                .and(buscaTexto(filtro.busca()));
    }
}
