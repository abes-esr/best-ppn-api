package fr.abes.bestppn.repository.bacon;

import fr.abes.bestppn.configuration.BaconDbConfiguration;
import fr.abes.bestppn.entity.bacon.ProviderPackage;
import fr.abes.bestppn.entity.bacon.ProviderPackageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;

@Repository
@BaconDbConfiguration
public interface ProviderPackageRepository extends JpaRepository<ProviderPackage, ProviderPackageId> {
    Optional<ProviderPackage> findByProviderPackageId(ProviderPackageId providerPackageId);

    Optional<ProviderPackage> findAllByPackageNameAndProviderIdtProviderAndDateP(String packageName, Integer providerIdtProvider, Date date_P);

}
