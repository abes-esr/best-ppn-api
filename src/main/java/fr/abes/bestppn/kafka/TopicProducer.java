package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.abes.LigneKbartConnect;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.kafka.PpnKbartProviderDto;
import fr.abes.bestppn.entity.bacon.ProviderPackage;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.utils.UtilsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
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
import java.util.List;
import java.util.concurrent.ExecutionException;
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

    @Value("${topic.name.target.endoftraitment}")
    private String topicEndOfTraitment;

    @Value("${topic.name.target.ppnFromKbart}")
    private String topicKbartPpnToCreate;

    @Autowired
    private KafkaTemplate<String, LigneKbartConnect> kafkaTemplate;

    @Autowired
    private UtilsMapper utilsMapper;

    @Autowired
    private KafkaProducer<String, String> kafkaProducer;

    @Transactional(transactionManager = "kafkaTransactionManager", rollbackFor = {BestPpnException.class, JsonProcessingException.class})
    public void sendKbart(List<LigneKbartDto> kbart, ProviderPackage provider, String filename) throws JsonProcessingException, BestPpnException, ExecutionException, InterruptedException {
        int numLigneCourante = 0;
        for (LigneKbartDto ligne : kbart) {
            numLigneCourante++;
            ligne.setProviderPackagePackage(provider.getProviderPackageId().getPackageName());
            ligne.setProviderPackageDateP(provider.getProviderPackageId().getDateP());
            ligne.setProviderPackageIdtProvider(provider.getProviderPackageId().getProviderIdtProvider());
            List<Header> headerList = new ArrayList<>();
            headerList.add(constructHeader("filename", filename.getBytes()));
            if (numLigneCourante == kbart.size())
                headerList.add(constructHeader("OK", "true".getBytes()));
            sendObject(ligne, topicKbart, headerList);
        }
        log.debug("message envoyé vers {}", topicKbart);
    }

    @Transactional(transactionManager = "kafkaTransactionManager")
    public void sendPrintNotice(List<PpnKbartProviderDto> ppnKbartProviderDtoList, ProviderPackage provider, String filename) throws JsonProcessingException, ExecutionException, InterruptedException {
        for (PpnKbartProviderDto ppnToCreate : ppnKbartProviderDtoList) {
            ppnToCreate.getKbart().setProviderPackagePackage(provider.getProviderPackageId().getPackageName());
            ppnToCreate.getKbart().setProviderPackageDateP(provider.getProviderPackageId().getDateP());
            ppnToCreate.getKbart().setProviderPackageIdtProvider(provider.getProviderPackageId().getProviderIdtProvider());
            List<Header> headerList = new ArrayList<>();
            headerList.add(constructHeader("ppn", ppnToCreate.getPpn().getBytes(StandardCharsets.US_ASCII)));
            headerList.add(constructHeader("filename", filename.getBytes(StandardCharsets.US_ASCII)));
            sendObject(ppnToCreate.getKbart(), topicNoticeImprimee, headerList);
        }
        log.debug("message envoyé vers {}", topicNoticeImprimee);
    }

    @Transactional(transactionManager = "kafkaTransactionManager")
    public void sendPpnExNihilo(List<LigneKbartDto> ppnFromKbartToCreate, ProviderPackage provider, String filename) throws JsonProcessingException, ExecutionException, InterruptedException {
        for (LigneKbartDto ligne : ppnFromKbartToCreate) {
            ligne.setProviderPackagePackage(provider.getProviderPackageId().getPackageName());
            ligne.setProviderPackageDateP(provider.getProviderPackageId().getDateP());
            ligne.setProviderPackageIdtProvider(provider.getProviderPackageId().getProviderIdtProvider());
            List<Header> headerList = new ArrayList<>();
            headerList.add(constructHeader("filename", filename.getBytes(StandardCharsets.US_ASCII)));
            sendObject(ligne, topicKbartPpnToCreate, headerList);
        }
        log.debug("message envoyé vers {}", topicKbartPpnToCreate);
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

    private SendResult sendObject(LigneKbartDto ligneKbartDto, String topic, List<Header> header) {
        LigneKbartConnect ligne = utilsMapper.map(ligneKbartDto, LigneKbartConnect.class);
        try {
            ProducerRecord<String, LigneKbartConnect> record = new ProducerRecord<>(topic, null, "", ligne, header);
            final SendResult result = kafkaTemplate.send(record).get();
            final RecordMetadata metadata = result.getRecordMetadata();
            log.debug(String.format("Sent record(key=%s value=%s) meta(topic=%s, partition=%d, offset=%d, headers=%s)",
                    record.key(), record.value(), metadata.topic(), metadata.partition(), metadata.offset(), Stream.of(result.getProducerRecord().headers().toArray()).map(h -> new String(h.key() + ":" + h.value())).collect(Collectors.joining(";"))));
            return result;
        } catch (Exception e) {
            String message = "Error sending message to topic " + topic;
            throw new RuntimeException(message, e);
        }
    }

    /**
     * Envoie un message de fin de traitement sur le topic kafka endOfTraitment_kbart2kafka
     * @param headerList list de Header (contient le nom du package et la date)
     */
    @Transactional(transactionManager = "kafkaTransactionManager")
    public void sendEndOfTraitmentReport(List<Header> headerList) throws ExecutionException, InterruptedException {
        ProducerRecord<String, String> record = new ProducerRecord<>(topicEndOfTraitment, null, "", "OK", headerList);
        kafkaProducer.send(record);
        log.info("End of traitment report sent.");
    }
}
