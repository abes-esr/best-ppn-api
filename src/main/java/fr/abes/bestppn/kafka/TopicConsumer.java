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
import java.util.*;
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

    private ExecutorService executorService;

    private final ProviderRepository providerRepository;

    private final ExecutionReportService executionReportService;

    private final LogFileService logFileService;

    private final EmailService emailService;

    @Value("${spring.kafka.concurrency.nbThread}")
    private int nbThread;

    private final Set<String> filenames = Collections.synchronizedSet(new HashSet<>());

    private final Map<String, AtomicBoolean> isForced = Collections.synchronizedMap(new HashMap<>());

    private final Map<String, AtomicBoolean> isOnError = Collections.synchronizedMap(new HashMap<>());

    private final Map<String, AtomicInteger> nbLignesTraitees = Collections.synchronizedMap(new HashMap<>());

    private final AtomicInteger nbActiveThreads;

    private final Semaphore semaphore;




    public TopicConsumer(ObjectMapper mapper, KbartService service, EmailService emailService, ProviderRepository providerRepository, ExecutionReportService executionReportService, LogFileService logFileService, Semaphore semaphore) {
        this.mapper = mapper;
        this.service = service;
        this.emailService = emailService;
        this.providerRepository = providerRepository;
        this.executionReportService = executionReportService;
        this.logFileService = logFileService;
        this.semaphore = semaphore;
        this.nbActiveThreads = new AtomicInteger(0);
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
            if (!this.filenames.contains(lignesKbart.key())) {
                //nouveau fichier trouvé dans le topic, on initialise les variables partagées
                this.filenames.add(extractFilenameFromKey(lignesKbart.key()));
                this.nbLignesTraitees.put(lignesKbart.key(), new AtomicInteger(0));
                this.isOnError.put(lignesKbart.key(), new AtomicBoolean(false));
                this.isForced.put(lignesKbart.key(), new AtomicBoolean(false));
            }
            LigneKbartDto ligneKbartDto = mapper.readValue(lignesKbart.value(), LigneKbartDto.class);
            String providerName = Utils.extractProvider(lignesKbart.key());
            executorService.execute(() -> {
                try {
                    this.nbActiveThreads.incrementAndGet();
                    this.nbLignesTraitees.get(lignesKbart.key()).incrementAndGet();
                    ThreadContext.put("package", (lignesKbart.key()));  //Ajoute le nom de fichier dans le contexte du thread pour log4j
                    service.processConsumerRecord(ligneKbartDto, providerName, isForced.get(lignesKbart.key()).get());
                    Header lastHeader = lignesKbart.headers().lastHeader("nbLinesTotal");
                    if (lastHeader != null) {
                        int nbLignesTotal = Integer.parseInt(new String(lastHeader.value()));
                        if (nbLignesTotal == nbLignesTraitees.get(lignesKbart.key()).get() && semaphore.tryAcquire()) {
                            executionReportService.setNbtotalLines(nbLignesTotal);
                            handleFichier(lignesKbart.key());
                        }
                    }
                } catch (IOException | URISyntaxException | IllegalDoiException e) {
                    //erreurs non bloquantes, on les inscrits dans le rapport, mais on n'arrête pas le programme
                    log.error(e.getMessage());
                    emailService.addLineKbartToMailAttachementWithErrorMessage(ligneKbartDto, e.getMessage());
                    executionReportService.addNbLinesWithInputDataErrors();
                } catch (BestPpnException e) {
                    if (!isForced.get(lignesKbart.key()).get()) {
                        isOnError.get(lignesKbart.key()).set(true);
                    }
                    log.error(e.getMessage());
                    emailService.addLineKbartToMailAttachementWithErrorMessage(ligneKbartDto, e.getMessage());
                    executionReportService.addNbLinesWithErrorsInBestPPNSearch();
                } finally {
                    this.nbActiveThreads.addAndGet(-1);
                }
            });
        } catch (IllegalProviderException | JsonProcessingException e) {
            isOnError.get(lignesKbart.key()).set(true);
            log.error(e.getMessage());
            emailService.addLineKbartToMailAttachementWithErrorMessage(new LigneKbartDto(), e.getMessage());
            executionReportService.addNbLinesWithInputDataErrors();
        }
    }


    private void handleFichier(String filename) {
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
            if (isOnError.get(filename).get()) {
                isOnError.get(filename).set(false);
            } else {
                String providerName = Utils.extractProvider(filename);
                Optional<Provider> providerOpt = providerRepository.findByProvider(providerName);
                service.commitDatas(providerOpt, providerName, filename);
                //quel que soit le résultat du traitement, on envoie le rapport par mail
                log.info("Nombre de best ppn trouvé : " + executionReportService.getExecutionReport().getNbBestPpnFind() + "/" + executionReportService.getExecutionReport().getNbtotalLines());
                logFileService.createExecutionReport(filename, executionReportService.getExecutionReport(), isForced.get(filename).get());
            }
            emailService.sendMailWithAttachment(filename);
        } catch (IllegalPackageException | IllegalDateException | IllegalProviderException | ExecutionException |
                 InterruptedException | IOException e) {
            emailService.sendProductionErrorEmail(filename, e.getMessage());
        } finally {
            log.info("Traitement terminé pour fichier " + filename + " / nb lignes " + nbLignesTraitees);
            emailService.clearMailAttachment();
            executionReportService.clearExecutionReport();
            service.clearListesKbart();
            nbLignesTraitees.remove(filename);
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
        if (this.filenames.contains(extractFilenameFromKey(error.key()))) {
            String fileNameFromError = error.key();
            if (semaphore.tryAcquire()) {
                emailService.sendProductionErrorEmail(fileNameFromError, error.value());
                logFileService.createExecutionReport(fileNameFromError, executionReportService.getExecutionReport(), isForced.get(fileNameFromError).get());
                isOnError.get(fileNameFromError).set(true);
                handleFichier(fileNameFromError);
            }
        }
    }

    private void clearSharedObjects() {
        emailService.clearMailAttachment();
        executionReportService.clearExecutionReport();
        service.clearListesKbart();
    }

    private String extractFilenameFromKey(String key) {
        if (key.contains("_FORCE")) {
            this.isForced.get(key).set(true);
        }
        return key;
    }
}
