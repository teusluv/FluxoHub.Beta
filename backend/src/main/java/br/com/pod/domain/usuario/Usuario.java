package br.com.pod.domain.usuario;

import br.com.pod.domain.filial.Filial;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Usuário do sistema.
 *
 * <p>Implementa {@link UserDetails} do Spring Security para integração direta
 * com o mecanismo de autenticação. Cada usuário pertence a uma {@link Filial}
 * (multi-tenant) e possui um papel ({@link Papel}) que define suas permissões.
 *
 * <p>Usuários com {@code papel = ADMIN} e {@code adminGlobal = true} podem
 * acessar dados de todas as filiais — flag usada apenas para gestores corporativos.
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filial_id", nullable = false)
    private Filial filial;

    @Column(nullable = false, length = 150)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Papel papel;

    /**
     * Somente usuários com papel=ADMIN podem ter adminGlobal=true.
     * Quando true, o FilialContextFilter não aplica filtro de filial.
     */
    @Column(name = "admin_global", nullable = false)
    @Builder.Default
    private boolean adminGlobal = false;

    @Column(name = "telefone_whatsapp", length = 20)
    private String telefoneWhatsapp;

    @Column(unique = true, length = 150)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 255)
    private String senhaHash;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        criadoEm = OffsetDateTime.now();
        atualizadoEm = criadoEm;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = OffsetDateTime.now();
    }

    // ---- UserDetails -------------------------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Usamos o nome do enum diretamente como authority (sem prefixo ROLE_).
        // As regras de segurança devem usar hasAuthority("ADMIN") e não hasRole("ADMIN").
        return List.of(new SimpleGrantedAuthority(papel.name()));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return ativo;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ativo;
    }
}
