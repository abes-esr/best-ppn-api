package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalPpnException;
import fr.abes.bestppn.exception.IllegalProviderException;
import fr.abes.bestppn.service.BestPpnService;
import fr.abes.bestppn.utils.StatusKafka;
import fr.abes.bestppn.utils.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URISyntaxException;

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
            log.debug("test");
            if(lignesKbart.value().equals("OK")){
//                kafkaTransactionManager.commit(new StatusKafka());
                log.info("commit");
            } else {
                String filename = "";
                for (Header header : lignesKbart.headers().toArray()) {
                    if(header.key().equals("FileName")){
                        filename = new String(header.value());
                    }
                }
                String provider = Utils.extractProvider(filename);
                LigneKbartDto ligneFromKafka = mapper.readValue(lignesKbart.value(), LigneKbartDto.class);
                if (!ligneFromKafka.isBestPpnEmpty()) {
                    ligneFromKafka.setBestPpn(service.getBestPpn(ligneFromKafka, provider, true));
                }
                producer.sendKbart(ligneFromKafka);
            }
        } catch (IllegalProviderException e) {
            log.error("Erreur dans les données en entrée, provider incorrect");
        } catch (IllegalPpnException | BestPpnException | IOException | URISyntaxException e) {
            log.error(e.getMessage());
        }
    }
}
