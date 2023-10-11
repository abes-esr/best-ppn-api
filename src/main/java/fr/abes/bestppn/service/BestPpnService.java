package fr.abes.bestppn.service;

import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.dto.kafka.PpnWithDestinationDto;
import fr.abes.bestppn.dto.wscall.PpnWithTypeDto;
import fr.abes.bestppn.dto.wscall.ResultDat2PpnWebDto;
import fr.abes.bestppn.dto.wscall.ResultWsSudocDto;
import fr.abes.bestppn.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalPpnException;
import fr.abes.bestppn.kafka.TopicProducer;
import fr.abes.bestppn.utils.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@Getter
@Slf4j
public class BestPpnService {
    private final WsService service;
    @Value("${score.online.id.to.ppn.elect}")
    private int scoreOnlineId2PpnElect;

    @Value("${score.print.id.to.ppn.elect}")
    private int scorePrintId2PpnElect;

    @Value("${score.error.type.notice}")
    private int scoreErrorType;

    @Value("${score.doi.to.ppn}")
    private int scoreDoi2Ppn;

    @Value("${score.dat.to.ppn}")
    private int scoreDat2Ppn;

    private final NoticeService noticeService;

    private final TopicProducer topicProducer;

    private final CheckUrlService checkUrlService;


    public BestPpnService(WsService service, NoticeService noticeService, TopicProducer topicProducer, CheckUrlService checkUrlService) {
        this.service = service;
        this.noticeService = noticeService;
        this.topicProducer = topicProducer;
        this.checkUrlService = checkUrlService;
    }

    public PpnWithDestinationDto getBestPpn(LigneKbartDto kbart, String provider, boolean injectKafka) throws IOException, IllegalPpnException, BestPpnException, URISyntaxException, RestClientException, IllegalArgumentException {

        Map<String, Integer> ppnElecScoredList = new HashMap<>();
        Set<String> ppnPrintResultList = new HashSet<>();

        if (!kbart.getPublicationType().isEmpty()) {
            provider = kbart.getPublicationType().equals(PUBLICATION_TYPE.serial.toString()) ? "" : provider;
            if (kbart.getOnlineIdentifier() != null && !kbart.getOnlineIdentifier().isEmpty()) {
                log.debug("paramètres en entrée : type : " + kbart.getPublicationType() + " / id : " + kbart.getOnlineIdentifier() + " / provider : " + provider);
                feedPpnListFromOnline(kbart, provider, ppnElecScoredList, ppnPrintResultList);
            }
            if (kbart.getPrintIdentifier() != null && !kbart.getPrintIdentifier().isEmpty()) {
                log.debug("paramètres en entrée : type : " + kbart.getPublicationType() + " / id : " + kbart.getPrintIdentifier() + " / provider : " + provider);
                feedPpnListFromPrint(kbart, provider, ppnElecScoredList, ppnPrintResultList);
            }
        }
        String doi = Utils.extractDOI(kbart);
        if (!doi.isBlank()){
            feedPpnListFromDoi(doi, provider, ppnElecScoredList, ppnPrintResultList);
        }

        if (ppnElecScoredList.isEmpty() && ppnPrintResultList.isEmpty()) {
            feedPpnListFromDat(kbart, ppnElecScoredList, ppnPrintResultList);
        }

        return getBestPpnByScore(kbart, ppnElecScoredList, ppnPrintResultList, injectKafka);
    }

