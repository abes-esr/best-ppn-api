package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
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
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class TopicConsumer {
    private final ObjectMapper mapper;
    private final KbartService service;

    @Value("${spring.kafka.concurrency.nbThread}")
    private int nbThread;
    private final EmailService emailService;

    private String filename = "";

    private boolean isForced = false;

    private final AtomicBoolean isOnError;

    private ExecutorService executorService;

    private final ProviderRepository providerRepository;

    private final ExecutionReportService executionReportService;

    private final LogFileService logFileService;

    private AtomicInteger nbLignesTraitees;

    private final Semaphore semaphore;

    private final AtomicInteger nbActiveThreads;


    public TopicConsumer(ObjectMapper mapper, KbartService service, EmailService emailService, ProviderRepository providerRepository, ExecutionReportService executionReportService, LogFileService logFileService, Semaphore semaphore) {
        this.mapper = mapper;
        this.service = service;
        this.emailService = emailService;
        this.providerRepository = providerRepository;
        this.executionReportService = executionReportService;
        this.logFileService = logFileService;
        this.semaphore = semaphore;
        this.nbLignesTraitees = new AtomicInteger(0);
        this.nbActiveThreads = new AtomicInteger(0);
        this.isOnError = new AtomicBoolean(false);
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
    @KafkaListener(topics = {"${topic.name.source.kbart}"}, groupId = "${topic.groupid.source.kbart}", containerFactory = "kafkaKbartListenerContainerFactory", concurrency = "${spring.kafka.concurrency.nbThread}")
    public void kbartFromkafkaListener(ConsumerRecord<String, String> lignesKbart) {
        log.warn("Paquet reçu : Partition : " + lignesKbart.partition() + " / offset " + lignesKbart.offset() + " / value : " + lignesKbart.value());
        try {
            //traitement de chaque ligne kbart
            this.filename = extractFilenameFromHeader(lignesKbart.headers().toArray());
            LigneKbartDto ligneKbartDto = mapper.readValue(lignesKbart.value(), LigneKbartDto.class);
            String providerName = Utils.extractProvider(filename);
            executorService.execute(() -> {
                try {
                    this.nbActiveThreads.incrementAndGet();
                    this.nbLignesTraitees.incrementAndGet();
                    ThreadContext.put("package", (filename + ";" + nbLignesTraitees.get()));  //Ajoute le nom de fichier dans le contexte du thread pour log4j
                    service.processConsumerRecord(ligneKbartDto, providerName, isForced);
                    Header lastHeader = lignesKbart.headers().lastHeader("nbLinesTotal");
                    if (lastHeader != null) {
                        int nbLignesTotal = Integer.parseInt(new String(lastHeader.value()));
                        if (nbLignesTotal == nbLignesTraitees.get() && semaphore.tryAcquire()) {
                            executionReportService.setNbtotalLines(nbLignesTotal);
                            handleFichier();
                        }
                    }
                } catch (IOException | URISyntaxException | IllegalDoiException e) {
                    //erreurs non bloquantes, on les inscrits dans le rapport, mais on n'arrête pas le programme
                    log.error(e.getMessage());
                    emailService.addLineKbartToMailAttachementWithErrorMessage(ligneKbartDto, e.getMessage());
                    executionReportService.addNbLinesWithInputDataErrors();
                } catch (BestPpnException e) {
                    if (isForced) {
                        //si le programme doit forcer l'insertion, il n'est pas arrêté en cas d'erreur sur le calcul du bestPpn
                        log.error(e.getMessage());
                        emailService.addLineKbartToMailAttachementWithErrorMessage(ligneKbartDto, e.getMessage());
                        executionReportService.addNbLinesWithErrorsInBestPPNSearch();
                    } else {
                        addBestPPNSearchError(ligneKbartDto, e.getMessage());
                    }
                } finally {
                    this.nbActiveThreads.addAndGet(-1);
                }
            });
        } catch (IllegalProviderException | JsonProcessingException e) {
            addDataError(new LigneKbartDto(), e.getMessage());
        }
    }

    private void handleFichier() {
        //on attend que l'ensemble des threads aient terminé de travailler avant de lancer le commit
        do {
            try {
                //ajout d'un sleep sur la durée du poll kafka pour être sur que le consumer de kbart ait lu au moins une fois
                Thread.sleep(80);
            } catch (InterruptedException e) {
                log.warn("Erreur de sleep sur attente fin de traitement");
            }
        } while (this.nbActiveThreads.get() > 1);
        try {
            if (isOnError.get()) {
                log.warn("isOnError à true");
                service.finishLogFile(filename);
                isOnError.set(false);
            } else {
                String providerName = Utils.extractProvider(filename);
                Optional<Provider> providerOpt = providerRepository.findByProvider(providerName);
                service.commitDatas(providerOpt, providerName, filename);
                //quel que soit le résultat du traitement, on envoie le rapport par mail
                log.info("Nombre de best ppn trouvé : " + executionReportService.getExecutionReport().getNbBestPpnFind() + "/" + executionReportService.getExecutionReport().getNbtotalLines());
                logFileService.createExecutionReport(filename, executionReportService.getExecutionReport(), isForced);
            }
            emailService.sendMailWithAttachment(filename);
        } catch (IllegalPackageException | IllegalDateException | IllegalProviderException | ExecutionException |
                 InterruptedException | IOException e) {
            emailService.sendProductionErrorEmail(this.filename, e.getMessage());
        } finally {
            log.info("Traitement terminé pour fichier " + this.filename + " / nb lignes " + nbLignesTraitees);
            emailService.clearMailAttachment();
            executionReportService.clearExecutionReport();
            service.clearListesKbart();
            nbLignesTraitees = new AtomicInteger(0);
            clearSharedObjects();
            semaphore.release();
        }
    }

    @KafkaListener(topics = {"${topic.name.source.kbart.errors}"}, groupId = "${topic.groupid.source.errors}", containerFactory = "kafkaKbartListenerContainerFactory")
    public void errorsListener(ConsumerRecord<String, String> error) {
        log.error(error.value());
        do {
            try {
                //ajout d'un sleep sur la durée du poll kafka pour être sur que le consumer de kbart ait lu au moins une fois
                Thread.sleep(80);
            } catch (InterruptedException e) {
                log.warn("Erreur de sleep sur attente fin de traitement");
            }
        } while (this.nbActiveThreads.get() != 0);
        if (this.filename.equals(extractFilenameFromHeader(error.headers().toArray()))) {
            if (semaphore.tryAcquire()) {
                emailService.sendProductionErrorEmail(this.filename, error.value());
                logFileService.createExecutionReport(filename, executionReportService.getExecutionReport(), isForced);
                isOnError.set(true);
                handleFichier();
            }
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


    private void addBestPPNSearchError(LigneKbartDto ligneKbartDto, String message) {
        isOnError.set(true);
        log.error(message);
        emailService.addLineKbartToMailAttachementWithErrorMessage(ligneKbartDto, message);
        executionReportService.addNbLinesWithErrorsInBestPPNSearch();
    }

    private void addDataError(LigneKbartDto ligneKbartDto, String message) {
        isOnError.set(true);
        log.error(message);
        emailService.addLineKbartToMailAttachementWithErrorMessage(ligneKbartDto, message);
        executionReportService.addNbLinesWithInputDataErrors();
    }
}
