package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalDateException;
import fr.abes.bestppn.exception.IllegalPackageException;
import fr.abes.bestppn.exception.IllegalProviderException;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.service.EmailService;
import fr.abes.bestppn.service.KbartService;
import fr.abes.bestppn.service.LogFileService;
import fr.abes.bestppn.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClientException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static fr.abes.bestppn.utils.LogMarkers.FUNCTIONAL;
import static fr.abes.bestppn.utils.LogMarkers.TECHNICAL;

@Slf4j
@Service
public class TopicConsumer {
    @Value("${delay.max.topic}")
    private int maxDelayBetweenMessage;
    private final ObjectMapper mapper;
    private final KbartService service;

    private final LogFileService logFileService;

    private final EmailService emailService;

    private final Map<String, KafkaWorkInProgress> workInProgress;


    public TopicConsumer(ObjectMapper mapper, KbartService service, EmailService emailService, LogFileService logFileService, Map<String, KafkaWorkInProgress> workInProgress) {
        this.mapper = mapper;
        this.service = service;
        this.emailService = emailService;
        this.logFileService = logFileService;
        this.workInProgress = workInProgress;
    }


    /**
     * Listener Kafka qui écoute un topic et récupère les messages dès qu'ils y arrivent.
     *
     * @param ligneKbart message kafka récupéré par le Consumer Kafka
     */
    @KafkaListener(topics = {"${topic.name.source.kbart}"}, groupId = "${topic.groupid.source.kbart}", containerFactory = "kafkaKbartListenerContainerFactory", concurrency = "${abes.kafka.concurrency.nbThread}")
    public void kbartFromkafkaListener(ConsumerRecord<String, String> ligneKbart) {
        String filename = extractFilenameFromKey(ligneKbart.key());

        // Initialisation atomique thread-safe pour éviter les écrasements d'instance par des threads concurrents
        KafkaWorkInProgress currentWork = this.workInProgress.computeIfAbsent(filename,
                k -> new KafkaWorkInProgress(ligneKbart.key().contains("_FORCE"), ligneKbart.key().contains("_BYPASS")));

        try {
            // Traitement de chaque ligne kbart
            LigneKbartDto ligneKbartDto = mapper.readValue(ligneKbart.value(), LigneKbartDto.class);
            int nbLignesTotal = ligneKbartDto.getNbLinesTotal();
            currentWork.setTotalLines(nbLignesTotal);
            String providerName = Utils.extractProvider(filename);
            try {
                log.debug(TECHNICAL, "Partition;" + ligneKbart.partition() + ";offset;" + ligneKbart.offset() + ";fichier;" + filename + ";" + Thread.currentThread().getName());
                int origineNbCurrentLine = ligneKbartDto.getNbCurrentLines();
                ThreadContext.put("package", (filename + ";" + origineNbCurrentLine));  // Ajoute le nom de fichier dans le contexte du thread pour log4j
                service.processConsumerRecord(ligneKbartDto, providerName, currentWork.isForced(), currentWork.isBypassed(), filename);
            } catch (IOException | URISyntaxException | RestClientException e) {
                // Erreurs non bloquantes, on n'arrête pas le programme
                log.warn(e.getMessage());
                ligneKbartDto.setErrorType(e.getMessage());
                currentWork.addNbLinesWithInputDataErrorsInExecutionReport();
            } catch (BestPpnException e) {
                if (!currentWork.isForced()) {
                    currentWork.setIsOnError(true);
                }
                log.error(FUNCTIONAL, e.getMessage());
                ligneKbartDto.setErrorType(e.getMessage());
                currentWork.addNbLinesWithErrorsInExecutionReport();
            } finally {
                if (ligneKbartDto.getBestPpn() != null && !ligneKbartDto.getBestPpn().isEmpty())
                    currentWork.addNbBestPpnFindedInExecutionReport();
                currentWork.addLineKbartToMailAttachment(ligneKbartDto);
                // Vérifie si le fichier est complet et déclenche le commit de façon atomique
                checkFileCompletion(filename, currentWork);
            }
        } catch (IllegalProviderException | JsonProcessingException e) {
            currentWork.setIsOnError(true);
            log.warn(e.getMessage());
            currentWork.addLineKbartToMailAttachementWithErrorMessage(new LigneKbartDto(), e.getMessage());
            currentWork.addNbLinesWithInputDataErrorsInExecutionReport();
            // Incrémente le compteur de lignes traitées pour éviter de bloquer le traitement du fichier
            // et libérer l'espace mémoire associé si c'était le dernier message.
            currentWork.incrementCurrentLine();
            // Vérifie également la fin de fichier en cas d'erreur de parsing pour ne pas bloquer le lot en mémoire
            checkFileCompletion(filename, currentWork);
        }
    }

