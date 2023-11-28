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
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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

    private CountDownLatch countDownLatch;

    private AtomicInteger nbLignesTraitees;
    private int nbLignesTotal;

    public TopicConsumer(KbartService service, EmailService emailService, ProviderRepository providerRepository, ExecutionReportService executionReportService, LogFileService logFileService) {
        this.service = service;
        this.emailService = emailService;
        this.providerRepository = providerRepository;
        this.executionReportService = executionReportService;
        this.logFileService = logFileService;
        this.countDownLatch = new CountDownLatch(1);
        this.nbLignesTraitees = new AtomicInteger(0);
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
    public void kbartFromkafkaListener(ConsumerRecord<String, String> lignesKbart) {
        log.info("Paquet reçu : Partition : " + lignesKbart.partition() + " / offset " + lignesKbart.offset() + " / value : " + lignesKbart.value());
        try {
            //traitement de chaque ligne kbart
            this.filename = extractFilenameFromHeader(lignesKbart.headers().toArray());
            ThreadContext.put("package", (filename));  //Ajoute le nom de fichier dans le contexte du thread pour log4j
            String providerName = Utils.extractProvider(filename);
            executorService.execute(() -> {
                try {
                    nbLignesTraitees.incrementAndGet();
                    service.processConsumerRecord(lignesKbart, providerName, isForced);
                    log.warn(String.valueOf(nbLignesTraitees.get()));
                    if (nbLignesTraitees.get() == nbLignesTotal) {
                        countDownLatch.countDown();
                        log.debug("CountDownLatch dans kbartFromKafka : " + this.countDownLatch.getCount());
                    }
                } catch (IOException | URISyntaxException | IllegalDoiException e) {
                    //erreurs non bloquantes, on les inscrits dans le rapport, mais on n'arrête pas le programme
                    log.error(e.getMessage());
                    emailService.addLineKbartToMailAttachementWithErrorMessage(e.getMessage());
                    executionReportService.addNbLinesWithInputDataErrors();
                } catch (BestPpnException e) {
                    if (isForced) {
                        //si le programme doit forcer l'insertion, il n'est pas arrêté en cas d'erreur sur le calcul du bestPpn
                        log.error(e.getMessage());
                        emailService.addLineKbartToMailAttachementWithErrorMessage(e.getMessage());
                        executionReportService.addNbLinesWithErrorsInBestPPNSearch();
                    } else {
                        addBestPPNSearchError(e.getMessage());
                        this.countDownLatch.countDown();
                    }
                }
            });
        } catch (IllegalProviderException e) {
            addDataError(e.getMessage());
            this.countDownLatch.countDown();
        }
    }


    @KafkaListener(topics = {"${topic.name.source.nbLines}"}, groupId = "${topic.groupid.source.nbLines}", containerFactory = "kafkaNbLinesListenerContainerFactory")
    public void nbLinesListener(ConsumerRecord<String, String> nbLines) {
        try {
            log.warn("Nombre de lignes traitées par kbart2kafka : " + nbLines.value());
            nbLignesTotal = Integer.parseInt(nbLines.value());
            if (this.filename.equals(extractFilenameFromHeader(nbLines.headers().toArray()))) {
                countDownLatch.await();
                log.debug("Thread débloqué : " + nbLines.value());
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
        } catch (IllegalPackageException | IllegalDateException | IllegalProviderException | ExecutionException | InterruptedException | IOException e) {
            addDataError(e.getMessage());
        } finally {
            log.warn("Traitement terminé pour fichier " + this.filename + " / nb lignes " + nbLignesTraitees);
            emailService.clearMailAttachment();
            executionReportService.clearExecutionReport();
            service.clearListesKbart();
            nbLignesTraitees = new AtomicInteger(0);
            countDownLatch = new CountDownLatch(1);
            clearSharedObjects();

        }
    }

    @KafkaListener(topics = {"${topic.name.source.kbart.errors}"}, groupId = "${topic.groupid.source.errors}", containerFactory = "kafkaKbartListenerContainerFactory")
    public void errorsListener(ConsumerRecord<String, String> error) {
        try {
            if (this.filename.equals(extractFilenameFromHeader(error.headers().toArray()))) {
                //erreur lors du chargement du fichier détectée, on arrête tous les threads en cours pour ne pas envoyer de lignes dans le topic
                executorService.shutdownNow();
                isOnError = true;
                log.error(error.value());
                emailService.addLineKbartToMailAttachementWithErrorMessage(error.value());
                logFileService.createExecutionReport(filename, executionReportService.getExecutionReport(), isForced);
                clearSharedObjects();
            }
        } catch (IOException e) {
            addDataError(e.getMessage());
        }
    }

    private void clearSharedObjects() {
        emailService.clearMailAttachment();
        executionReportService.clearExecutionReport();
        service.clearListesKbart();
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
