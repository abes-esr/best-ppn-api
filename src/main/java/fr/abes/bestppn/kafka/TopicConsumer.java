package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.exception.*;
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
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.ExecutionException;

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
        long now = Calendar.getInstance().getTimeInMillis();
        //si on a pas reçu de message depuis plus de maxDelayBetweenMessage
        if (this.workInProgress.get(filename).getTimestamp() + maxDelayBetweenMessage < now) {
            workInProgress.remove(filename);
        }
        if (!this.workInProgress.containsKey(filename)) {
            //nouveau fichier trouvé dans le topic, on initialise les variables partagées
            workInProgress.put(filename, new KafkaWorkInProgress(ligneKbart.key().contains("_FORCE"), ligneKbart.key().contains("_BYPASS")));
        }
        try {
            //traitement de chaque ligne kbart
            LigneKbartDto ligneKbartDto = mapper.readValue(ligneKbart.value(), LigneKbartDto.class);
            String providerName = Utils.extractProvider(filename);
            try {
                log.info("Partition;" + ligneKbart.partition() + ";offset;" + ligneKbart.offset() + ";fichier;" + filename + ";" + Thread.currentThread().getName());
                workInProgress.get(filename).incrementThreads();
                int origineNbCurrentLine = ligneKbartDto.getNbCurrentLines();
                ThreadContext.put("package", (filename + ";" + origineNbCurrentLine));  //Ajoute le nom de fichier dans le contexte du thread pour log4j
                service.processConsumerRecord(ligneKbartDto, providerName, workInProgress.get(filename).isForced(), workInProgress.get(filename).isBypassed(), filename);
            } catch (IOException | URISyntaxException | RestClientException | IllegalDoiException e) {
                //erreurs non bloquantes, on n'arrête pas le programme
                log.warn(e.getMessage());
                ligneKbartDto.setErrorType(e.getMessage());
                workInProgress.get(filename).addNbLinesWithInputDataErrorsInExecutionReport();
            } catch (BestPpnException e) {
                if (!workInProgress.get(filename).isForced()) {
                    workInProgress.get(filename).setIsOnError(true);
                } else {
                    workInProgress.get(filename).addKbartToSend(ligneKbartDto);
                }
                log.error(e.getMessage());
                ligneKbartDto.setErrorType(e.getMessage());
                workInProgress.get(filename).addNbLinesWithErrorsInExecutionReport();
            } finally {
                if (ligneKbartDto.getBestPpn() != null && !ligneKbartDto.getBestPpn().isEmpty())
                    workInProgress.get(filename).addNbBestPpnFindedInExecutionReport();
                workInProgress.get(filename).addLineKbartToMailAttachment(ligneKbartDto);
                int nbLignesTotal = ligneKbartDto.getNbLinesTotal();
                int nbCurrentLine = workInProgress.get(filename).incrementNbLignesTraiteesAndGet();
                log.debug("Ligne en cours : {} NbLignesTotal : {}", nbCurrentLine, nbLignesTotal);
                if (nbLignesTotal == nbCurrentLine) {
                    log.debug("Commit du fichier {}", filename);
                    workInProgress.get(filename).setNbtotalLinesInExecutionReport(nbLignesTotal);
                    handleFichier(filename);
                }
                //on ne décrémente pas le nb de thread si l'objet de suivi a été supprimé après la production des messages dans le second topic
                if (workInProgress.get(filename) != null)
                    workInProgress.get(filename).decrementThreads();
            }
        } catch (IllegalProviderException | JsonProcessingException e) {
            workInProgress.get(filename).setIsOnError(true);
            log.warn(e.getMessage());
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
                log.info(filename + " : Thread : " + workInProgress.get(filename).getNbActiveThreads());
            } catch (InterruptedException e) {
                log.warn("Erreur de sleep sur attente fin de traitement");
            }
        } while (workInProgress.get(filename).getNbActiveThreads() > 1);
        try {
            if (!workInProgress.get(filename).isOnError()) {
                String providerName = Utils.extractProvider(filename);
                service.commitDatas(providerName, filename);
                //quel que soit le résultat du traitement, on envoie le rapport par mail
                log.info("Nombre de best ppn trouvé : " + workInProgress.get(filename).getExecutionReport().getNbBestPpnFind() + "/" + workInProgress.get(filename).getExecutionReport().getNbtotalLines());
                logFileService.createExecutionReport(filename, workInProgress.get(filename).getExecutionReport(), workInProgress.get(filename).isForced());
            }
            emailService.sendMailWithAttachment(filename, workInProgress.get(filename).getMailAttachment());
        } catch (ExecutionException | InterruptedException | IOException e) {
            emailService.sendProductionErrorEmail(filename, e.getMessage());
        } catch (IllegalPackageException | IllegalDateException | IllegalProviderException e) {
            log.error("Le nom du fichier " + filename + " n'est pas correct. " + e);
            emailService.sendProductionErrorEmail(filename, e.getMessage());
        } finally {
            log.info("Traitement terminé pour fichier " + filename + " / nb lignes " + workInProgress.get(filename).getNbLignesTraitees());
            workInProgress.remove(filename);
        }
    }

    @KafkaListener(topics = {"${topic.name.source.kbart.errors}"}, groupId = "${topic.groupid.source.errors}", containerFactory = "kafkaKbartListenerContainerFactory")
    public void errorsListener(ConsumerRecord<String, String> error) {
        log.error(error.value());
        String filename = extractFilenameFromKey(error.key());
        if (workInProgress.containsKey(filename)) {
            emailService.sendProductionErrorEmail(filename, error.value());
            logFileService.createExecutionReport(filename, workInProgress.get(filename).getExecutionReport(), workInProgress.get(filename).isForced());
            workInProgress.get(filename).setIsOnError(true);
            handleFichier(filename);
        }
    }

    private String extractFilenameFromKey (String key) {
        return key.substring(0, key.lastIndexOf('_'));
    }
}
