package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.abes.LigneKbartConnect;
import fr.abes.LigneKbartImprime;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.entity.bacon.ProviderPackage;
import fr.abes.bestppn.exception.BestPpnException;
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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicProducer {
    @Value("${spring.kafka.concurrency.nbThread}")
    private int nbThread;

    @Value("${topic.name.target.kbart}")
    private String topicKbart;

    @Value("${topic.name.target.noticeimprime}")
    private String topicNoticeImprimee;

    @Value("${topic.name.target.endoftraitment}")
    private String topicEndOfTraitment;

    @Value("${topic.name.target.ppnFromKbart}")
    private String topicKbartPpnToCreate;

    private KafkaTemplate<String, LigneKbartConnect> kafkaTemplateConnect;

    private KafkaTemplate<String, LigneKbartImprime> kafkaTemplateImprime;

    private KafkaTemplate<String, String> kafkatemplateEndoftraitement;

    private ExecutorService executorService;

    private UtilsMapper utilsMapper;

    @Autowired
    public TopicProducer(KafkaTemplate<String, LigneKbartConnect> kafkaTemplateConnect, KafkaTemplate<String, LigneKbartImprime> kafkaTemplateImprime, KafkaTemplate<String, String> kafkatemplateEndoftraitement, UtilsMapper utilsMapper) {
        this.kafkaTemplateConnect = kafkaTemplateConnect;
        this.kafkaTemplateImprime = kafkaTemplateImprime;
        this.kafkatemplateEndoftraitement = kafkatemplateEndoftraitement;
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
    @Transactional(transactionManager = "kafkaTransactionManagerKbartConnect", rollbackFor = {BestPpnException.class, JsonProcessingException.class})
    public void sendKbart(List<LigneKbartDto> kbart, ProviderPackage provider, String filename) {
        Iterator<LigneKbartDto> iterator = kbart.iterator();
        while (iterator.hasNext()) {
            LigneKbartDto ligne = iterator.next();
            ligne.setIdProviderPackage(provider.getIdProviderPackage());
            ligne.setProviderPackagePackage(provider.getPackageName());
            ligne.setProviderPackageDateP(provider.getDateP());
            ligne.setProviderPackageIdtProvider(provider.getProviderIdtProvider());
            LigneKbartConnect ligneKbartConnect = utilsMapper.map(ligne, LigneKbartConnect.class);
            List<Header> headerList = new ArrayList<>();
            executorService.execute(() -> {
                headerList.add(new RecordHeader("nbLinesTotal",  String.valueOf(kbart.size()).getBytes()));
                ProducerRecord<String, LigneKbartConnect> record = new ProducerRecord<>(topicKbart, new Random().nextInt(nbThread), filename, ligneKbartConnect, headerList);
                CompletableFuture<SendResult<String, LigneKbartConnect>>  result = kafkaTemplateConnect.executeInTransaction(kt -> kt.send(record));
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
    @Transactional(transactionManager = "kafkaTransactionManagerKbartImprime")
    public void sendPrintNotice(List<LigneKbartImprime> ligneKbartImprimes, String filename) {
        for (LigneKbartImprime ppnToCreate : ligneKbartImprimes) {
            List<Header> headerList = new ArrayList<>();
            headerList.add(constructHeader("filename", filename.getBytes(StandardCharsets.US_ASCII)));
            sendNoticeImprime(ppnToCreate, topicNoticeImprimee, headerList);
        }
        if (!ligneKbartImprimes.isEmpty())
            log.debug("message envoyé vers {}", topicNoticeImprimee);
    }


    /**
     * Méthode d'envoi d'une ligne Kbart pour création de notice ExNihilo
     * @param ppnFromKbartToCreate : liste de lignes kbart
     * @param filename : nom du fichier à traiter
     */
    @Transactional(transactionManager = "kafkaTransactionManagerKbartConnect")
    public void sendPpnExNihilo(List<LigneKbartDto> ppnFromKbartToCreate, ProviderPackage provider, String filename) {
        for (LigneKbartDto ligne : ppnFromKbartToCreate) {
            ligne.setIdProviderPackage(provider.getIdProviderPackage());
            ligne.setProviderPackagePackage(provider.getPackageName());
            ligne.setProviderPackageDateP(provider.getDateP());
            ligne.setProviderPackageIdtProvider(provider.getProviderIdtProvider());
            List<Header> headerList = new ArrayList<>();
            headerList.add(constructHeader("filename", filename.getBytes(StandardCharsets.US_ASCII)));
            sendObject(ligne, topicKbartPpnToCreate, headerList);
        }
        if (!ppnFromKbartToCreate.isEmpty())
            log.debug("message envoyé vers {}", topicKbartPpnToCreate);
    }

    /**
     * Méthode envoyant un objet de notice imprimé sur un topic Kafka
     * @param ligneKbartDto : ligne contenant la ligne kbart, et le provider
     * @param topic : topic d'envoi de la ligne
     * @param header : header kafka de la ligne
     */
    private void sendObject(LigneKbartDto ligneKbartDto, String topic, List<Header> header) {
        LigneKbartConnect ligne = utilsMapper.map(ligneKbartDto, LigneKbartConnect.class);
        try {
            ProducerRecord<String, LigneKbartConnect> record = new ProducerRecord<>(topic, null, "", ligne, header);
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
     * @param header : header kafka de la ligne
     */
    private void sendNoticeImprime(LigneKbartImprime ligne, String topic, List<Header> header) {
        try {
            ProducerRecord<String, LigneKbartImprime> record = new ProducerRecord<>(topic, null, "", ligne, header);
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

    private Header constructHeader(String key, byte[] value) {
        return new Header() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public byte[] value() {
                return value;
            }
        };
    }

    /**
     * Envoie un message de fin de traitement sur le topic kafka endOfTraitment_kbart2kafka
     */
    public void sendEndOfTraitmentReport(String filename) {
        List<Header> headerList = new ArrayList<>();
        headerList.add(new RecordHeader("FileName", filename.getBytes(StandardCharsets.UTF_8)));
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topicEndOfTraitment, null, "", "OK", headerList);
            kafkatemplateEndoftraitement.send(record);
            log.info("End of traitment report sent.");
        } catch (Exception e) {
            String message = "Error sending message to topic " + topicEndOfTraitment;
            throw new RuntimeException(message, e);
        }
    }
}
