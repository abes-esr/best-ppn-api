package fr.abes.bestppn.repository.bacon;

import fr.abes.bestppn.configuration.BaconDbConfiguration;
import fr.abes.bestppn.model.entity.bacon.ProviderPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;

@Repository
@BaconDbConfiguration
public interface ProviderPackageRepository extends JpaRepository<ProviderPackage, Integer> {
    Optional<ProviderPackage> findByPackageNameAndDatePAndProviderIdtProvider(String packageName, Date dateP, Integer providerIdtProvider);

}
