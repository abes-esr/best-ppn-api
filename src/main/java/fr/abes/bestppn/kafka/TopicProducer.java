package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.kafka.PpnKbartProviderDto;
import fr.abes.bestppn.exception.BestPpnException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper mapper;

    @Transactional(transactionManager = "kafkaTransactionManager", rollbackFor = {BestPpnException.class, JsonProcessingException.class})
    public void sendKbart(List<LigneKbartDto> kbart, Headers headers) throws JsonProcessingException, BestPpnException {
        for (LigneKbartDto ligne : kbart) {
            if( ligne.isBestPpnEmpty()){
                throw new BestPpnException("La ligne " + ligne +" n'a pas de BestPpn.");
            }
            setHeadersAndSend(headers, mapper.writeValueAsString(ligne), topicKbart);
        }
    }

    @Transactional(transactionManager = "kafkaTransactionManager")
    public void sendPrintNotice(List<PpnKbartProviderDto> ppnKbartProviderDtoList, Headers headers) throws JsonProcessingException {
        for (PpnKbartProviderDto ppnToCreate : ppnKbartProviderDtoList) {
            setHeadersAndSend(headers, mapper.writeValueAsString(ppnToCreate), topicNoticeImprimee);
        }
    }

    /**
     * Envoie un message de fin de traitement sur le topic kafka endOfTraitment_kbart2kafka
     * @param headers le header du message (contient le nom du package et la date)
     */
    @Transactional(transactionManager = "kafkaTransactionManager")
    public void sendEndOfTraitmentReport(Headers headers) {
        setHeadersAndSend(headers, "OK", topicEndOfTraitment);
    }

    private void setHeadersAndSend(Headers headers, String value, String topic) {
        MessageBuilder<String> messageBuilder = MessageBuilder
                .withPayload(value)
                .setHeader(KafkaHeaders.TOPIC, topic);
        for (Header header : headers.toArray()) {
            messageBuilder.setHeader(header.key(), header.value());
        }
        Message<String> message = messageBuilder.build();
        kafkaTemplate.send(message);
    }
}
