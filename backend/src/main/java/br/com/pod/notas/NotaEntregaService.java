package br.com.pod.notas;

import br.com.pod.auditoria.AuditoriaService;
import br.com.pod.domain.entrega.Entrega;
import br.com.pod.domain.nota.*;
import br.com.pod.domain.usuario.Usuario;
import br.com.pod.entregas.EntregaService;
import br.com.pod.shared.exception.PodException;
import br.com.pod.shared.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço responsável pela gestão de notas e observações de entregas.
 */
@Service
public class NotaEntregaService {

    private static final Logger log = LoggerFactory.getLogger(NotaEntregaService.class);

    private final NotaEntregaRepository notaEntregaRepository;
    private final EntregaService entregaService;
    private final AuditoriaService auditoriaService;

    public NotaEntregaService(NotaEntregaRepository notaEntregaRepository,
                              EntregaService entregaService,
                              AuditoriaService auditoriaService) {
        this.notaEntregaRepository = notaEntregaRepository;
        this.entregaService = entregaService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Cria uma nova nota vinculada a uma entrega.
     * Restrito a VENDEDOR e ADMIN.
     */
    @Transactional
    public NotaResponse criar(UUID entregaId, CriarNotaRequest req) {
        Usuario usuario = SecurityUtils.getCurrentUsuario();

        // 1. Validação de papel (Motoristas não podem criar notas nesta fase)
        if (SecurityUtils.isMotorista()) {
            throw new PodException.AcessoNegado("Motoristas possuem acesso somente leitura às notas");
        }

        // 2. Idempotência (evitar duplicatas em retries de rede)
        if (req.idempotencyKey() != null && !req.idempotencyKey().isBlank()) {
            Optional<NotaEntrega> existente = notaEntregaRepository
                    .findByEntregaIdAndIdempotencyKey(entregaId, req.idempotencyKey());
            if (existente.isPresent()) {
                log.info("Nota já existe para entrega={} idempotencyKey={} — retornando existente",
                        entregaId, req.idempotencyKey());
                return NotaResponse.de(existente.get());
            }
        }

        // 3. Valida entrega e escopo de filial / multi-tenant
        Entrega entrega = entregaService.encontrarPorIdComTenant(entregaId);

        // 4. Cria e persiste a nota
        NotaEntrega nota = NotaEntrega.builder()
                .entrega(entrega)
                .filial(entrega.getFilial())
                .autor(usuario)
                .autorNome(usuario.getNome())
                .autorPapel(usuario.getPapel())
                .tipo(req.tipo() != null ? req.tipo() : TipoNota.GERAL)
                .conteudo(req.conteudo().trim())
                .idempotencyKey(req.idempotencyKey())
                .criadoEm(OffsetDateTime.now())
                .build();

        nota = notaEntregaRepository.save(nota);

        // 5. Auditoria
        auditoriaService.registrar(
                "nota_entrega",
                nota.getId(),
                AuditoriaService.NOTA_ENTREGA_CRIADA,
                usuario,
                entrega.getFilial(),
                AuditoriaService.detalhes(
                        "entregaId", entrega.getId(),
                        "numeroNota", entrega.getNumeroNotaFiscal(),
                        "tipo", nota.getTipo().name(),
                        "autorPapel", usuario.getPapel().name()
                )
        );

        log.info("Nota criada com sucesso: id={} entregaId={} tipo={} autor={}",
                nota.getId(), entrega.getId(), nota.getTipo(), usuario.getEmail());

        return NotaResponse.de(nota);
    }

    /**
     * Lista as notas de uma entrega com paginação e isolamento de filial.
     */
    @Transactional(readOnly = true)
    public Page<NotaResponse> listarPorEntrega(UUID entregaId, Pageable pageable) {
        Usuario usuario = SecurityUtils.getCurrentUsuario();

        // Valida acesso ao tenant da entrega
        Entrega entrega = entregaService.encontrarPorIdComTenant(entregaId);

        // Se for motorista, só pode visualizar notas se a entrega pertencer a ele
        if (SecurityUtils.isMotorista()) {
            if (entrega.getMotorista() == null || !entrega.getMotorista().getId().equals(usuario.getId())) {
                throw new PodException.AcessoNegado("Você só pode visualizar notas de suas próprias entregas");
            }
        }

        return notaEntregaRepository
                .findByEntregaIdAndFilialIdOrderByCriadoEmDesc(entrega.getId(), entrega.getFilial().getId(), pageable)
                .map(NotaResponse::de);
    }
}
