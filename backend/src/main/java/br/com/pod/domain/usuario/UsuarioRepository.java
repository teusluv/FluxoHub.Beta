package br.com.pod.domain.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmailAndAtivoTrue(String email);

    boolean existsByEmail(String email);

    java.util.List<Usuario> findAllByFilialIdAndPapelAndAtivoTrue(UUID filialId, Papel papel);
}
