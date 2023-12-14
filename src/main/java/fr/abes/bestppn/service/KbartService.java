package fr.abes.bestppn.service;

import fr.abes.LigneKbartImprime;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.kafka.PpnWithDestinationDto;
import fr.abes.bestppn.entity.bacon.Provider;
import fr.abes.bestppn.entity.bacon.ProviderPackage;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalDateException;
import fr.abes.bestppn.exception.IllegalDoiException;
import fr.abes.bestppn.exception.IllegalPackageException;
import fr.abes.bestppn.kafka.KafkaWorkInProgress;
import fr.abes.bestppn.kafka.TopicProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
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
    public void processConsumerRecord(LigneKbartDto ligneFromKafka, String providerName, boolean isForced, String filename) throws IOException, BestPpnException, URISyntaxException, IllegalDoiException {
        log.info("Début calcul BestPpn pour la ligne " + ligneFromKafka);
        if (ligneFromKafka.isBestPpnEmpty()) {
            log.info(ligneFromKafka.toString());
            PpnWithDestinationDto ppnWithDestinationDto = service.getBestPpn(ligneFromKafka, providerName, isForced);
            switch (ppnWithDestinationDto.getDestination()) {
                case BEST_PPN_BACON -> ligneFromKafka.setBestPpn(ppnWithDestinationDto.getPpn());
                case PRINT_PPN_SUDOC -> workInProgress.get(filename).addPpnToCreate(getLigneKbartImprime(ppnWithDestinationDto, ligneFromKafka));
                case NO_PPN_FOUND_SUDOC -> {
                    if (ligneFromKafka.getPublicationType().equals("monograph")) {
                        workInProgress.get(filename).addPpnFromKbartToCreate(ligneFromKafka);
                    }
                }
            }
        } else {
            log.info("Bestppn déjà existant sur la ligne : " + ligneFromKafka + ",PPN : " + ligneFromKafka.getBestPpn());
        }
        workInProgress.get(filename).addKbartToSend(ligneFromKafka);
    }

    @Transactional
    public void commitDatas(String providerName, String filename) throws IllegalPackageException, IllegalDateException, ExecutionException, InterruptedException, IOException {
        Optional<Provider> providerOpt = providerService.findByProvider(providerName);
        ProviderPackage provider = providerService.handlerProvider(providerOpt, filename, providerName);
        producer.sendKbart(workInProgress.get(filename).getKbartToSend(), provider, filename);
        producer.sendPrintNotice(workInProgress.get(filename).getPpnToCreate(), filename);
        producer.sendPpnExNihilo(workInProgress.get(filename).getPpnFromKbartToCreate(), provider, filename);
        producer.sendEndOfTraitmentReport(filename);
    }


    private static LigneKbartImprime getLigneKbartImprime(PpnWithDestinationDto ppnWithDestinationDto, LigneKbartDto ligneFromKafka) {
        return LigneKbartImprime.newBuilder()
                .setPpn(ppnWithDestinationDto.getPpn())
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
