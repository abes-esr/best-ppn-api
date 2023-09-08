package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
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
import fr.abes.bestppn.service.LogFileService;
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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

    @Autowired
    private LogFileService logFileService;

    private final List<LigneKbartDto> kbartToSend = new ArrayList<>();

    private final List<PpnKbartProviderDto> ppnToCreate = new ArrayList<>();

    private final PackageKbartDto mailAttachment = new PackageKbartDto();

    private final ProviderPackageRepository providerPackageRepository;

    private final ProviderRepository providerRepository;

    private boolean isOnError = false;

    private int nbBestPpnFind = 0;

    private int linesWithInputDataErrors = 0;

    private int linesWithErrorsInBestPPNSearch = 0;

    /**
     * Listener Kafka qui écoute un topic et récupère les messages dès qu'ils y arrivent.
     * @param lignesKbart message kafka récupéré par le Consumer Kafka
     */
    @KafkaListener(topics = {"${topic.name.source.kbart}"}, groupId = "lignesKbart", containerFactory = "kafkaKbartListenerContainerFactory")
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
                    if (providerOpt.isPresent()) {
                        Provider provider = providerOpt.get();
                        ProviderPackageId providerPackageId = new ProviderPackageId(Utils.extractPackageName(filename), Utils.extractDate(filename), provider.getIdtProvider());
                        Optional<ProviderPackage> providerPackage = providerPackageRepository.findByProviderPackageId(providerPackageId);
                        //pas d'info de package, on le crée
                        providerPackage.orElseGet(() -> providerPackageRepository.save(new ProviderPackage(providerPackageId, 'N')));
                    } else {
                        //pas de provider, ni de package, on les crée tous les deux
                        Provider newProvider = new Provider(providerName);
                        Provider savedProvider = providerRepository.save(newProvider);
                        ProviderPackage providerPackage = new ProviderPackage(new ProviderPackageId(Utils.extractPackageName(filename), Utils.extractDate(filename), savedProvider.getIdtProvider()), 'N');
                        providerPackageRepository.save(providerPackage);
                    }
                    producer.sendKbart(kbartToSend, lignesKbart.headers());
                    producer.sendPrintNotice(ppnToCreate, lignesKbart.headers());
                } else {
                    isOnError = false;
                }
                log.info("Nombre de best ppn trouvé : " + this.nbBestPpnFind + "/" + nbLine);
                this.nbBestPpnFind = 0;
                serviceMail.sendMailWithAttachment(filename, mailAttachment);
                producer.sendEndOfTraitmentReport(lignesKbart.headers()); // Appel le producer pour l'envoi du message de fin de traitement.
                logFileService.createFileLog(filename, Integer.parseInt(totalLine), Integer.parseInt(totalLine) - this.linesWithInputDataErrors - this.linesWithErrorsInBestPPNSearch, this.linesWithInputDataErrors, this.linesWithErrorsInBestPPNSearch);
                kbartToSend.clear();
                ppnToCreate.clear();
                mailAttachment.clearKbartDto();
                this.linesWithInputDataErrors = 0;
                this.linesWithErrorsInBestPPNSearch = 0;
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
                        case PRINT_PPN_SUDOC ->
                                ppnToCreate.add(new PpnKbartProviderDto(ppnWithDestinationDto.getPpn(), ligneFromKafka, providerName));
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
            linesWithInputDataErrors++;
        } catch (IllegalPpnException | BestPpnException | IOException | URISyntaxException | RestClientException |
                 IllegalArgumentException e) {
            isOnError = true;
            log.error(e.getMessage());
            addLineToMailAttachementWithErrorMessage(e.getMessage());
            linesWithErrorsInBestPPNSearch++;
        } catch (IllegalPackageException | IllegalDateException e) {
            isOnError = true;
            log.error(e.getMessage());
            addLineToMailAttachementWithErrorMessage(e.getMessage());
            throw new RuntimeException(e);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    private void addLineToMailAttachementWithErrorMessage(String messageError) {
        LigneKbartDto ligneVide = new LigneKbartDto();
        ligneVide.setErrorType(messageError);
        mailAttachment.addKbartDto(ligneVide);
    }
}
