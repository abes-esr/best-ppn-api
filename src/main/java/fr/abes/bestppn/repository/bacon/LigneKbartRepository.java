package fr.abes.bestppn.repository.bacon;

import fr.abes.bestppn.configuration.BaconDbConfiguration;
import fr.abes.bestppn.model.entity.bacon.LigneKbart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@BaconDbConfiguration
public interface LigneKbartRepository extends JpaRepository<LigneKbart, Integer> {

    void deleteAllByIdProviderPackage(Integer idProviderPackage);
}
