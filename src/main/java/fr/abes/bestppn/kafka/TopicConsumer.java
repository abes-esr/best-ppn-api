package fr.abes.bestppn.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.kafka.PpnKbartProviderDto;
import fr.abes.bestppn.dto.kafka.PpnWithDestinationDto;
import fr.abes.bestppn.dto.PackageKbartDto;
import fr.abes.bestppn.entity.bacon.Provider;
import fr.abes.bestppn.entity.bacon.ProviderPackage;
import fr.abes.bestppn.entity.bacon.ProviderPackageId;
import fr.abes.bestppn.exception.*;
import fr.abes.bestppn.repository.bacon.ProviderPackageRepository;
import fr.abes.bestppn.repository.bacon.ProviderRepository;
import fr.abes.bestppn.service.BestPpnService;
import fr.abes.bestppn.service.EmailService;
import fr.abes.bestppn.utils.Utils;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    @Autowired
    private EmailService serviceMail;
    private final List<LigneKbartDto> kbartToSend = new ArrayList<>();

    private final List<PpnKbartProviderDto> ppnToCreate = new ArrayList<>();

    private final PackageKbartDto mailAttachment = new PackageKbartDto();

    private final ProviderPackageRepository providerPackageRepository;

    private final ProviderRepository providerRepository;

    boolean isOnError = false;

    @KafkaListener(topics = {"${topic.name.source.kbart}"}, groupId = "lignesKbart", containerFactory = "kafkaKbartListenerContainerFactory")
    public void listenKbartFromKafka(ConsumerRecord<String, String> lignesKbart) {
        String filename = "";
        try {
            for (Header header : lignesKbart.headers().toArray()) {
                if(header.key().equals("FileName")){
                    filename = new String(header.value());
                    break;
                }
            }
            String providerName = Utils.extractProvider(filename);
            Optional<Provider> providerOpt = providerRepository.findByProvider(providerName);

            if(lignesKbart.value().equals("OK") ){
                if( !isOnError ) {
                    if (providerOpt.isPresent()) {
                        Provider provider = providerOpt.get();
                        ProviderPackageId providerPackageId = new ProviderPackageId(Utils.extractPackageName(filename), Utils.extractDate(filename), provider.getIdtProvider());
                        Optional<ProviderPackage> providerPackage = providerPackageRepository.findByProviderPackageId(providerPackageId);
                        //pas d'info de package, on le crée
                        providerPackage.orElseGet(() -> providerPackageRepository.save(new ProviderPackage(providerPackageId, 'N')));
                    } else {
                        //pas de provider, ni de package, on les crée tous les deux
                        Provider newProvider = new Provider(providerName);
                        Provider savedProvider = providerRepository.save(newProvider);
                        ProviderPackage providerPackage = new ProviderPackage(new ProviderPackageId(Utils.extractPackageName(filename), Utils.extractDate(filename), savedProvider.getIdtProvider()), 'N');
                        providerPackageRepository.save(providerPackage);
                    }
                    producer.sendKbart(kbartToSend, lignesKbart.headers());
                    producer.sendPrintNotice(ppnToCreate, lignesKbart.headers());
                } else {
                    isOnError = false;
                }
                serviceMail.sendMailWithAttachment(filename,mailAttachment);
                kbartToSend.clear();
                ppnToCreate.clear();
                mailAttachment.clearKbartDto();
            } else {
                LigneKbartDto ligneFromKafka = mapper.readValue(lignesKbart.value(), LigneKbartDto.class);
                if (ligneFromKafka.isBestPpnEmpty()) {
                    PpnWithDestinationDto ppnWithDestinationDto = service.getBestPpn(ligneFromKafka, providerName);
                    switch (ppnWithDestinationDto.getDestination()){
                        case BEST_PPN_BACON -> {
                            ligneFromKafka.setBestPpn(ppnWithDestinationDto.getPpn());
                            kbartToSend.add(ligneFromKafka);
                        }
                        case PRINT_PPN_SUDOC -> ppnToCreate.add(new PpnKbartProviderDto(ppnWithDestinationDto.getPpn(),ligneFromKafka,providerName));
                    }
                } else {
                    kbartToSend.add(ligneFromKafka);
                }
                mailAttachment.addKbartDto(ligneFromKafka);
            }
        } catch (IllegalProviderException e) {
            isOnError = true;
            log.error("Erreur dans les données en entrée, provider incorrect");
        } catch (IllegalPpnException | BestPpnException | IOException | URISyntaxException | RestClientException | IllegalArgumentException e) {
            isOnError = true;
            log.error(e.getMessage());
        } catch (MessagingException | IllegalPackageException | IllegalDateException e) {
            throw new RuntimeException(e);
        }
    }
}
