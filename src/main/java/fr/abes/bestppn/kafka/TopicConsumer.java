package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalPpnException;
import fr.abes.bestppn.exception.IllegalProviderException;
import fr.abes.bestppn.service.BestPpnService;
import fr.abes.bestppn.utils.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicConsumer {
    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private BestPpnService service;

    @Autowired
    private TopicProducer producer;

    @KafkaListener(topics = {"bacon.kbart.toload"}, groupId = "lignesKbart", containerFactory = "kafkaKbartListenerContainerFactory")
    public void listenKbartFromKafka(ConsumerRecord<String, String> lignesKbart) {
        try {

            String fileName = new String(lignesKbart.headers().lastHeader("FileName").value(), StandardCharsets.UTF_8);
            String currentLine = new String(lignesKbart.headers().lastHeader("CurrentLine").value(), StandardCharsets.UTF_8);

            if (!lignesKbart.value().contains("OK")){
                String provider = Utils.extractProvider(fileName);
                LigneKbartDto ligneFromKafka = mapper.readValue(lignesKbart.value(), LigneKbartDto.class);
                if (!ligneFromKafka.isBestPpnEmpty()) {
                    boolean forceSetBestPpn = fileName.contains("_FORCE");
                    ligneFromKafka.setBestPpn(service.getBestPpn(ligneFromKafka, provider, forceSetBestPpn));
                }
                producer.sendKbart(ligneFromKafka, fileName);
                log.debug("La ligne " + currentLine + " du kbart " + fileName + " a été correctemnt traitée.");
            } else {
                log.debug("Le kbart " + fileName + " a été correctement traité");
            }
        } catch (IllegalProviderException e) {
            log.error("Erreur dans les données en entrée, provider incorrect");
        } catch (IllegalPpnException | BestPpnException | IOException | URISyntaxException e) {
            log.error(e.getMessage());
        }
    }
}
