package fr.abes.bestppn.repository.bacon;

import fr.abes.bestppn.configuration.BaconDbConfiguration;
import fr.abes.bestppn.entity.bacon.LigneKbart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@BaconDbConfiguration
public interface LigneKbartRepository extends JpaRepository<LigneKbart, Integer> {

    @Transactional
    void deleteAllByIdProviderPackage(Integer idProviderPackage);
}
