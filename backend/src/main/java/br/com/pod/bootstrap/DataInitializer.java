package br.com.pod.bootstrap;

import br.com.pod.domain.filial.Filial;
import br.com.pod.domain.filial.FilialRepository;
import br.com.pod.domain.usuario.Papel;
import br.com.pod.domain.usuario.Usuario;
import br.com.pod.domain.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Inicializador de dados: cria a filial padrão e o admin inicial se não existirem.
 *
 * <p>Usa os UUIDs fixos da migration V1 para ser idempotente — pode rodar
 * múltiplas vezes sem duplicar dados.
 *
 * <p>Em produção, a senha do admin DEVE ser alterada no primeiro login.
 * Este componente loga um warning se a senha padrão ainda estiver em uso.
 *
 * <p>@Profile("!test"): não executa durante testes de integração (cada teste
 * cria seus próprios dados via fixture).
 */
@Component
@Profile("!test")
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final UUID FILIAL_PADRAO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_PADRAO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String ADMIN_EMAIL = "admin@pod.local";
    private static final String ADMIN_SENHA_PADRAO = "admin@pod2024";

    private final FilialRepository filialRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(FilialRepository filialRepository,
                           UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder) {
        this.filialRepository = filialRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        criarFilialPadrao();
        criarAdminPadrao();
    }

    private void criarFilialPadrao() {
        if (filialRepository.existsById(FILIAL_PADRAO_ID)) {
            return;
        }

        var filial = Filial.builder()
                .id(FILIAL_PADRAO_ID)
                .nome("Matriz Feira de Santana")
                .cidade("Feira de Santana")
                .estado("BA")
                .cnpj("00.000.000/0001-00")
                .build();

        filialRepository.save(filial);
        log.info("Filial padrão criada: {}", filial.getId());
    }

    private void criarAdminPadrao() {
        var adminExistente = usuarioRepository.findByEmailAndAtivoTrue(ADMIN_EMAIL);
        if (adminExistente.isPresent()) {
            Usuario admin = adminExistente.get();
            // Se a senha for o placeholder da migração V1, a gente corrige
            if (admin.getSenhaHash().endsWith("..")) {
                admin.setSenhaHash(passwordEncoder.encode(ADMIN_SENHA_PADRAO));
                usuarioRepository.save(admin);
                log.info("Senha do Admin Padrão corrigida do placeholder para o valor real.");
            } else {
                log.debug("Admin padrão já existe e possui senha válida — ignorando inicialização");
            }
            criarVendedorEMotoristaPadrao();
            return;
        }

        var filial = filialRepository.findById(FILIAL_PADRAO_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Filial padrão não encontrada — verifique a migration V1"));

        var admin = Usuario.builder()
                .id(ADMIN_PADRAO_ID)
                .filial(filial)
                .nome("Administrador")
                .papel(Papel.ADMIN)
                .adminGlobal(true)
                .email(ADMIN_EMAIL)
                .senhaHash(passwordEncoder.encode(ADMIN_SENHA_PADRAO))
                .ativo(true)
                .build();

        usuarioRepository.save(admin);

        // Aviso explícito — não deixar passar despercebido em produção
        log.warn("========================================================");
        log.warn("  ADMIN PADRÃO CRIADO: {} / {}", ADMIN_EMAIL, ADMIN_SENHA_PADRAO);
        log.warn("  ALTERE A SENHA IMEDIATAMENTE NO PRIMEIRO LOGIN!");
        log.warn("========================================================");

        criarVendedorEMotoristaPadrao();
    }

    private void criarVendedorEMotoristaPadrao() {
        var filial = filialRepository.findById(FILIAL_PADRAO_ID)
                .orElseThrow(() -> new IllegalStateException("Filial padrão não encontrada"));

        String vendedorEmail = "vendedor@pod.local";
        if (!usuarioRepository.existsByEmail(vendedorEmail)) {
            var vendedor = Usuario.builder()
                    .id(UUID.fromString("00000000-0000-0000-0000-000000000003"))
                    .filial(filial)
                    .nome("Vendedor Teste")
                    .papel(Papel.VENDEDOR)
                    .email(vendedorEmail)
                    .senhaHash(passwordEncoder.encode("vendedor@pod2024"))
                    .ativo(true)
                    .build();
            usuarioRepository.save(vendedor);
            log.info("Vendedor padrão criado: {} / vendedor@pod2024", vendedorEmail);
        }

        String motoristaEmail = "motorista@pod.local";
        if (!usuarioRepository.existsByEmail(motoristaEmail)) {
            var motorista = Usuario.builder()
                    .id(UUID.fromString("00000000-0000-0000-0000-000000000004"))
                    .filial(filial)
                    .nome("Motorista Teste")
                    .papel(Papel.MOTORISTA)
                    .email(motoristaEmail)
                    .senhaHash(passwordEncoder.encode("motorista@pod2024"))
                    .ativo(true)
                    .build();
            usuarioRepository.save(motorista);
            log.info("Motorista padrão criado: {} / motorista@pod2024", motoristaEmail);
        }
    }
}
