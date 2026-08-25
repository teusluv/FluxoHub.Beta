package br.com.pod.config;

import br.com.pod.domain.usuario.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * UserDetailsService separado da SecurityConfig principal para evitar
 * dependência circular (SecurityConfig → UsuarioRepository → JPA → SecurityConfig).
 */
@Configuration
public class UserDetailsConfig {

    private final UsuarioRepository usuarioRepository;

    public UserDetailsConfig(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return email -> usuarioRepository.findByEmailAndAtivoTrue(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuário não encontrado: " + email));
    }
}
