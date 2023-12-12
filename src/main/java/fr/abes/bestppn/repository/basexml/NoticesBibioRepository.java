package fr.abes.bestppn.repository.basexml;

import fr.abes.bestppn.model.entity.basexml.NoticesBibio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NoticesBibioRepository extends JpaRepository<NoticesBibio, Integer> {
    Optional<NoticesBibio> findByPpn(String ppn);
}
