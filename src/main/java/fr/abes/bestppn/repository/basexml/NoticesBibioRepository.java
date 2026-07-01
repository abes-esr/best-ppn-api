package fr.abes.bestppn.repository.basexml;

import fr.abes.bestppn.model.entity.basexml.NoticesBibio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NoticesBibioRepository extends JpaRepository<NoticesBibio, Integer> {
    Optional<NoticesBibio> findByPpn(String ppn);

    /**
     * Récupère directement la chaîne XML de la notice à partir de son PPN.
     * Cette méthode permet d'éviter de charger l'entité NoticesBibio complète dans
     * le Persistence Context d'Hibernate pour limiter la consommation mémoire lors
     * des traitements de masse.
     *
     * @param ppn Identifiant de la notice
     * @return Optionnel contenant le XML brut de la notice sous forme de chaîne de caractères
     */
    @Query("select n.dataXml from NoticesBibio n where n.ppn = :ppn")
    Optional<String> findDataXmlByPpn(@Param("ppn") String ppn);
}
