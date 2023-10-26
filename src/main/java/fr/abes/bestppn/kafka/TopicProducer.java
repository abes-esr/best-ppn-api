package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.abes.LigneKbartConnect;
import fr.abes.LigneKbartImprime;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.entity.bacon.ProviderPackage;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.utils.UtilsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicProducer {

    @Value("${topic.name.target.kbart}")
    private String topicKbart;

    @Value("${topic.name.target.noticeimprime}")
    private String topicNoticeImprimee;

    @Value("${topic.name.target.ppnFromKbart}")
    private String topicKbartPpnToCreate;

    private KafkaTemplate<String, LigneKbartConnect> kafkaTemplateConnect;

    private KafkaTemplate<String, LigneKbartImprime> kafkaTemplateImprime;

    private UtilsMapper utilsMapper;

    @Autowired
    public TopicProducer(KafkaTemplate<String, LigneKbartConnect> kafkaTemplateConnect, KafkaTemplate<String, LigneKbartImprime> kafkaTemplateImprime, UtilsMapper utilsMapper) {
        this.kafkaTemplateConnect = kafkaTemplateConnect;
        this.kafkaTemplateImprime = kafkaTemplateImprime;
        this.utilsMapper = utilsMapper;
    }


    /**
     * Méthode d'envoi d'une ligne kbart vers topic kafka pour chargement
     *
     * @param kbart    : ligne kbart à envoyer
     * @param provider
     * @param filename : nom du fichier du traitement en cours
     */
    @Transactional(transactionManager = "kafkaTransactionManager", rollbackFor = {BestPpnException.class, JsonProcessingException.class})
    public void sendKbart(List<LigneKbartDto> kbart, ProviderPackage provider, String filename) {
        int numLigneCourante = 0;
        for (LigneKbartDto ligne : kbart) {
            numLigneCourante++;
            ligne.setIdProviderPackage(provider.getIdProviderPackage());
            ligne.setProviderPackagePackage(provider.getPackageName());
            ligne.setProviderPackageDateP(provider.getDateP());
            ligne.setProviderPackageIdtProvider(provider.getProviderIdtProvider());
            List<Header> headerList = new ArrayList<>();
            headerList.add(constructHeader("filename", filename.getBytes()));
            if (numLigneCourante == kbart.size())
                headerList.add(constructHeader("OK", "true".getBytes()));
            sendObject(ligne, topicKbart, headerList);
        }
        if (!kbart.isEmpty())
            log.debug("message envoyé vers {}", topicKbart);
    }


    /**
     * Méthode d'envoi d'une ligne kbart pour création d'une notice à partir de la version imprimée
     *
     * @param ligneKbartImprimes : liste de kbart
     * @param filename           : nom du fichier à traiter
     */
    @Transactional(transactionManager = "kafkaTransactionManager")
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
    @Transactional(transactionManager = "kafkaTransactionManager")
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
            log.error(message);
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
            log.error(message);
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
}
