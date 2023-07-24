package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.kafka.PpnKbartProviderDto;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalPpnException;
import fr.abes.bestppn.exception.IllegalProviderException;
import fr.abes.bestppn.service.BestPpnService;
import fr.abes.bestppn.utils.StatusKafka;
import fr.abes.bestppn.utils.Utils;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

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

    @Resource(name="kafkaTransactionManager")
    private KafkaTransactionManager kafkaTransactionManager;

    private final List<LigneKbartDto> kbartToSend = new ArrayList<>();

    boolean isOnError = false;

    @KafkaListener(topics = {"TEST.TRANSACTION.bacon.kbart.toload"}, groupId = "lignesKbart", containerFactory = "kafkaKbartListenerContainerFactory")
    public void listenKbartFromKafka(ConsumerRecord<String, String> lignesKbart) {

        try {
            if(lignesKbart.value().equals("OK") ){
                if( !isOnError ) {
                    producer.sendKbart(kbartToSend);

                } else {
                    isOnError = false;
                }
                kbartToSend.clear();

            } else {
                String filename = "";
                for (Header header : lignesKbart.headers().toArray()) {
                    if(header.key().equals("FileName")){
                        filename = new String(header.value());
                        break;
                    }
                }
                String provider = Utils.extractProvider(filename);
                LigneKbartDto ligneFromKafka = mapper.readValue(lignesKbart.value(), LigneKbartDto.class);
                if (ligneFromKafka.isBestPpnEmpty()) {
                    ligneFromKafka.setBestPpn(service.getBestPpn(ligneFromKafka, provider, true));
                }
                kbartToSend.add(ligneFromKafka);
            }
        } catch (IllegalProviderException e) {
            isOnError = true;
            log.error("Erreur dans les données en entrée, provider incorrect");
        } catch (IllegalPpnException | BestPpnException | IOException | URISyntaxException e) {
            isOnError = true;
            log.error(e.getMessage());
        }
    }
}
