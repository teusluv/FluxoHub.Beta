package br.com.pod.entregas;

import br.com.pod.auditoria.AuditoriaService;
import br.com.pod.domain.entrega.*;
import br.com.pod.domain.filial.Filial;
import br.com.pod.domain.filial.FilialRepository;
import br.com.pod.domain.usuario.Papel;
import br.com.pod.domain.usuario.Usuario;
import br.com.pod.domain.usuario.UsuarioRepository;
import br.com.pod.shared.exception.PodException;
import br.com.pod.shared.security.SecurityUtils;
import br.com.pod.shared.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Serviço de entregas — orquestra criação, consulta e transições de status.
 *
 * <p><strong>Multi-tenant:</strong> todo método que consulta dados usa
 * {@link TenantContext#getFilialId()} e passa para {@link EntregaSpec#deFiltro}.
 * Administradores globais têm {@code filialId = null} (sem filtro de tenant).
 *
 * <p><strong>LGPD:</strong> o campo {@code clienteDocumento} é mascarado
 * para usuários com papel MOTORISTA via {@link EntregaResponse#de}.
 *
 * <p><strong>Isolamento de MOTORISTA:</strong> usuários com papel MOTORISTA
 * só visualizam suas próprias entregas. Isso é garantido no service, não no
 * controller, para que qualquer ponto de entrada (WhatsApp bot, API futura)
 * respeite a mesma regra.
 */
@Service
@Transactional(readOnly = true)
public class EntregaService {

    private static final Logger log = LoggerFactory.getLogger(EntregaService.class);

    private final EntregaRepository entregaRepository;
    private final UsuarioRepository usuarioRepository;
    private final FilialRepository filialRepository;
    private final AuditoriaService auditoriaService;
    private final EntregaEventoRepository entregaEventoRepository;

    public EntregaService(EntregaRepository entregaRepository,
                          UsuarioRepository usuarioRepository,
                          FilialRepository filialRepository,
                          AuditoriaService auditoriaService,
                          EntregaEventoRepository entregaEventoRepository) {
        this.entregaRepository = entregaRepository;
        this.usuarioRepository = usuarioRepository;
        this.filialRepository = filialRepository;
        this.auditoriaService = auditoriaService;
        this.entregaEventoRepository = entregaEventoRepository;
    }

    // =========================================================================
    // CRIAÇÃO
    // =========================================================================

    /**
     * Cria uma nova entrega associada à filial do usuário autenticado.
     * Somente ADMIN e VENDEDOR podem criar entregas.
     */
    @Transactional
    public EntregaResponse criar(CriarEntregaRequest req) {
        var usuario = SecurityUtils.getCurrentUsuario();
        var filial = usuario.getFilial();

        // 1. Idempotência por idempotencyKey
        if (req.idempotencyKey() != null && !req.idempotencyKey().trim().isEmpty()) {
            var existente = entregaRepository.findByFilialIdAndIdempotencyKey(filial.getId(), req.idempotencyKey().trim());
            if (existente.isPresent()) {
                log.info("Entrega duplicada detectada via idempotencyKey: {}. Retornando registro existente.", req.idempotencyKey());
                return toResponse(existente.get());
            }
        }

        // 2. Valida Nota Fiscal ativa duplicada na mesma filial
        String notaSanitizada = sanitizarNota(req.numeroNotaFiscal());
        List<Entrega> entregasNf = entregaRepository.findByFilialIdAndNumeroNotaFiscal(filial.getId(), notaSanitizada);
        boolean possuiEntregaAtiva = entregasNf.stream()
                .anyMatch(e -> e.getStatus() == StatusEntrega.PENDENTE || e.getStatus() == StatusEntrega.EM_ROTA);
        if (possuiEntregaAtiva) {
            throw new PodException.Conflito("Nota fiscal %s já vinculada a uma entrega ativa nesta filial.".formatted(req.numeroNotaFiscal()));
        }

        // Valida e resolve referências de usuários — devem pertencer à mesma filial
        Usuario vendedor  = resolverUsuario(req.vendedorId(),  Papel.VENDEDOR,  filial.getId());
        Usuario motorista = resolverUsuario(req.motoristaId(), Papel.MOTORISTA, filial.getId());

        var entrega = Entrega.builder()
                .filial(filial)
                .numeroNotaFiscal(notaSanitizada)
                .chaveNfe(req.chaveNfe())
                .clienteNome(req.clienteNome().trim())
                .clienteDocumento(req.clienteDocumento())
                .vendedor(vendedor)
                .motorista(motorista)
                .dataPrevistaEntrega(req.dataPrevistaEntrega())
                .latitude(req.latitude())
                .longitude(req.longitude())
                .observacoes(req.observacoes())
                .status(StatusEntrega.PENDENTE)
                .idempotencyKey(req.idempotencyKey() != null ? req.idempotencyKey().trim() : null)
                .build();

        entrega = entregaRepository.save(entrega);

        // Registro de Evento de Auditoria específico para a timeline
        var evento = EntregaEvento.builder()
                .entrega(entrega)
                .statusAnterior(null)
                .statusNovo(StatusEntrega.PENDENTE)
                .ator(usuario)
                .origem("WEB_ADMIN")
                .build();
        entregaEventoRepository.save(evento);

        auditoriaService.registrar(
                "entrega", entrega.getId(), AuditoriaService.ENTREGA_CRIADA,
                usuario, filial,
                AuditoriaService.detalhes(
                        "numeroNotaFiscal", entrega.getNumeroNotaFiscal(),
                        "clienteNome", entrega.getClienteNome(),
                        "status", entrega.getStatus().name()
                )
        );

        log.info("Entrega criada: {} NF={} filial={}",
                entrega.getId(), entrega.getNumeroNotaFiscal(), filial.getId());

        return toResponse(entrega);
    }

    // =========================================================================
    // CONSULTA
    // =========================================================================

    /**
     * Lista entregas com paginação e filtros dinâmicos.
     *
     * <p>MOTORISTA: forçado a ver apenas suas próprias entregas,
     * independentemente do filtro passado pelo cliente.
     */
    public Page<EntregaResponse> listar(EntregaFiltro filtro, Pageable pageable) {
        var usuario = SecurityUtils.getCurrentUsuario();
        UUID filialId = TenantContext.getFilialId(); // null se admin global

        // MOTORISTA só vê suas próprias entregas — override o filtro de motorista
        EntregaFiltro filtroEfetivo = filtro;
        if (SecurityUtils.isMotorista()) {
            filtroEfetivo = new EntregaFiltro(
                    filtro.vendedorId(),
                    usuario.getId(), // forçado
                    filtro.status(),
                    filtro.dataInicio(),
                    filtro.dataFim(),
                    filtro.busca()
            );
        }

        Specification<Entrega> spec = EntregaSpec.deFiltro(filtroEfetivo, filialId);
        return entregaRepository.findAll(spec, pageable).map(this::toResponse);
    }

    /**
     * Busca uma entrega pelo ID, respeitando o tenant.
     */
    public EntregaResponse buscarPorId(UUID id) {
        Entrega entrega = encontrarPorIdComTenant(id);

        // MOTORISTA só vê suas próprias entregas
        if (SecurityUtils.isMotorista()) {
            var usuario = SecurityUtils.getCurrentUsuario();
            if (entrega.getMotorista() == null || !entrega.getMotorista().getId().equals(usuario.getId())) {
                throw new PodException.AcessoNegado("Você não tem acesso a esta entrega");
            }
        }

        return toResponse(entrega);
    }

    /**
     * Busca por número de nota fiscal exato — endpoint crítico para a meta de < 10s.
     * Retorna lista pois pode haver múltiplas entregas com a mesma NF (re-entregas).
     */
    public List<EntregaResponse> buscarPorNota(String numeroNota) {
        String notaSanitizada = sanitizarNota(numeroNota);
        UUID filialId = TenantContext.getFilialId();

        List<Entrega> entregas = filialId != null
                ? entregaRepository.findByFilialIdAndNumeroNotaFiscal(filialId, notaSanitizada)
                : entregaRepository.findByNumeroNotaFiscal(notaSanitizada);

        return entregas.stream().map(this::toResponse).toList();
    }

    /**
     * Entregas do dia para um motorista — tela principal do app mobile.
     */
    public List<EntregaResponse> entregasDoDia(UUID motoristaId, LocalDate data) {
        var usuario = SecurityUtils.getCurrentUsuario();
        UUID filialId = TenantContext.getFilialId();

        // MOTORISTA só pode ver as suas próprias
        UUID motoristaEfetivo = SecurityUtils.isMotorista()
                ? usuario.getId()
                : motoristaId;

        if (motoristaEfetivo == null) {
            throw new PodException.Invalido("motoristaId é obrigatório para este endpoint");
        }

        return entregaRepository.findEntregasDoDia(motoristaEfetivo, filialId, data)
                .stream().map(this::toResponse).toList();
    }

    // =========================================================================
    // ATUALIZAÇÃO DE STATUS
    // =========================================================================

    /**
     * Altera o status de uma entrega, validando a transição e registrando auditoria.
     *
     * <p>Transições de status seguem a máquina de estados definida em
     * {@link Entrega#transicaoValida}. ADMINs podem sempre marcar DIVERGENCIA.
     */
    @Transactional
    public EntregaResponse atualizarStatus(UUID id, AtualizarStatusRequest req) {
        var usuario = SecurityUtils.getCurrentUsuario();
        Entrega entrega = encontrarPorIdComTenant(id);
        StatusEntrega statusAnterior = entrega.getStatus();

        // Validação de transição
        if (!entrega.transicaoValida(req.novoStatus())) {
            throw new PodException.Invalido(
                    "Transição inválida: %s → %s".formatted(statusAnterior, req.novoStatus()));
        }

        // MOTORISTA só pode alterar suas próprias entregas
        if (SecurityUtils.isMotorista()) {
            if (entrega.getMotorista() == null || !entrega.getMotorista().getId().equals(usuario.getId())) {
                throw new PodException.AcessoNegado("Você só pode alterar suas próprias entregas");
            }
            // MOTORISTA só pode mover para EM_ROTA ou ENTREGUE_SEM_CANHOTO
            if (req.novoStatus() == StatusEntrega.DIVERGENCIA || req.novoStatus() == StatusEntrega.ENTREGUE_COM_CANHOTO) {
                throw new PodException.AcessoNegado("MOTORISTA não pode definir este status");
            }
        }

        entrega.setStatus(req.novoStatus());
        if (req.novoStatus() == StatusEntrega.ENTREGUE_SEM_CANHOTO
                || req.novoStatus() == StatusEntrega.ENTREGUE_COM_CANHOTO) {
            if (entrega.getDataEntregaReal() == null) {
                entrega.setDataEntregaReal(OffsetDateTime.now());
            }
        }
        if (req.observacao() != null) {
            entrega.setObservacoes(req.observacao());
        }

        entrega = entregaRepository.save(entrega);

        // Registro de Evento de Auditoria para a timeline
        var evento = EntregaEvento.builder()
                .entrega(entrega)
                .statusAnterior(statusAnterior)
                .statusNovo(req.novoStatus())
                .ator(usuario)
                .origem(SecurityUtils.isMotorista() ? "APP_MOTORISTA" : "WEB_ADMIN")
                .build();
        entregaEventoRepository.save(evento);

        auditoriaService.registrar(
                "entrega", entrega.getId(), AuditoriaService.ENTREGA_STATUS_ALTERADO,
                usuario, entrega.getFilial(),
                AuditoriaService.detalhes(
                        "statusAnterior", statusAnterior.name(),
                        "statusNovo", req.novoStatus().name(),
                        "observacao", req.observacao()
                )
        );

        log.info("Status alterado: entrega={} {} → {} por usuário={}",
                id, statusAnterior, req.novoStatus(), usuario.getId());

        return toResponse(entrega);
    }

    // =========================================================================
    // Métodos internos usados pela Fase 3 (upload de canhoto)
    // =========================================================================

    @Transactional
    public Entrega atualizarStatusInterno(UUID entregaId, StatusEntrega novoStatus, Filial filial) {
        Entrega entrega = entregaRepository.findByIdAndFilialId(entregaId, filial.getId())
                .orElseThrow(() -> new PodException.NaoEncontrado("Entrega", entregaId));
        
        StatusEntrega statusAnterior = entrega.getStatus();
        entrega.setStatus(novoStatus);
        if (novoStatus == StatusEntrega.ENTREGUE_SEM_CANHOTO
                || novoStatus == StatusEntrega.ENTREGUE_COM_CANHOTO) {
            if (entrega.getDataEntregaReal() == null) {
                entrega.setDataEntregaReal(OffsetDateTime.now());
            }
        }
        entrega = entregaRepository.save(entrega);

        // Identifica o ator para o evento da timeline
        Usuario ator = null;
        try {
            ator = SecurityUtils.getCurrentUsuario();
        } catch (Exception ex) {
            ator = entrega.getMotorista();
        }
        if (ator == null) {
            ator = entrega.getVendedor();
        }

        var evento = EntregaEvento.builder()
                .entrega(entrega)
                .statusAnterior(statusAnterior)
                .statusNovo(novoStatus)
                .ator(ator)
                .origem("APP_MOTORISTA") // Canhoto upload is mobile originating
                .build();
        entregaEventoRepository.save(evento);

        return entrega;
    }

    public Entrega encontrarPorIdComTenant(UUID id) {
        UUID filialId = TenantContext.getFilialId();
        if (filialId != null) {
            return entregaRepository.findByIdAndFilialId(id, filialId)
                    .orElseThrow(() -> new PodException.NaoEncontrado("Entrega", id));
        }
        // Admin global: busca sem filtro de filial
        return entregaRepository.findById(id)
                .orElseThrow(() -> new PodException.NaoEncontrado("Entrega", id));
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    private EntregaResponse toResponse(Entrega e) {
        // LGPD: MOTORISTA não vê documento do cliente
        boolean ocultarDocumento = SecurityUtils.isMotorista();
        return EntregaResponse.de(e, ocultarDocumento);
    }

    /**
     * Sanitiza o número de nota fiscal: remove espaços, pontos e traços.
     * Evita que variações de formatação quebrem a busca ("12345" vs "1.2345-6").
     */
    private String sanitizarNota(String nota) {
        if (nota == null) return null;
        return nota.trim().replaceAll("[^\\w]", "");
    }

    /**
     * Resolve e valida referência de usuário.
     * Garante que vendedores e motoristas pertençam à mesma filial da entrega.
     */
    private Usuario resolverUsuario(UUID id, Papel papelEsperado, UUID filialId) {
        if (id == null) return null;
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new PodException.NaoEncontrado("Usuário", id));
        if (usuario.getPapel() != papelEsperado) {
            throw new PodException.Invalido(
                    "Usuário %s não tem papel %s".formatted(id, papelEsperado));
        }
        if (!usuario.getFilial().getId().equals(filialId)) {
            throw new PodException.AcessoNegado(
                    "Usuário %s pertence a outra filial".formatted(id));
        }
        return usuario;
    }
}
