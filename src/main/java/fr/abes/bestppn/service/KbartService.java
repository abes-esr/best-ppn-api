package fr.abes.bestppn.service;

import fr.abes.LigneKbartImprime;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalDateException;
import fr.abes.bestppn.exception.IllegalPackageException;
import fr.abes.bestppn.exception.IllegalProviderException;
import fr.abes.bestppn.kafka.KafkaWorkInProgress;
import fr.abes.bestppn.kafka.TopicProducer;
import fr.abes.bestppn.model.BestPpn;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.entity.bacon.Provider;
import fr.abes.bestppn.model.entity.bacon.ProviderPackage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static fr.abes.bestppn.utils.LogMarkers.FUNCTIONAL;

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

    public void processConsumerRecord(LigneKbartDto ligneFromKafka, String providerName, boolean isForced, Boolean isBypassed, String filename) throws IOException, BestPpnException, URISyntaxException {
        log.info(FUNCTIONAL, "Début calcul BestPpn pour la ligne " + ligneFromKafka);
        try {
            if (!isBypassed) {
                if (ligneFromKafka.isBestPpnEmpty()) {
                    log.info(FUNCTIONAL, ligneFromKafka.toString());
                    BestPpn bestPpn = service.getBestPpn(ligneFromKafka, providerName, isForced);
                    switch (Objects.requireNonNull(bestPpn.getDestination())) {
                        case BEST_PPN_BACON -> ligneFromKafka.setBestPpn(bestPpn.getPpn());
                        case PRINT_PPN_SUDOC -> {
                            //on ne lance la création dans le Sudoc que pour les monographie
                            if (ligneFromKafka.getPublicationType().equals("monograph")) {
                                workInProgress.get(filename).addPpnToCreate(getLigneKbartImprime(bestPpn, ligneFromKafka));
                            }
                        }
                        case NO_PPN_FOUND_SUDOC -> {
                            //on ne lance la création dans le Sudoc que pour les monographie
                            if (ligneFromKafka.getPublicationType().equals("monograph")) {
                                workInProgress.get(filename).addPpnFromKbartToCreate(ligneFromKafka);
                            }
                        }
                    }
                } else {
                    log.info(FUNCTIONAL, "Bestppn déjà existant sur la ligne : " + ligneFromKafka + ",PPN : " + ligneFromKafka.getBestPpn());
                }
            }
            workInProgress.get(filename).addKbartToSend(ligneFromKafka);
            //quel que soit le résultat du calcul du best ppn on met à jour le timestamp correspondant à la consommation du message
            workInProgress.get(filename).setTimestamp(Calendar.getInstance().getTimeInMillis());
        } catch (IOException | BestPpnException | URISyntaxException | RestClientException ex) {
            if (isForced) {
                this.workInProgress.get(filename).addKbartToSend(ligneFromKafka);
            } else {
                this.workInProgress.get(filename).incrementCurrentLine();
            }
            throw ex;
        }
    }

    public void commitDatas(String providerName, String filename) throws IllegalPackageException, IllegalDateException, ExecutionException, InterruptedException, IOException, IllegalProviderException {
        Optional<Provider> providerOpt = providerService.findByProvider(providerName);
        if (providerOpt.isPresent()) {
            ProviderPackage provider = providerService.handlerProvider(providerOpt.get(), filename);
            if (!workInProgress.get(filename).isBypassed()) {
                producer.sendKbart(workInProgress.get(filename).getKbartToSend(), provider, filename);
                producer.sendPrintNotice(workInProgress.get(filename).getPpnToCreate(), filename);
                producer.sendPpnExNihilo(workInProgress.get(filename).getPpnFromKbartToCreate(), provider, filename);
            } else {
                producer.sendBypassToLoad(workInProgress.get(filename).getKbartToSend(), provider, filename);
            }
        } else {
            throw new IllegalProviderException("Fichier : " + filename + " / Provider " + providerName + " inconnu");
        }
    }

    private static LigneKbartImprime getLigneKbartImprime(BestPpn bestPpn, LigneKbartDto ligneFromKafka) {
        return LigneKbartImprime.newBuilder()
                .setCurrentLine(ligneFromKafka.getNbCurrentLines())
                .setTotalLines(ligneFromKafka.getNbLinesTotal())
                .setPpn(bestPpn.getPpn())
                .setPublicationTitle(ligneFromKafka.getPublicationTitle())
                .setPrintIdentifier(ligneFromKafka.getPrintIdentifier())
                .setOnlineIdentifier(ligneFromKafka.getOnlineIdentifier())
                .setDateFirstIssueOnline(ligneFromKafka.getDateFirstIssueOnline())
                .setNumFirstVolOnline(null) // /!\ la donnée est mal formatée et pas exploitée coté kafka2sudoc donc null
                .setNumFirstIssueOnline(null)
                .setDateLastIssueOnline(ligneFromKafka.getDateLastIssueOnline())
                .setNumLastVolOnline(null)
                .setNumLastIssueOnline(null)
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
                .setMonographVolume(null)
                .setMonographEdition(ligneFromKafka.getMonographEdition())
                .setFirstEditor(ligneFromKafka.getFirstEditor())
                .setParentPublicationTitleId(ligneFromKafka.getParentPublicationTitleId())
                .setPrecedingPublicationTitleId(ligneFromKafka.getPrecedingPublicationTitleId())
                .setAccessType(ligneFromKafka.getAccessType())
                .build();
    }
}
