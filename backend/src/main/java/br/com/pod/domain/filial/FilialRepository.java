package br.com.pod.domain.filial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FilialRepository extends JpaRepository<Filial, UUID> {
}
