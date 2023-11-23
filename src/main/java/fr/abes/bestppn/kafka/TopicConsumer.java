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

    private final Semaphore semaphoreNbLines;


    public TopicConsumer(KbartService service, EmailService emailService, ProviderRepository providerRepository, ExecutionReportService executionReportService, LogFileService logFileService, Semaphore semaphoreNbLines) {
        this.service = service;
        this.emailService = emailService;
        this.providerRepository = providerRepository;
        this.executionReportService = executionReportService;
        this.logFileService = logFileService;
        this.semaphoreNbLines = semaphoreNbLines;
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
            this.filename = extractFilenameFromHeader(lignesKbart.headers().toArray());
            ThreadContext.put("package", (filename));  //Ajoute le nom de fichier dans le contexte du thread pour log4j
            String providerName = Utils.extractProvider(filename);
            executorService.execute(() -> {
                try {
                    service.processConsumerRecord(lignesKbart, providerName, isForced);
                } catch (ExecutionException | InterruptedException | IOException | URISyntaxException e) {
                    addDataError(e.getMessage());
                } catch (IllegalPpnException | BestPpnException e) {
                    addBestPPNSearchError(e.getMessage());
                }
            });
        } catch (IllegalProviderException e) {
            addDataError(e.getMessage());
        }
    }


    @KafkaListener(topics = {"${topic.name.source.nbLines}"}, groupId = "${topic.groupid.source.nbLines}", containerFactory = "kafkaNbLinesListenerContainerFactory")
    public void listenNbLines(ConsumerRecord<String, String> nbLines) {
        try {
            semaphoreNbLines.acquire();
            log.debug("Permit acquis");
            if (this.filename.equals(extractFilenameFromHeader(nbLines.headers().toArray()))) {
                log.info("condition vérifiée : " + nbLines.value());
                executionReportService.setNbtotalLines(Integer.parseInt(nbLines.value()));
                if (!isOnError) {
                    String providerName = Utils.extractProvider(filename);
                    Optional<Provider> providerOpt = providerRepository.findByProvider(providerName);
                    service.commitDatas(providerOpt, providerName, filename);
                    //quel que soit le résultat du traitement, on envoie le rapport par mail
                    log.info("Nombre de best ppn trouvé : " + executionReportService.getExecutionReport().getNbBestPpnFind() + "/" + executionReportService.getExecutionReport().getNbtotalLines());
                    emailService.sendMailWithAttachment(filename);
                    logFileService.createExecutionReport(filename, executionReportService.getExecutionReport(), isForced);
                } else {
                    isOnError = false;
                }
            }
        } catch (IllegalPackageException | IllegalDateException e) {
            addBestPPNSearchError(e.getMessage());
        } catch (IllegalProviderException | ExecutionException | InterruptedException | IOException e) {
            addDataError(e.getMessage());
        } finally {
            semaphoreNbLines.release();
            log.debug("semaphore libéré");
            emailService.clearMailAttachment();
            executionReportService.clearExecutionReport();
            service.clearListesKbart();
        }
    }

    @KafkaListener(topics = {"${topic.name.source.kbart.errors}"}, groupId = "${topic.groupid.source.errors}", containerFactory = "kafkaKbartListenerContainerFactory")
    public void listenErrors(ConsumerRecord<String, String> error) {
        try {
            if (this.filename.equals(extractFilenameFromHeader(error.headers().toArray()))) {
                //erreur lors du chargement du fichier détectée, on arrête tous les threads en cours pour ne pas envoyer de lignes dans le topic
                executorService.shutdown();
                isOnError = true;
                log.error(error.value());
                emailService.addLineKbartToMailAttachementWithErrorMessage(error.value());
                logFileService.createExecutionReport(filename, executionReportService.getExecutionReport(), isForced);
                emailService.clearMailAttachment();
                executionReportService.clearExecutionReport();
                service.clearListesKbart();
            }
        } catch (IOException e) {
            addDataError(e.getMessage());
        }
    }

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


    private void addBestPPNSearchError(String message) {
        isOnError = true;
        log.error(message);
        emailService.addLineKbartToMailAttachementWithErrorMessage(message);
        executionReportService.addNbLinesWithErrorsInBestPPNSearch();
    }

    private void addDataError(String message) {
        isOnError = true;
        log.error(message);
        emailService.addLineKbartToMailAttachementWithErrorMessage(message);
        executionReportService.addNbLinesWithInputDataErrors();
    }
}
