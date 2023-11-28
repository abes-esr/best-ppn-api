package fr.abes.bestppn.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.LigneKbartImprime;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.kafka.PpnWithDestinationDto;
import fr.abes.bestppn.entity.bacon.Provider;
import fr.abes.bestppn.entity.bacon.ProviderPackage;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalDateException;
import fr.abes.bestppn.exception.IllegalDoiException;
import fr.abes.bestppn.exception.IllegalPackageException;
import fr.abes.bestppn.kafka.TopicProducer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
    private final ObjectMapper mapper;

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


    public KbartService(ObjectMapper mapper, BestPpnService service, TopicProducer producer, EmailService serviceMail, ProviderService providerService, ExecutionReportService executionReportService) {
        this.mapper = mapper;
        this.service = service;
        this.producer = producer;
        this.serviceMail = serviceMail;
        this.providerService = providerService;
        this.executionReportService = executionReportService;
    }

    @Transactional
    public void processConsumerRecord(ConsumerRecord<String, String> lignesKbart, String providerName, boolean isForced) throws IOException, BestPpnException, URISyntaxException, IllegalDoiException {
        LigneKbartDto ligneFromKafka = mapper.readValue(lignesKbart.value(), LigneKbartDto.class);
        log.info("Début calcul BestPpn pour la ligne " + lignesKbart);
        if (ligneFromKafka.isBestPpnEmpty()) {
            log.info(ligneFromKafka.toString());
            PpnWithDestinationDto ppnWithDestinationDto = service.getBestPpn(ligneFromKafka, providerName, isForced);
            switch (ppnWithDestinationDto.getDestination()) {
                case BEST_PPN_BACON -> {
                    ligneFromKafka.setBestPpn(ppnWithDestinationDto.getPpn());
                    executionReportService.addNbBestPpnFind();
                }
                case PRINT_PPN_SUDOC -> ppnToCreate.add(getLigneKbartImprime(ppnWithDestinationDto, ligneFromKafka));
                case NO_PPN_FOUND_SUDOC -> {
                    if (ligneFromKafka.getPublicationType().equals("monograph")) {
                        ppnFromKbartToCreate.add(ligneFromKafka);
                    }
                }
            }
        } else {
            log.info("Bestppn déjà existant sur la ligne : " + lignesKbart + ",PPN : " + ligneFromKafka.getBestPpn());
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
        producer.sendEndOfTraitmentReport(filename);
    }

    public void clearListesKbart() {
        kbartToSend.clear();
        ppnToCreate.clear();
        ppnFromKbartToCreate.clear();
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
