package fr.abes.bestppn.kafka;

import fr.abes.bestppn.entity.bacon.Provider;
import fr.abes.bestppn.exception.*;
import fr.abes.bestppn.repository.bacon.ProviderRepository;
import fr.abes.bestppn.service.EmailService;
import fr.abes.bestppn.service.ExecutionReportService;
import fr.abes.bestppn.service.KbartService;
import fr.abes.bestppn.service.LogFileService;
import fr.abes.bestppn.utils.Utils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class TopicConsumer {
    private final KbartService service;

    @Value("${topic.name.source.kbart}")
    private String topicKbart;
    @Value("${topic.name.source.nbLines}")
    private String topicKbartNbLines;
    @Value("${topic.name.source.kbart.errors}")
    private String topicKbartErrors;

    @Value("${spring.kafka.concurrency.nbThread}")
    private int nbThread;
    private final EmailService emailService;

    private String filename = "";

    private boolean isForced = false;

    private boolean isOnError = false;

    private ExecutorService executorService;

    private final ProviderRepository providerRepository;

    private final ExecutionReportService executionReportService;

    private final LogFileService logFileService;

    private int totalLine = 0;

    private final Semaphore semaphore;

    public TopicConsumer(KbartService service, EmailService emailService, ProviderRepository providerRepository, ExecutionReportService executionReportService, LogFileService logFileService, Semaphore semaphore) {
        this.service = service;
        this.emailService = emailService;
        this.providerRepository = providerRepository;
        this.executionReportService = executionReportService;
        this.logFileService = logFileService;
        this.semaphore = semaphore;
    }

    @PostConstruct
    public void initExecutor() {
        executorService = Executors.newFixedThreadPool(nbThread);
    }

    /**
     * Listener Kafka qui écoute un topic et récupère les messages dès qu'ils y arrivent.
     *
     * @param lignesKbart message kafka récupéré par le Consumer Kafka
     */
    @KafkaListener(topics = {"${topic.name.source.kbart}",}, groupId = "${topic.groupid.source.kbart}", containerFactory = "kafkaKbartListenerContainerFactory", concurrency = "${spring.kafka.concurrency.nbThread}")
    public void listenKbartFromKafka(ConsumerRecord<String, String> lignesKbart) {
        log.info("Paquet reçu : Partition : " + lignesKbart.partition() + " / offset " + lignesKbart.offset() + " / value : " + lignesKbart.value());
        try {
            //traitement de chaque ligne kbart
            if (lignesKbart.topic().equals(topicKbart)) {
                this.filename = extractFilenameFromHeader(lignesKbart.headers().toArray());
                ThreadContext.put("package", (filename));  //Ajoute le nom de fichier dans le contexte du thread pour log4j
                String providerName = Utils.extractProvider(filename);
                executorService.execute(() -> {
                    try {
                        service.processConsumerRecord(lignesKbart, providerName, isForced);
                    } catch (ExecutionException | InterruptedException | IOException | URISyntaxException e) {
                        isOnError = true;
                        log.error(e.getMessage());
                        emailService.addLineKbartToMailAttachementWithErrorMessage(e.getMessage());
                        executionReportService.addNbLinesWithInputDataErrors();
                    } catch (IllegalPpnException | BestPpnException e) {
                        isOnError = true;
                        log.error(e.getMessage());
                        emailService.addLineKbartToMailAttachementWithErrorMessage(e.getMessage());
                        executionReportService.addNbLinesWithErrorsInBestPPNSearch();
                    }
                });
            }
        } catch (IllegalProviderException e) {
            isOnError = true;
            log.error("Erreur dans les données en entrée, provider incorrect");
            emailService.addLineKbartToMailAttachementWithErrorMessage(e.getMessage());
            executionReportService.addNbLinesWithInputDataErrors();
        }

    }

    @KafkaListener(topics = {"${topic.name.source.nbLines}"}, groupId = "nbLinesLocal", containerFactory = "kafkaNbLinesListenerContainerFactory")
    public void listenNbLines(ConsumerRecord<String, String> nbLines) {
        try {
            semaphore.acquire();
            log.info("Permit acquis");
            if (this.filename.equals(extractFilenameFromHeader(nbLines.headers().toArray()))) {
                log.info("condition vérifiée : " + nbLines.value());
                totalLine = Integer.parseInt(nbLines.value());
                if (!isOnError) {
                    String providerName = Utils.extractProvider(filename);
                    Optional<Provider> providerOpt = providerRepository.findByProvider(providerName);
                    service.commitDatas(providerOpt, providerName, filename);
                    //quel que soit le résultat du traitement, on envoie le rapport par mail
                    log.info("Nombre de best ppn trouvé : " + executionReportService.getExecutionReport().getNbBestPpnFind() + "/" + totalLine);
                    emailService.sendMailWithAttachment(filename);
                    logFileService.createExecutionReport(filename, totalLine, executionReportService.getNbLinesOk(), executionReportService.getExecutionReport().getNbLinesWithInputDataErrors(), executionReportService.getExecutionReport().getNbLinesWithErrorsInBestPPNSearch(), isForced);
                    emailService.clearMailAttachment();
                    executionReportService.clearExecutionReport();
                    totalLine = 0;
                } else {
                    isOnError = false;
                }
            }
        } catch (IllegalPackageException | IllegalDateException e) {
            isOnError = true;
            log.error(e.getMessage());
            emailService.addLineKbartToMailAttachementWithErrorMessage(e.getMessage());
            executionReportService.addNbLinesWithErrorsInBestPPNSearch();
        } catch (IllegalProviderException e) {
            isOnError = true;
            log.error("Erreur dans les données en entrée, provider incorrect");
            emailService.addLineKbartToMailAttachementWithErrorMessage(e.getMessage());
            executionReportService.addNbLinesWithInputDataErrors();
        } catch (ExecutionException | InterruptedException | IOException e) {
            isOnError = true;
            log.error(e.getMessage());
            emailService.addLineKbartToMailAttachementWithErrorMessage(e.getMessage());
            executionReportService.addNbLinesWithInputDataErrors();
        } finally {
            semaphore.release();
            log.info("semaphore libéré");
        }
    }

  /* @KafkaListener(topics = {"${topic.name.source.kbart.errors}"}, groupId = "errorsLocal", containerFactory = "kafkaKbartListenerContainerFactory")
    public void listenErrors(ConsumerRecord<String, String> error) {
        executorService.shutdownNow();
        isOnError = true;
        log.error(error.value());
        emailService.addLineKbartToMailAttachementWithErrorMessage(error.value());
        emailService.sendMailWithAttachment(filename);
        logFileService.createExecutionReport(filename, totalLine, executionReportService.getNbLinesOk(), executionReportService.getExecutionReport().getNbLinesWithInputDataErrors(), executionReportService.getExecutionReport().getNbLinesWithErrorsInBestPPNSearch(), isForced);
        emailService.clearMailAttachment();
        executionReportService.clearExecutionReport();
    } */

    private String extractFilenameFromHeader(Header[] headers) {
        String nomFichier = "";
        for (Header header : headers) {
            if (header.key().equals("FileName")) {
                nomFichier = new String(header.value());
                if (nomFichier.contains("_FORCE")) {
                    isForced = true;
                }
            }
        }
        return nomFichier;
    }
}
