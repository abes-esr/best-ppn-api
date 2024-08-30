package fr.abes.bestppn.kafka;

import fr.abes.LigneKbartConnect;
import fr.abes.LigneKbartImprime;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.entity.bacon.ProviderPackage;
import fr.abes.bestppn.utils.UtilsMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicProducer {
    @Value("${abes.kafka.concurrency.nbThread}")
    private int nbThread;

    @Value("${topic.name.target.kbart}")
    private String topicKbart;

    @Value("${topic.name.target.noticeimprime}")
    private String topicNoticeImprimee;

    @Value("${topic.name.target.endoftraitment}")
    private String topicEndOfTraitment;

    @Value("${topic.name.target.ppnFromKbart}")
    private String topicKbartPpnToCreate;

    @Value("${topic.name.target.kbart.bypass.toload}")
    private String topicKbartBypassToload;

    private KafkaTemplate<String, LigneKbartConnect> kafkaTemplateConnect;

    private KafkaTemplate<String, LigneKbartImprime> kafkaTemplateImprime;

    private ExecutorService executorService;

    private UtilsMapper utilsMapper;

    @Autowired
    public TopicProducer(KafkaTemplate<String, LigneKbartConnect> kafkaTemplateConnect, KafkaTemplate<String, LigneKbartImprime> kafkaTemplateImprime, UtilsMapper utilsMapper) {
        this.kafkaTemplateConnect = kafkaTemplateConnect;
        this.kafkaTemplateImprime = kafkaTemplateImprime;
        this.utilsMapper = utilsMapper;
    }

    @PostConstruct
    public void initExecutor() {
        executorService = Executors.newFixedThreadPool(nbThread);
    }

    /**
     * Méthode d'envoi d'une ligne kbart vers topic kafka pour chargement
     *
     * @param kbart    : ligne kbart à envoyer
     * @param provider : provider
     * @param filename : nom du fichier du traitement en cours
     */
    public void sendKbart(List<LigneKbartDto> kbart, ProviderPackage provider, String filename) {
        sendToTopic(kbart, provider, filename, topicKbart);
    }

    /**
     * Méthode d'nvoi d'une ligne kbart vers topic kafka si option bypass activée
     * @param kbart     : ligne kbart à envoyer
     * @param provider  : provider
     * @param filename  : nom du fichier du traitement en cours
     */
    public void sendBypassToLoad(List<LigneKbartDto> kbart, ProviderPackage provider, String filename) {
        sendToTopic(kbart, provider, filename, topicKbartBypassToload);
    }

    private void sendToTopic(List<LigneKbartDto> kbart, ProviderPackage provider, String filename, String destinationTopic) {
        Integer nbLigneTotal = kbart.size();
        AtomicInteger index = new AtomicInteger(0);
        for (LigneKbartDto ligneKbartDto : kbart) {
            ligneKbartDto.setIdProviderPackage(provider.getIdProviderPackage());
            ligneKbartDto.setProviderPackagePackage(provider.getPackageName());
            ligneKbartDto.setProviderPackageDateP(provider.getDateP());
            ligneKbartDto.setProviderPackageIdtProvider(provider.getProviderIdtProvider());
            LigneKbartConnect ligneKbartConnect = utilsMapper.map(ligneKbartDto, LigneKbartConnect.class);
            List<Header> headerList = new ArrayList<>();
            executorService.execute(() -> {
                headerList.add(new RecordHeader("nbLinesTotal", String.valueOf(nbLigneTotal).getBytes()));
                ProducerRecord<String, LigneKbartConnect> record = new ProducerRecord<>(destinationTopic, new Random().nextInt(nbThread), filename+"_"+index.getAndIncrement(), ligneKbartConnect, headerList);
                CompletableFuture<SendResult<String, LigneKbartConnect>> result = kafkaTemplateConnect.send(record);
                assert result != null : "Result est null, donc exception";
                result.whenComplete((sr, ex) -> {
                    try {
                        logEnvoi(result.get(), record);
                    } catch (InterruptedException | ExecutionException e) {
                        log.warn("erreur de récupération du résultat de l'envoi");
                    }
                });
            });
        }
    }

    /**
     * Méthode d'envoi d'une ligne kbart pour création d'une notice à partir de la version imprimée
     *
     * @param ligneKbartImprimes : liste de kbart
     * @param filename           : nom du fichier à traiter
     */
    public void sendPrintNotice(List<LigneKbartImprime> ligneKbartImprimes, String filename) {
        Integer nbLigneTotal = ligneKbartImprimes.size();
        int index = 0;
        for (LigneKbartImprime ppnToCreate : ligneKbartImprimes) {
            sendNoticeImprime(ppnToCreate, topicNoticeImprimee, filename+"_"+(index++), nbLigneTotal);
        }
        if (!ligneKbartImprimes.isEmpty())
            log.debug("message envoyé vers {}", topicNoticeImprimee);
    }


    /**
     * Méthode d'envoi d'une ligne Kbart pour création de notice ExNihilo
     * @param ppnFromKbartToCreate : liste de lignes kbart
     * @param filename : nom du fichier à traiter
     */
    public void sendPpnExNihilo(List<LigneKbartDto> ppnFromKbartToCreate, ProviderPackage provider, String filename) {
        Integer nbLigneTotal = ppnFromKbartToCreate.size();
        int index = 0;
        for (LigneKbartDto ligne : ppnFromKbartToCreate) {
            ligne.setIdProviderPackage(provider.getIdProviderPackage());
            ligne.setProviderPackagePackage(provider.getPackageName());
            ligne.setProviderPackageDateP(provider.getDateP());
            ligne.setProviderPackageIdtProvider(provider.getProviderIdtProvider());
            sendNoticeExNihilo(ligne, topicKbartPpnToCreate, filename+"_"+(index++), nbLigneTotal);
        }
        if (!ppnFromKbartToCreate.isEmpty())
            log.debug("message envoyé vers {}", topicKbartPpnToCreate);
    }

    /**
     * Méthode envoyant un objet de notice imprimé sur un topic Kafka
     * @param ligneKbartDto : ligne contenant la ligne kbart, et le provider
     * @param topic : topic d'envoi de la ligne
     * @param filemame : clé kafka de la ligne correspondant au nom de fichier
     */
    private void sendNoticeExNihilo(LigneKbartDto ligneKbartDto, String topic, String filemame, Integer nbLignesTotal) {
        List<Header> headerList = new ArrayList<>();
        LigneKbartConnect ligne = utilsMapper.map(ligneKbartDto, LigneKbartConnect.class);
        try {
            headerList.add(new RecordHeader("nbLinesTotal", String.valueOf(nbLignesTotal).getBytes()));
            ProducerRecord<String, LigneKbartConnect> record = new ProducerRecord<>(topic, null, filemame, ligne, headerList);
            final SendResult<String, LigneKbartConnect> result = kafkaTemplateConnect.send(record).get();
            logEnvoi(result, record);
        } catch (Exception e) {
            String message = "Error sending message to topic " + topic;
            throw new RuntimeException(message, e);
        }
    }

    /**
     * Méthode envoyant un objet de notice imprimé sur un topic Kafka
     * @param ligne : ligne contenant la ligne kbart, le ppn de la notice imprimée et le provider
     * @param topic : topic d'envoi de la ligne
     * @param key : clé kafka de la ligne correspondant au nom du fichier + numéro séquenciel
     * @param nbLignesTotal : nombre de lignes totales du fichier
     */
    private void sendNoticeImprime(LigneKbartImprime ligne, String topic, String key, Integer nbLignesTotal) {
        List<Header> headerList = new ArrayList<>();
        headerList.add(new RecordHeader("nbLinesTotal", String.valueOf(nbLignesTotal).getBytes()));
        try {
            ProducerRecord<String, LigneKbartImprime> record = new ProducerRecord<>(topic, null, key, ligne, headerList);
            final SendResult<String, LigneKbartImprime> result = kafkaTemplateImprime.send(record).get();
            logEnvoi(result, record);
        } catch (Exception e) {
            String message = "Error sending message to topic " + topic;
            throw new RuntimeException(message, e);
        }
    }

    private void logEnvoi(SendResult<String, ?> result, ProducerRecord<String, ?> record) {
        final RecordMetadata metadata = result.getRecordMetadata();
        log.debug(String.format("Sent record(key=%s value=%s) meta(topic=%s, partition=%d, offset=%d, headers=%s)",
                record.key(), record.value(), metadata.topic(), metadata.partition(), metadata.offset(), Stream.of(result.getProducerRecord().headers().toArray()).map(h -> h.key() + ":" + Arrays.toString(h.value())).collect(Collectors.joining(";"))));
    }
}
