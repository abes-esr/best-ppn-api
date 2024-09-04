package fr.abes.bestppn.service;

import fr.abes.bestppn.exception.IllegalDateException;
import fr.abes.bestppn.exception.IllegalPackageException;
import fr.abes.bestppn.exception.IllegalProviderException;
import fr.abes.bestppn.model.entity.bacon.Provider;
import fr.abes.bestppn.model.entity.bacon.ProviderPackage;
import fr.abes.bestppn.repository.bacon.LigneKbartRepository;
import fr.abes.bestppn.repository.bacon.ProviderPackageRepository;
import fr.abes.bestppn.repository.bacon.ProviderRepository;
import fr.abes.bestppn.utils.Utils;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Service
@Slf4j
public class ProviderService {
    private final ProviderPackageRepository providerPackageRepository;

    private final ProviderRepository providerRepository;

    private final LigneKbartRepository ligneKbartRepository;

    public ProviderService(ProviderPackageRepository providerPackageRepository, ProviderRepository providerRepository, LigneKbartRepository ligneKbartRepository) {
        this.providerPackageRepository = providerPackageRepository;
        this.providerRepository = providerRepository;
        this.ligneKbartRepository = ligneKbartRepository;
    }

    @Transactional
    public ProviderPackage handlerProvider(Optional<Provider> providerOpt, String filename) throws IllegalPackageException, IllegalDateException, IllegalProviderException {
        String packageName = Utils.extractPackageName(filename);
        Date packageDate = Utils.extractDate(filename);
        if (providerOpt.isPresent()) {
            Provider provider = providerOpt.get();

            Optional<ProviderPackage> providerPackageOpt = providerPackageRepository.findByPackageNameAndDatePAndProviderIdtProvider(packageName,packageDate,provider.getIdtProvider());
            if( providerPackageOpt.isPresent()){
                log.info("clear row package : " + providerPackageOpt.get());
                ligneKbartRepository.deleteAllByIdProviderPackage(providerPackageOpt.get().getIdProviderPackage());
                return providerPackageOpt.get();
            } else {
                //pas d'info de package, on le crée
                return providerPackageRepository.save(new ProviderPackage(packageName, packageDate, provider.getIdtProvider(), 'N'));
            }
        } else {
            throw new IllegalProviderException("Provider et package inconnu impossible de lancer le traitement");
        }
    }

    public Optional<Provider> findByProvider(String providerName) {
        return providerRepository.findByProvider(providerName);
    }
}