    /**
     * Vérifie si l'ensemble des lignes attendues pour un fichier ont été traitées,
     * et déclenche le commit de façon atomique (un seul thread exécute handleFichier).
     * Utilise >= pour se prémunir d'un dépassement causé par des messages dupliqués Kafka.
     *
     * @param filename nom du fichier en cours
     * @param workInProgressForFile contexte de traitement du fichier
     * @param nbLignesTotal nombre total de lignes attendues
     */
    private void checkFileCompletion(String filename, KafkaWorkInProgress workInProgressForFile) {
        int nbLignesTotal = workInProgressForFile.getTotalLines();
        int nbCurrentLine = workInProgressForFile.getCurrentLine().get();
        log.debug(TECHNICAL, "Ligne en cours : {} NbLignesTotal : {}", nbCurrentLine, nbLignesTotal);
        if (nbLignesTotal > 0 && nbCurrentLine >= nbLignesTotal) {
            // compareAndSet garantit qu'un seul thread effectuera le commit et la fermeture du fichier
            if (workInProgressForFile.getIsCommitting().compareAndSet(false, true)) {
                log.debug(TECHNICAL, "Commit du fichier {}", filename);
                workInProgressForFile.setNbtotalLinesInExecutionReport(nbLignesTotal);
                handleFichier(filename);
            }
        }
    }

    /**
     * Finalise le traitement du fichier : commit des données, envoi du mail récapitulatif
     * et purge explicite des structures en mémoire pour libérer la Heap JVM.
     *
     * @param filename nom du fichier à finaliser
     */
    private void handleFichier(String filename) {
        KafkaWorkInProgress currentWork = workInProgress.get(filename);
        if (currentWork == null) {
            return;
        }
        try {
            if (!currentWork.isOnError()) {
                String providerName = Utils.extractProvider(filename);
                service.commitDatas(providerName, filename);
                // Quel que soit le résultat du traitement, on envoie le rapport par mail
                log.info(FUNCTIONAL, "Nombre de best ppn trouvé : " + currentWork.getExecutionReport().getNbBestPpnFind() + "/" + currentWork.getExecutionReport().getNbtotalLines());
                logFileService.createExecutionReport(filename, currentWork.getExecutionReport(), currentWork.isForced());
            }
            emailService.sendMailWithAttachment(filename, currentWork.getMailAttachment());
        } catch (ExecutionException | InterruptedException | IOException e) {
            emailService.sendProductionErrorEmail(filename, e.getMessage());
        } catch (IllegalPackageException | IllegalDateException e) {
            log.error(FUNCTIONAL, "Le nom du fichier " + filename + " n'est pas correct. " + e);
            emailService.sendProductionErrorEmail(filename, e.getMessage());
        } catch (IllegalProviderException e) {
            log.error(FUNCTIONAL, e.getMessage());
            emailService.sendProductionErrorEmail(filename, e.getMessage());
        } finally {
            log.info(FUNCTIONAL, "Traitement terminé pour fichier " + filename + " / nb lignes " + currentWork.getKbartToSend().size());
            // Nettoyage explicite des collections pour libérer immédiatement la mémoire vive (Heap)
            currentWork.clear();
            workInProgress.remove(filename);
        }
    }

    /**
     * Extrait le nom du fichier à partir de la clé du message Kafka.
     *
     * @param key clé du message Kafka
     * @return nom du fichier extrait
     */
    private String extractFilenameFromKey (String key) {
        return key.substring(0, key.lastIndexOf('_'));
    }

    /**
     * Nettoyage actif périodique des traitements obsolètes pour libérer la Heap.
     * Si un fichier dépasse le délai max sans être finalisé, il est purgé avec un log d'erreur détaillé.
     */
    @Scheduled(fixedDelay = 60000)
    public void cleanExpiredWorkInProgress() {
        log.debug(TECHNICAL, "Lancement du nettoyage des traitements obsoletes");
        long now = Calendar.getInstance().getTimeInMillis();
        this.workInProgress.entrySet().removeIf(entry -> {
            KafkaWorkInProgress work = entry.getValue();
            boolean isExpired = work.getTimestamp() + maxDelayBetweenMessage < now;
            if (isExpired) {
                log.error(TECHNICAL, "Fichier orphelin / obsolète détecté et purgé pour libérer la mémoire : {} (Lignes traitées : {} / Total attendu : {})",
                        entry.getKey(), work.getCurrentLine().get(), work.getTotalLines());
                work.clear();
            }
            return isExpired;
        });
    }
}
