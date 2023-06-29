package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.exception.*;
import fr.abes.bestppn.service.BestPpnService;
import fr.abes.bestppn.utils.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicConsumer {
    @Value("${topic.name.source.kbart}")
    private  String topicLigneKbart;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private BestPpnService service;

    @Autowired
    private TopicProducer producer;

    @KafkaListener(topics = {"bacon.kbart.toload"}, groupId = "lignesKbart", containerFactory = "kafkaKbartListenerContainerFactory")
    public void listenKbartFromKafka(ConsumerRecord<String, String> lignesKbart) {
        try {
            String filename = lignesKbart.key();
            String provider = Utils.extractProvider(filename);
            LigneKbartDto ligneFromKafka = mapper.readValue(lignesKbart.value(), LigneKbartDto.class);
            ligneFromKafka.setBestPpn(service.getBestPpn(ligneFromKafka, provider));
            producer.sendKbart(ligneFromKafka, filename);
        } catch (IllegalProviderException e) {
            log.error("Erreur dans les données en entrée, provider incorrect");
        } catch (IllegalPpnException | BestPpnException | IOException | URISyntaxException e) {
            log.error(e.getMessage());
        }
    }
}
