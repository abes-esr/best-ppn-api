package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.kafka.PpnKbartProviderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicProducer {

    @Value("${topic.name.target.kbart}")
    private String topicKbart;

    @Value("${topic.name.target.noticeimprime}")
    private String topicNoticeImprimee;

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper mapper;

    public void sendKbart(LigneKbartDto kbart, String fileName) throws JsonProcessingException {
        log.debug("Message envoyé : {}", mapper.writeValueAsString(kbart));
        kafkaTemplate.send(topicKbart, fileName, mapper.writeValueAsString(kbart));
    }

    public void sendPrintNotice(String ppn, LigneKbartDto kbart, String provider) throws JsonProcessingException {
        PpnKbartProviderDto dto = new PpnKbartProviderDto(ppn, kbart, provider);
        log.debug("Message envoyé : {}", dto);
        kafkaTemplate.send(topicNoticeImprimee, Integer.valueOf(dto.hashCode()).toString(), mapper.writeValueAsString(dto));
    }
}
