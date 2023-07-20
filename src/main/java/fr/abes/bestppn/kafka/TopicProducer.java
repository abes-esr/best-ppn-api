package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.kafka.PpnKbartProviderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper mapper;

    public void sendKbart(LigneKbartDto kbart) throws JsonProcessingException {
        log.debug("Message envoyé : {}", mapper.writeValueAsString(kbart));
        kafkaTemplate.send(topicKbart, mapper.writeValueAsString(kbart));
    }

    public void sendPrintNotice(String ppn, LigneKbartDto kbart, String provider) throws JsonProcessingException {
        PpnKbartProviderDto dto = new PpnKbartProviderDto(ppn, kbart, provider);
        log.debug("Message envoyé : {}", dto);
        kafkaTemplate.send(topicNoticeImprimee, Integer.valueOf(dto.hashCode()).toString(), mapper.writeValueAsString(dto));
    }
}
