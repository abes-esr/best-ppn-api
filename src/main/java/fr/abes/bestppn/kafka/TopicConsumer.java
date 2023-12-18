package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.exception.*;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.service.EmailService;
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
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class TopicConsumer {
    private final ObjectMapper mapper;
    private final KbartService service;

    private ExecutorService executorService;

    private final LogFileService logFileService;

    private final EmailService emailService;

    private final Map<String, KafkaWorkInProgress> workInProgress;

    @Value("${spring.kafka.concurrency.nbThread}")
    private int nbThread;


    public TopicConsumer(ObjectMapper mapper, KbartService service, EmailService emailService, LogFileService logFileService, Map<String, KafkaWorkInProgress> workInProgress) {
        this.mapper = mapper;
        this.service = service;
        this.emailService = emailService;
        this.logFileService = logFileService;
        this.workInProgress = workInProgress;
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
        String filename = lignesKbart.key();
        try {
            //traitement de chaque ligne kbart
            if (!this.workInProgress.containsKey(lignesKbart.key())) {
                //nouveau fichier trouvé dans le topic, on initialise les variables partagées
                workInProgress.put(filename, new KafkaWorkInProgress(lignesKbart.key().contains("_FORCE")));
            }
            LigneKbartDto ligneKbartDto = mapper.readValue(lignesKbart.value(), LigneKbartDto.class);
            String providerName = Utils.extractProvider(lignesKbart.key());
            executorService.execute(() -> {
                try {
                    workInProgress.get(filename).incrementThreads();
                    workInProgress.get(filename).incrementNbLignesTraitees();
                    ThreadContext.put("package", (lignesKbart.key()));  //Ajoute le nom de fichier dans le contexte du thread pour log4j
                    service.processConsumerRecord(ligneKbartDto, providerName, workInProgress.get(filename).isForced(), filename);
                    if (ligneKbartDto.getBestPpn() != null && !ligneKbartDto.getBestPpn().isEmpty())
                        workInProgress.get(filename).addNbBestPpnFindedInExecutionReport();
                    workInProgress.get(filename).addLineKbartToMailAttachment(ligneKbartDto);
                    Header lastHeader = lignesKbart.headers().lastHeader("nbLinesTotal");
                    if (lastHeader != null) {
                        int nbLignesTotal = Integer.parseInt(new String(lastHeader.value()));
                        if (nbLignesTotal == workInProgress.get(filename).getNbLignesTraitees() && workInProgress.get(filename).getSemaphore().tryAcquire()) {
                            workInProgress.get(filename).setNbtotalLinesInExecutionReport(nbLignesTotal);
                            handleFichier(filename);
                        }
                    }
                } catch (IOException | URISyntaxException | IllegalDoiException e) {
                    //erreurs non bloquantes, on les inscrits dans le rapport, mais on n'arrête pas le programme
                    log.error(e.getMessage());
                    workInProgress.get(filename).addLineKbartToMailAttachementWithErrorMessage(ligneKbartDto, e.getMessage());
                    workInProgress.get(filename).addNbLinesWithInputDataErrorsInExecutionReport();
                } catch (BestPpnException e) {
                    if (!workInProgress.get(filename).isForced()) {
                        workInProgress.get(filename).setIsOnError(true);
                    }
                    log.error(e.getMessage());
                    workInProgress.get(filename).addLineKbartToMailAttachementWithErrorMessage(ligneKbartDto, e.getMessage());
                    workInProgress.get(filename).addNbLinesWithErrorsInExecutionReport();
                } finally {
                    //on ne décrémente pas le nb de thread si l'objet de suivi a été supprimé après la production des messages dans le second topic
                    if (workInProgress.get(filename) != null)
                        workInProgress.get(filename).decrementThreads();
                }
            });
        } catch (IllegalProviderException | JsonProcessingException e) {
            workInProgress.get(filename).setIsOnError(true);
            log.error(e.getMessage());
            workInProgress.get(filename).addLineKbartToMailAttachementWithErrorMessage(new LigneKbartDto(), e.getMessage());
            workInProgress.get(filename).addNbLinesWithInputDataErrorsInExecutionReport();
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
        } while (workInProgress.get(filename).getNbActiveThreads() > 1);
        try {
            if (workInProgress.get(filename).isOnError()) {
                workInProgress.get(filename).setIsOnError(false);
            } else {
                String providerName = Utils.extractProvider(filename);
                service.commitDatas(providerName, filename);
                //quel que soit le résultat du traitement, on envoie le rapport par mail
                log.info("Nombre de best ppn trouvé : " + workInProgress.get(filename).getExecutionReport().getNbBestPpnFind() + "/" + workInProgress.get(filename).getExecutionReport().getNbtotalLines());
                logFileService.createExecutionReport(filename, workInProgress.get(filename).getExecutionReport(), workInProgress.get(filename).isForced());
            }
            emailService.sendMailWithAttachment(filename, workInProgress.get(filename).getMailAttachment());
        } catch (IllegalPackageException | IllegalDateException | IllegalProviderException | ExecutionException |
                 InterruptedException | IOException e) {
            emailService.sendProductionErrorEmail(filename, e.getMessage());
        } finally {
            log.info("Traitement terminé pour fichier " + filename + " / nb lignes " + workInProgress.get(filename).getNbLignesTraitees());
            workInProgress.remove(filename);
        }
    }

    @KafkaListener(topics = {"${topic.name.source.kbart.errors}"}, groupId = "${topic.groupid.source.errors}", containerFactory = "kafkaKbartListenerContainerFactory")
    public void errorsListener(ConsumerRecord<String, String> error) {
        log.error(error.value());
        String filename = error.key();
        do {
            try {
                //ajout d'un sleep sur la durée du poll kafka pour être sur que le consumer de kbart ait lu au moins une fois
                Thread.sleep(80);
            } catch (InterruptedException e) {
                log.warn("Erreur de sleep sur attente fin de traitement");
            }
        } while (workInProgress.get(filename) != null && workInProgress.get(filename).getNbActiveThreads() != 0);
        if (workInProgress.containsKey(filename)) {
            String fileNameFromError = error.key();
            if (workInProgress.get(filename).getSemaphore().tryAcquire()) {
                emailService.sendProductionErrorEmail(fileNameFromError, error.value());
                logFileService.createExecutionReport(fileNameFromError, workInProgress.get(filename).getExecutionReport(), workInProgress.get(filename).isForced());
                workInProgress.get(filename).setIsOnError(true);
                handleFichier(fileNameFromError);
            }
        }
    }
}
