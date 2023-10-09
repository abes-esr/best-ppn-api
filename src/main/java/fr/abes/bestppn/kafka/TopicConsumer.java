package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.LigneKbartImprime;
import fr.abes.bestppn.dto.PackageKbartDto;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.kafka.PpnKbartProviderDto;
import fr.abes.bestppn.dto.kafka.PpnWithDestinationDto;
import fr.abes.bestppn.entity.bacon.Provider;
import fr.abes.bestppn.entity.bacon.ProviderPackage;
import fr.abes.bestppn.entity.bacon.ProviderPackageId;
import fr.abes.bestppn.exception.*;
import fr.abes.bestppn.repository.bacon.ProviderPackageRepository;
import fr.abes.bestppn.repository.bacon.ProviderRepository;
import fr.abes.bestppn.service.BestPpnService;
import fr.abes.bestppn.service.EmailService;
import fr.abes.bestppn.utils.Utils;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicConsumer {
    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private BestPpnService service;

    @Autowired
    private TopicProducer producer;

    @Autowired
    private EmailService serviceMail;
    private final List<LigneKbartDto> kbartToSend = new ArrayList<>();

    private final List<LigneKbartImprime> ppnToCreate = new ArrayList<>();

    private final List<LigneKbartDto> ppnFromKbartToCreate = new ArrayList<>();

    private final PackageKbartDto mailAttachment = new PackageKbartDto();

    private final ProviderPackageRepository providerPackageRepository;

    private final ProviderRepository providerRepository;

    private boolean isOnError = false;

    private int nbBestPpnFind = 0;

    /**
     * Listener Kafka qui écoute un topic et récupère les messages dès qu'ils y arrivent.
     *
     * @param lignesKbart message kafka récupéré par le Consumer Kafka
     */
    @KafkaListener(topics = {"${topic.name.source.kbart}"}, groupId = "${topic.groupid.source.kbart}", containerFactory = "kafkaKbartListenerContainerFactory")
    public void listenKbartFromKafka(ConsumerRecord<String, String> lignesKbart) {
        try {
            String filename = "";
            String currentLine = "";
            String totalLine = "";
            boolean injectKafka = false;
            for (Header header : lignesKbart.headers().toArray()) {
                if (header.key().equals("FileName")) {
                    filename = new String(header.value());
                    if (filename.contains("_FORCE")) {
                        injectKafka = true;
                    }
                    ThreadContext.put("package", filename);
                } else if (header.key().equals("CurrentLine")) {
                    currentLine = new String(header.value());
                } else if (header.key().equals("TotalLine")) {
                    totalLine = new String(header.value());
                }
            }
            String nbLine = currentLine + "/" + totalLine;
            String providerName = Utils.extractProvider(filename);
            Optional<Provider> providerOpt = providerRepository.findByProvider(providerName);
            if (lignesKbart.value().equals("OK")) {
                if (!isOnError) {
                    ProviderPackage provider = handlerProvider(providerOpt, filename, providerName);
                    // TODO vérifier s'il est pertinent de retirer le "_FORCE" du paramètre FileName du header avant envoi au producer
                    //  fileName = fileName.contains("_FORCE") ? fileName.replace("_FORCE", "") : fileName;
                    producer.sendKbart(kbartToSend, provider, filename);
                    producer.sendPrintNotice(ppnToCreate, filename);
                    producer.sendPpnExNihilo(ppnFromKbartToCreate, provider, filename);
                } else {
                    isOnError = false;
                }
                log.info("Nombre de best ppn trouvé : " + this.nbBestPpnFind + "/" + nbLine);
                this.nbBestPpnFind = 0;
                serviceMail.sendMailWithAttachment(filename, mailAttachment);
                kbartToSend.clear();
                ppnToCreate.clear();
                ppnFromKbartToCreate.clear();
                mailAttachment.clearKbartDto();
            } else {
                LigneKbartDto ligneFromKafka = mapper.readValue(lignesKbart.value(), LigneKbartDto.class);
                if (ligneFromKafka.isBestPpnEmpty()) {
                    log.info("Debut du calcul du bestppn sur la ligne : " + nbLine);
                    log.info(ligneFromKafka.toString());
                    PpnWithDestinationDto ppnWithDestinationDto = service.getBestPpn(ligneFromKafka, providerName, injectKafka);
                    switch (ppnWithDestinationDto.getDestination()) {
                        case BEST_PPN_BACON -> {
                            ligneFromKafka.setBestPpn(ppnWithDestinationDto.getPpn());
                            this.nbBestPpnFind++;
                            kbartToSend.add(ligneFromKafka);
                        }
                        case PRINT_PPN_SUDOC -> {
                            LigneKbartImprime ligne = LigneKbartImprime.newBuilder()
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
                            ppnToCreate.add(ligne);
                        }
                        case NO_PPN_FOUND_SUDOC -> {
                            if (ligneFromKafka.getPublicationType().equals("monograph")) {
                                ppnFromKbartToCreate.add(ligneFromKafka);
                            }
                        }
                    }
                } else {
                    log.info("Bestppn déjà existant sur la ligne : " + nbLine + ", le voici : " + ligneFromKafka.getBestPpn());
                    kbartToSend.add(ligneFromKafka);
                }
                mailAttachment.addKbartDto(ligneFromKafka);
            }
        } catch (IllegalProviderException e) {
            isOnError = true;
            log.error("Erreur dans les données en entrée, provider incorrect");
            addLineToMailAttachementWithErrorMessage(e.getMessage());
        } catch (IllegalPpnException | BestPpnException | IOException | URISyntaxException | RestClientException |
                 IllegalArgumentException e) {
            isOnError = true;
            log.error(e.getMessage());
            addLineToMailAttachementWithErrorMessage(e.getMessage());
        } catch (MessagingException | IllegalPackageException | IllegalDateException e) {
            isOnError = true;
            log.error(e.getMessage());
            addLineToMailAttachementWithErrorMessage(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private ProviderPackage handlerProvider(Optional<Provider> providerOpt, String filename, String providerName) throws IllegalPackageException, IllegalDateException {
        if (providerOpt.isPresent()) {
            Provider provider = providerOpt.get();
            ProviderPackageId providerPackageId = new ProviderPackageId(Utils.extractPackageName(filename), Utils.extractDate(filename), provider.getIdtProvider());
            Optional<ProviderPackage> providerPackage = providerPackageRepository.findByProviderPackageId(providerPackageId);
            //pas d'info de package, on le crée
            return providerPackage.orElseGet(() -> providerPackageRepository.save(new ProviderPackage(providerPackageId, 'N')));
        } else {
            //pas de provider, ni de package, on les crée tous les deux
            Provider newProvider = new Provider(providerName);
            Provider savedProvider = providerRepository.save(newProvider);
            ProviderPackage providerPackage = new ProviderPackage(new ProviderPackageId(Utils.extractPackageName(filename), Utils.extractDate(filename), savedProvider.getIdtProvider()), 'N');
            return providerPackageRepository.save(providerPackage);
        }
    }

    private void addLineToMailAttachementWithErrorMessage(String messageError) {
        LigneKbartDto ligneVide = new LigneKbartDto();
        ligneVide.setErrorType(messageError);
        mailAttachment.addKbartDto(ligneVide);
    }
}
