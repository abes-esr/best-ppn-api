package fr.abes.bestppn.service;

import fr.abes.LigneKbartImprime;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalDateException;
import fr.abes.bestppn.exception.IllegalDoiException;
import fr.abes.bestppn.exception.IllegalPackageException;
import fr.abes.bestppn.kafka.TopicProducer;
import fr.abes.bestppn.model.BestPpn;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.entity.bacon.Provider;
import fr.abes.bestppn.model.entity.bacon.ProviderPackage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Service
@Slf4j
public class KbartService {
    private final BestPpnService service;

    private final TopicProducer producer;

    private final EmailService serviceMail;

    @Getter
    private final List<LigneKbartDto> kbartToSend = Collections.synchronizedList(new ArrayList<>());

    @Getter
    private final List<LigneKbartImprime> ppnToCreate = Collections.synchronizedList(new ArrayList<>());

    @Getter
    private final List<LigneKbartDto> ppnFromKbartToCreate = Collections.synchronizedList(new ArrayList<>());

    private final ProviderService providerService;

    private final ExecutionReportService executionReportService;


    public KbartService(BestPpnService service, TopicProducer producer, EmailService serviceMail, ProviderService providerService, ExecutionReportService executionReportService) {
        this.service = service;
        this.producer = producer;
        this.serviceMail = serviceMail;
        this.providerService = providerService;
        this.executionReportService = executionReportService;
    }

    @Transactional
    public void processConsumerRecord(LigneKbartDto ligneFromKafka, String providerName, boolean isForced) throws IOException, BestPpnException, URISyntaxException, IllegalDoiException {
        log.info("Début calcul BestPpn pour la ligne " + ligneFromKafka);
        if (ligneFromKafka.isBestPpnEmpty()) {
            log.info(ligneFromKafka.toString());
            BestPpn bestPpn = service.getBestPpn(ligneFromKafka, providerName, isForced, false);
            switch (bestPpn.getDestination()) {
                case BEST_PPN_BACON -> {
                    ligneFromKafka.setBestPpn(bestPpn.getPpn());
                    executionReportService.addNbBestPpnFind();
                }
                case PRINT_PPN_SUDOC -> ppnToCreate.add(getLigneKbartImprime(bestPpn, ligneFromKafka));
                case NO_PPN_FOUND_SUDOC -> {
                    if (ligneFromKafka.getPublicationType().equals("monograph")) {
                        ppnFromKbartToCreate.add(ligneFromKafka);
                    }
                }
            }
        } else {
            log.info("Bestppn déjà existant sur la ligne : " + ligneFromKafka + ",PPN : " + ligneFromKafka.getBestPpn());
        }
        kbartToSend.add(ligneFromKafka);
        serviceMail.addLineKbartToMailAttachment(ligneFromKafka);
    }

    @Transactional
    public void commitDatas(Optional<Provider> providerOpt, String providerName, String filename) throws IllegalPackageException, IllegalDateException, ExecutionException, InterruptedException, IOException {
        ProviderPackage provider = providerService.handlerProvider(providerOpt, filename, providerName);
        producer.sendKbart(kbartToSend, provider, filename);
        producer.sendPrintNotice(ppnToCreate, filename);
        producer.sendPpnExNihilo(ppnFromKbartToCreate, provider, filename);
        clearListesKbart();
    }

    public void clearListesKbart() {
        kbartToSend.clear();
        ppnToCreate.clear();
        ppnFromKbartToCreate.clear();
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
