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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

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

    /**
     * Listener Kafka qui écoute un topic et récupère les messages dès qu'ils y arrivent.
     * @param lignesKbart message kafka récupéré par le Consumer Kafka
     */
    @KafkaListener(topics = {"bacon.kbart.toload"}, groupId = "lignesKbart", containerFactory = "kafkaKbartListenerContainerFactory")
    public void listenKbartFromKafka(ConsumerRecord<String, String> lignesKbart) {
        try {


            String fileName = new String(lignesKbart.headers().lastHeader("FileName").value(), StandardCharsets.UTF_8);
            String currentLine = new String(lignesKbart.headers().lastHeader("CurrentLine").value(), StandardCharsets.UTF_8);
            String totalLine = new String(lignesKbart.headers().lastHeader("TotalLine").value(), StandardCharsets.UTF_8);

            if (!lignesKbart.value().contains("OK")){
                String provider = Utils.extractProvider(fileName);
                LigneKbartDto ligneFromKafka = mapper.readValue(lignesKbart.value(), LigneKbartDto.class);
                boolean injectKafka = fileName.contains("_FORCE");
                if (!ligneFromKafka.isBestPpnEmpty()) {
                    ligneFromKafka.setBestPpn(service.getBestPpn(ligneFromKafka, provider, injectKafka));
                }
                fileName = fileName.contains("_FORCE") ? fileName.replace("_FORCE", "") : fileName;
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
