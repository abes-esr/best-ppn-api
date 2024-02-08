package fr.abes.bestppn.service;

import fr.abes.LigneKbartImprime;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalDateException;
import fr.abes.bestppn.exception.IllegalDoiException;
import fr.abes.bestppn.exception.IllegalPackageException;
import fr.abes.bestppn.kafka.KafkaWorkInProgress;
import fr.abes.bestppn.kafka.TopicProducer;
import fr.abes.bestppn.model.BestPpn;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.entity.bacon.Provider;
import fr.abes.bestppn.model.entity.bacon.ProviderPackage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Service
@Slf4j
public class KbartService {
    private final BestPpnService service;

    private final TopicProducer producer;

    private final ProviderService providerService;

    private final Map<String, KafkaWorkInProgress> workInProgress;


    public KbartService(BestPpnService service, TopicProducer producer, ProviderService providerService, Map<String, KafkaWorkInProgress> workInProgress) {
        this.service = service;
        this.producer = producer;
        this.providerService = providerService;
        this.workInProgress = workInProgress;
    }

    @Transactional
    public void processConsumerRecord(LigneKbartDto ligneFromKafka, String providerName, boolean isForced, Boolean isBypassed, String filename) throws IOException, BestPpnException, URISyntaxException, IllegalDoiException {
        log.info("Début calcul BestPpn pour la ligne " + ligneFromKafka);
        if (!isBypassed) {
            if (ligneFromKafka.isBestPpnEmpty()) {
                log.info(ligneFromKafka.toString());
                BestPpn bestPpn = service.getBestPpn(ligneFromKafka, providerName, isForced, false);
                switch (Objects.requireNonNull(bestPpn.getDestination())) {
                    case BEST_PPN_BACON -> ligneFromKafka.setBestPpn(bestPpn.getPpn());
                    case PRINT_PPN_SUDOC -> {
                        if (ligneFromKafka.getPublicationType().equals("monograph")) {
                            workInProgress.get(filename).addPpnToCreate(getLigneKbartImprime(bestPpn, ligneFromKafka));
                        }
                    }
                    case NO_PPN_FOUND_SUDOC -> {
                        if (ligneFromKafka.getPublicationType().equals("monograph")) {
                            workInProgress.get(filename).addPpnFromKbartToCreate(ligneFromKafka);
                        }
                    }
                }
            } else {
                log.info("Bestppn déjà existant sur la ligne : " + ligneFromKafka + ",PPN : " + ligneFromKafka.getBestPpn());
            }
        }
        workInProgress.get(filename).addKbartToSend(ligneFromKafka);
    }

    @Transactional
    public void commitDatas(String providerName, String filename) throws IllegalPackageException, IllegalDateException, ExecutionException, InterruptedException, IOException {
        Optional<Provider> providerOpt = providerService.findByProvider(providerName);
        ProviderPackage provider = providerService.handlerProvider(providerOpt, filename, providerName);
        if (!workInProgress.get(filename).isBypassed()) {
            producer.sendKbart(workInProgress.get(filename).getKbartToSend(), provider, filename);
            producer.sendPrintNotice(workInProgress.get(filename).getPpnToCreate(), filename);
            producer.sendPpnExNihilo(workInProgress.get(filename).getPpnFromKbartToCreate(), provider, filename);
        } else {
            producer.sendBypassToLoad(workInProgress.get(filename).getKbartToSend(), provider, filename);
        }
    }

    private static LigneKbartImprime getLigneKbartImprime(BestPpn bestPpn, LigneKbartDto ligneFromKafka) {
        return LigneKbartImprime.newBuilder()
                .setPpn(bestPpn.getPpn())
                .setPublicationTitle(ligneFromKafka.getPublicationTitle())
                .setPrintIdentifier(ligneFromKafka.getPrintIdentifier())
                .setOnlineIdentifier(ligneFromKafka.getOnlineIdentifier())
                .setDateFirstIssueOnline(ligneFromKafka.getDateFirstIssueOnline())
                .setNumFirstVolOnline(ligneFromKafka.getNumFirstVolOnline())
                .setNumFirstIssueOnline(ligneFromKafka.getNumFirstIssueOnline())
                .setDateLastIssueOnline(ligneFromKafka.getDateLastIssueOnline())
                .setNumLastVolOnline(ligneFromKafka.getNumLastVolOnline())
                .setNumLastIssueOnline(ligneFromKafka.getNumLastIssueOnline())
                .setTitleUrl(ligneFromKafka.getTitleUrl())
                .setFirstAuthor(ligneFromKafka.getFirstAuthor())
                .setTitleId(ligneFromKafka.getTitleId())
                .setEmbargoInfo(ligneFromKafka.getEmbargoInfo())
                .setCoverageDepth(ligneFromKafka.getCoverageDepth())
                .setNotes(ligneFromKafka.getNotes())
                .setPublisherName(ligneFromKafka.getPublisherName())
                .setPublicationType(ligneFromKafka.getPublicationType())
                .setDateMonographPublishedPrint(ligneFromKafka.getDateMonographPublishedPrint())
                .setDateMonographPublishedOnline(ligneFromKafka.getDateMonographPublishedOnline())
                .setMonographVolume(ligneFromKafka.getMonographVolume())
                .setMonographEdition(ligneFromKafka.getMonographEdition())
                .setFirstEditor(ligneFromKafka.getFirstEditor())
                .setParentPublicationTitleId(ligneFromKafka.getParentPublicationTitleId())
                .setPrecedingPublicationTitleId(ligneFromKafka.getPrecedingPublicationTitleId())
                .setAccessType(ligneFromKafka.getAccessType())
                .build();
    }
}
