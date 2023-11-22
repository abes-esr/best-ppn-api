package fr.abes.bestppn.kafka;

import fr.abes.bestppn.entity.bacon.Provider;
import fr.abes.bestppn.exception.*;
import fr.abes.bestppn.repository.bacon.ProviderRepository;
import fr.abes.bestppn.service.EmailService;
import fr.abes.bestppn.service.ExecutionReportService;
import fr.abes.bestppn.service.KbartService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    private final List<Header> headerList = new ArrayList<>();
    private ExecutorService executorService;

    private final ProviderRepository providerRepository;


    private final ExecutionReportService executionReportService;

    public TopicConsumer(KbartService service, EmailService emailService, ProviderRepository providerRepository, ExecutionReportService executionReportService) {
        this.service = service;
        this.emailService = emailService;
        this.providerRepository = providerRepository;
        this.executionReportService = executionReportService;
    }

    @PostConstruct
    public void initExecutor() {
        executorService = Executors.newFixedThreadPool(nbThread);
    }
    /**
     * Listener Kafka qui écoute un topic et récupère les messages dès qu'ils y arrivent.
     * @param lignesKbart message kafka récupéré par le Consumer Kafka
     */
    @KafkaListener(topics = {"${topic.name.source.kbart}, ${topic.name.source.kbart.errors}, ${topic.name.source.nbLines}"}, groupId = "${topic.groupid.source.kbart}", containerFactory = "kafkaKbartListenerContainerFactory", concurrency = "${spring.kafka.concurrency.nbThread}")
    public void listenKbartFromKafka(ConsumerRecord<String, String> lignesKbart) throws Exception {
        log.info("Paquet reçu : Partition : " + lignesKbart.partition() + " / offset " + lignesKbart.offset());
        extractDataFromHeader(lignesKbart.headers().toArray());
        ThreadContext.put("package", (filename));  //Ajoute le nom de fichier dans le contexte du thread pour log4j
        String providerName = Utils.extractProvider(filename);
        Optional<Provider> providerOpt = providerRepository.findByProvider(providerName);
        if (lignesKbart.topic().equals(topicKbart)) {
            executorService.execute(() -> {
                try {
                    service.processConsumerRecord(lignesKbart, providerName, isForced);
                } catch (ExecutionException | InterruptedException | IOException | URISyntaxException e) {
                    isOnError = true;
                    log.error(e.getMessage());
                    emailService.addLineToMailAttachementWithErrorMessage(e.getMessage());
                    executionReportService.addNbLinesWithInputDataErrors();
                } catch (IllegalPpnException |BestPpnException e) {
                    isOnError = true;
                    log.error(e.getMessage());
                    emailService.addLineToMailAttachementWithErrorMessage(e.getMessage());
                    executionReportService.addNbLinesWithErrorsInBestPPNSearch();
                }
            });
        }
        if (lignesKbart.topic().equals(topicKbartNbLines)) {
            executorService.awaitTermination(1, TimeUnit.HOURS);
            try {
                if (!isOnError)
                    service.commitDatas(providerOpt, providerName, filename, headerList, isForced);
            } catch (IllegalPackageException | IllegalDateException e) {
                isOnError = true;
                log.error(e.getMessage());
                emailService.addLineToMailAttachementWithErrorMessage(e.getMessage());
                executionReportService.addNbLinesWithErrorsInBestPPNSearch();
            } catch (IllegalProviderException e) {
                isOnError = true;
                log.error("Erreur dans les données en entrée, provider incorrect");
                emailService.addLineToMailAttachementWithErrorMessage(e.getMessage());
                executionReportService.addNbLinesWithInputDataErrors();
            }
        }
        if (lignesKbart.topic().equals(topicKbartErrors)) {
            executorService.shutdownNow();
        }

    }

    private void extractDataFromHeader(Header[] headers) {
        for (Header header : headers) {
            if (header.key().equals("FileName")) {
                filename = new String(header.value());
                headerList.add(header);
                if (filename.contains("_FORCE")) {
                    isForced = true;
                }
            }
        }
    }
}