    private void feedPpnListFromOnline(LigneKbartDto kbart, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException, IllegalPpnException, URISyntaxException, RestClientException, IllegalArgumentException {
        log.debug("Entrée dans onlineId2Ppn");
        setScoreToEveryPpnFromResultWS(service.callOnlineId2Ppn(kbart.getPublicationType(), kbart.getOnlineIdentifier(), provider), kbart.getTitleUrl(), this.scoreOnlineId2PpnElect, ppnElecScoredList, ppnPrintResultList);
    }

    private void feedPpnListFromPrint(LigneKbartDto kbart, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException, IllegalPpnException, URISyntaxException, RestClientException, IllegalArgumentException {
        log.debug("Entrée dans printId2Ppn");
        ResultWsSudocDto resultCallWs = service.callPrintId2Ppn(kbart.getPublicationType(), kbart.getPrintIdentifier(), provider);
        ResultWsSudocDto resultWithTypeElectronique = resultCallWs.getPpnWithTypeElectronique();
        if (resultWithTypeElectronique != null && !resultWithTypeElectronique.getPpns().isEmpty()) {
            setScoreToEveryPpnFromResultWS(resultWithTypeElectronique, kbart.getTitleUrl(), this.scorePrintId2PpnElect, ppnElecScoredList, ppnPrintResultList);
        }
        ResultWsSudocDto resultWithTypeImprime = resultCallWs.getPpnWithTypeImprime();
        if (resultWithTypeElectronique != null && !resultWithTypeImprime.getPpns().isEmpty()) {
            setScoreToEveryPpnFromResultWS(resultWithTypeImprime, kbart.getTitleUrl(), this.scoreErrorType, ppnElecScoredList, ppnPrintResultList);
        }
    }

    private void feedPpnListFromDat(LigneKbartDto kbart, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException, IllegalPpnException {
        ResultDat2PpnWebDto resultDat2PpnWeb = null;
        if (!kbart.getAnneeFromDate_monograph_published_online().isEmpty()) {
            log.debug("Appel dat2ppn :  date_monograph_published_online : " + kbart.getAnneeFromDate_monograph_published_online() + " / publication_title : " + kbart.getPublicationTitle() + " auteur : " + kbart.getAuthor());
            resultDat2PpnWeb = service.callDat2Ppn(kbart.getAnneeFromDate_monograph_published_online(), kbart.getAuthor(), kbart.getPublicationTitle());
        } else if (ppnElecScoredList.isEmpty() && !kbart.getAnneeFromDate_monograph_published_print().isEmpty()) {
            log.debug("Appel dat2ppn :  date_monograph_published_print : " + kbart.getAnneeFromDate_monograph_published_print() + " / publication_title : " + kbart.getPublicationTitle() + " auteur : " + kbart.getAuthor());
            resultDat2PpnWeb = service.callDat2Ppn(kbart.getAnneeFromDate_monograph_published_print(), kbart.getAuthor(), kbart.getPublicationTitle());
        }
        if(resultDat2PpnWeb != null && !resultDat2PpnWeb.getPpns().isEmpty()) {
            for (String ppn : resultDat2PpnWeb.getPpns()) {
                log.debug("résultat : ppn " + ppn);
                NoticeXml notice = noticeService.getNoticeByPpn(ppn);
                if (notice.isNoticeElectronique()) {
                    ppnElecScoredList.put(ppn, scoreDat2Ppn);
                } else if (notice.isNoticeImprimee()) {
                    ppnPrintResultList.add(ppn);
                }
            }
        }
    }

    private void feedPpnListFromDoi(String doi, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException {
        ResultWsSudocDto resultWS = service.callDoi2Ppn(doi, provider);
        int nbPpnElec = (int) resultWS.getPpns().stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)).count();
        for(PpnWithTypeDto ppn : resultWS.getPpns()){
            if(ppn.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)){
                setScoreToPpnElect(scoreDoi2Ppn,ppnElecScoredList,nbPpnElec,ppn);
            } else {
                log.info("PPN Imprimé : " + ppn);
                ppnPrintResultList.add(ppn.getPpn());
            }
        }
    }

    private void setScoreToEveryPpnFromResultWS(ResultWsSudocDto resultCallWs, String titleUrl, int score, Map<String, Integer> ppnElecResultList, Set<String> ppnPrintResultList) throws URISyntaxException, IOException, IllegalPpnException {
        if (!resultCallWs.getPpns().isEmpty()) {
            int nbPpnElec = (int) resultCallWs.getPpns().stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)).count();
            for (PpnWithTypeDto ppn : resultCallWs.getPpns()) {
                if(ppn.getTypeSupport().equals(TYPE_SUPPORT.IMPRIME)) {
                    log.info("PPN Imprimé : " + ppn);
                    ppnPrintResultList.add(ppn.getPpn());
                } else if (ppn.getTypeDocument() != TYPE_DOCUMENT.MONOGRAPHIE || ppn.isProviderPresent() || checkUrlService.checkUrlInNotice(ppn.getPpn(), titleUrl)){
                    setScoreToPpnElect(score, ppnElecResultList, nbPpnElec, ppn);
                } else {
                    log.error("Le PPN " + ppn + " n'a pas de provider trouvé");
                }
            }
        }
    }

    private void setScoreToPpnElect(int score, Map<String, Integer> ppnElecScoredList, int nbPpnElec, PpnWithTypeDto ppn) {
        if (!ppnElecScoredList.isEmpty() && ppnElecScoredList.containsKey(ppn.getPpn())) {
            Integer value = ppnElecScoredList.get(ppn.getPpn()) + (score / nbPpnElec);
            ppnElecScoredList.put(ppn.getPpn(), value);
        } else {
            ppnElecScoredList.put(ppn.getPpn(), (score / nbPpnElec));
        }
        log.info("PPN Electronique : " + ppn + " / score : " + ppnElecScoredList.get(ppn.getPpn()));
    }

    public PpnWithDestinationDto getBestPpnByScore(LigneKbartDto kbart, Map<String, Integer> ppnElecResultList, Set<String> ppnPrintResultList, boolean injectKafka) throws BestPpnException {
        Map<String, Integer> ppnElecScore = Utils.getMaxValuesFromMap(ppnElecResultList);
        return switch (ppnElecScore.size()) {
            case 0 -> {
                log.info("Aucun ppn électronique trouvé." + kbart);
                yield switch (ppnPrintResultList.size()) {
                    case 0 -> {
                        kbart.setErrorType("Aucun ppn trouvé");
                        yield new PpnWithDestinationDto(null, DESTINATION_TOPIC.NO_PPN_FOUND_SUDOC);
                    }

                    case 1 -> {
                        kbart.setErrorType("Ppn imprimé trouvé : " + ppnPrintResultList.stream().toList().get(0));
                        yield new PpnWithDestinationDto(ppnPrintResultList.stream().toList().get(0),DESTINATION_TOPIC.PRINT_PPN_SUDOC);
                    }

                    default -> {
                        String errorString = "Plusieurs ppn imprimés (" + String.join(", ", ppnPrintResultList) + ") ont été trouvés.";
                        kbart.setErrorType(errorString);
                        // vérification du forçage
                        if (injectKafka) {
                            yield new PpnWithDestinationDto("",DESTINATION_TOPIC.BEST_PPN_BACON);
                        } else {
                            throw new BestPpnException(errorString);
                        }
                    }
                };
            }
            case 1 -> new PpnWithDestinationDto(ppnElecScore.keySet().stream().findFirst().get(), DESTINATION_TOPIC.BEST_PPN_BACON);

            default -> {
                String listPpn = String.join(", ", ppnElecScore.keySet());
                String errorString = "Les ppn électroniques " + listPpn + " ont le même score";
                kbart.setErrorType(errorString);
                // vérification du forçage
                if (injectKafka) {
                    yield new PpnWithDestinationDto("", DESTINATION_TOPIC.BEST_PPN_BACON);
                } else {
                    throw new BestPpnException(errorString);
                }
            }
        };
    }
}
