package br.com.pod.domain.usuario;

/**
 * Papel do usuário no sistema.
 *
 * <p>Mapeado como String no banco ({@code VARCHAR(20)}) para legibilidade
 * em queries SQL diretas e auditorias.
 *
 * <p>Hierarquia de permissões:
 * <ul>
 *   <li>MOTORISTA — captura canhotos, vê apenas suas próprias entregas do dia</li>
 *   <li>VENDEDOR — busca entregas e canhotos, não cria/altera</li>
 *   <li>ADMIN — acesso completo à sua filial; se admin_global=true, acessa todas</li>
 * </ul>
 */
public enum Papel {
    MOTORISTA,
    VENDEDOR,
    ADMIN
}
