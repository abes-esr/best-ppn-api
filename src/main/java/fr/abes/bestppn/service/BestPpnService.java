package fr.abes.bestppn.service;

import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.kafka.TopicProducer;
import fr.abes.bestppn.model.BestPpn;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.dto.wscall.PpnWithTypeDto;
import fr.abes.bestppn.model.dto.wscall.ResultWsSudocDto;
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

    public BestPpn getBestPpn(LigneKbartDto kbart, String provider, boolean isForced) throws IOException, BestPpnException, URISyntaxException, RestClientException, IllegalArgumentException {
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
        if (!doi.isBlank()) {
            feedPpnListFromDoi(doi, provider, ppnElecScoredList, ppnPrintResultList);
        }

        if (ppnElecScoredList.isEmpty() && ppnPrintResultList.isEmpty() && kbart.getPublicationType().equals(PUBLICATION_TYPE.monograph.toString())) {
            feedPpnListFromDat(kbart, ppnElecScoredList, ppnPrintResultList, provider);
        }

        return getBestPpnByScore(kbart, ppnElecScoredList, ppnPrintResultList, isForced);
    }

    private void feedPpnListFromOnline(LigneKbartDto kbart, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException, URISyntaxException, IllegalArgumentException, BestPpnException {
        log.debug("Entrée dans onlineId2Ppn");
        try {
            ResultWsSudocDto result = service.callOnlineId2Ppn(kbart.getPublicationType(), kbart.getOnlineIdentifier(), provider);
            log.info(result.toString());
            setScoreToEveryPpnFromResultWS(result, kbart.getTitleUrl(), this.scoreOnlineId2PpnElect, ppnElecScoredList, ppnPrintResultList);
        } catch (RestClientException ex) {
            throw new BestPpnException(ex.getMessage());
        }
    }

    private void feedPpnListFromPrint(LigneKbartDto kbart, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException, URISyntaxException, IllegalArgumentException, BestPpnException {
        log.debug("Entrée dans printId2Ppn");
        try {
            ResultWsSudocDto resultCallWs = service.callPrintId2Ppn(kbart.getPublicationType(), kbart.getPrintIdentifier(), provider);
            log.info(resultCallWs.toString());
            ResultWsSudocDto resultWithTypeElectronique = resultCallWs.getPpnWithTypeElectronique();
            if (resultWithTypeElectronique != null && !resultWithTypeElectronique.getPpns().isEmpty()) {
                setScoreToEveryPpnFromResultWS(resultWithTypeElectronique, kbart.getTitleUrl(), this.scorePrintId2PpnElect, ppnElecScoredList, ppnPrintResultList);
            }
            ResultWsSudocDto resultWithTypeImprime = resultCallWs.getPpnWithTypeImprime();
            if (resultWithTypeElectronique != null && !resultWithTypeImprime.getPpns().isEmpty()) {
                setScoreToEveryPpnFromResultWS(resultWithTypeImprime, kbart.getTitleUrl(), this.scoreErrorType, ppnElecScoredList, ppnPrintResultList);
            }
            ResultWsSudocDto resultWithTypeAutre = resultCallWs.getPpnWithTypeAutre().changePpnWithTypeAutreToTypeElectronique();
            if (resultWithTypeElectronique != null && !resultWithTypeAutre.getPpns().isEmpty()) {
                setScoreToEveryPpnFromResultWS(resultWithTypeAutre, kbart.getTitleUrl(), this.scoreErrorType, ppnElecScoredList, ppnPrintResultList);
            }
        } catch (RestClientException ex) {
            throw new BestPpnException(ex.getMessage());
        }
    }

    private void feedPpnListFromDat(LigneKbartDto kbart, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList, String providerName) throws IOException, URISyntaxException {
        log.debug("Entrée dans dat2ppn");
        ResultWsSudocDto resultCallWs = null;
        if (!kbart.getAnneeFromDate_monograph_published_online().isEmpty()) {
            log.debug("Appel dat2ppn :  date_monograph_published_online : {} / publication_title : {} auteur : {}", kbart.getDateMonographPublishedOnline(), kbart.getPublicationTitle(), kbart.getAuthor());
            resultCallWs = service.callDat2Ppn(kbart.getAnneeFromDate_monograph_published_online(), kbart.getAuthor(), kbart.getPublicationTitle(), providerName);
            log.info(resultCallWs.toString());
        } else if (ppnElecScoredList.isEmpty() && !kbart.getAnneeFromDate_monograph_published_print().isEmpty()) {
            log.debug("Appel dat2ppn :  date_monograph_published_print : {} / publication_title : {} auteur : {}", kbart.getDateMonographPublishedPrint(), kbart.getPublicationTitle(), kbart.getAuthor());
            resultCallWs = service.callDat2Ppn(kbart.getAnneeFromDate_monograph_published_print(), kbart.getAuthor(), kbart.getPublicationTitle(), providerName);
            log.info(resultCallWs.toString());
        }
        if (resultCallWs != null && !resultCallWs.getPpns().isEmpty()) {
            for (PpnWithTypeDto ppn : resultCallWs.getPpns()) {
                if (ppn.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)) {
                    if (ppn.isProviderPresent() || checkUrlService.checkUrlInNotice(ppn.getPpn(), kbart.getTitleUrl())) {
                        log.info("ppn : {} / score : {}", ppn, scoreDat2Ppn);
                        ppnElecScoredList.put(ppn.getPpn(), scoreDat2Ppn);
                    } else {
                        log.warn("Le PPN {} n'a pas de provider trouvé", ppn);
                    }
                } else if (ppn.getTypeSupport().equals(TYPE_SUPPORT.IMPRIME)) {
                    ppnPrintResultList.add(ppn.getPpn());
                }
            }
        }
    }

    private void feedPpnListFromDoi(String doi, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws BestPpnException {
        log.debug("Entrée dans doi2ppn");
        ResultWsSudocDto resultWS;
        try {
            resultWS = service.callDoi2Ppn(doi, provider);
            log.info(resultWS.toString());
            int nbPpnElec = (int) resultWS.getPpns().stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)).count();
            for (PpnWithTypeDto ppn : resultWS.getPpns()) {
                if (ppn.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)) {
                    setScoreToPpnElect(scoreDoi2Ppn, ppnElecScoredList, nbPpnElec, ppn);
                } else {
                    log.info("PPN Imprimé : {}", ppn);
                    ppnPrintResultList.add(ppn.getPpn());
                }
            }
        } catch (RestClientException e) {
            throw new BestPpnException(e.getMessage());
        }
    }

    private void setScoreToEveryPpnFromResultWS(ResultWsSudocDto resultCallWs, String titleUrl, int score, Map<String, Integer> ppnElecResultList, Set<String> ppnPrintResultList) throws URISyntaxException, IOException {
        if (!resultCallWs.getPpns().isEmpty()) {
            int nbPpnElec = (int) resultCallWs.getPpns().stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)).count();
            for (PpnWithTypeDto ppn : resultCallWs.getPpns()) {
                if (ppn.getTypeSupport().equals(TYPE_SUPPORT.IMPRIME)) {
                    log.info("PPN Imprimé : {}", ppn);
                    ppnPrintResultList.add(ppn.getPpn());
                } else if (ppn.getTypeDocument() != TYPE_DOCUMENT.MONOGRAPHIE || ppn.isProviderPresent() || checkUrlService.checkUrlInNotice(ppn.getPpn(), titleUrl)) {
                    setScoreToPpnElect(score, ppnElecResultList, nbPpnElec, ppn);
                } else {
                    log.warn("Le PPN {} n'a pas de provider trouvé", ppn);
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
        log.info( "PPN Electronique : " + ppn + " / score : " + ppnElecScoredList.get(ppn.getPpn()));
    }

    public BestPpn getBestPpnByScore(LigneKbartDto kbart, Map<String, Integer> ppnElecResultList, Set<String> ppnPrintResultList, boolean isForced) throws BestPpnException {
        Map<String, Integer> ppnElecScore = Utils.getMaxValuesFromMap(ppnElecResultList);
        return switch (ppnElecScore.size()) {
            case 0 -> {
                log.info("Aucun ppn électronique trouvé. " + kbart);
                yield switch (ppnPrintResultList.size()) {
                    case 0 -> {
                        kbart.setErrorType("Aucun ppn trouvé");
                        yield new BestPpn(null, DESTINATION_TOPIC.NO_PPN_FOUND_SUDOC);
                    }

                    case 1 -> {
                        String printPpn = ppnPrintResultList.stream().toList().get(0);
                        kbart.setErrorType("Ppn imprimé trouvé : " + printPpn);
                        log.debug(kbart.getErrorType());
                        yield new BestPpn(printPpn, DESTINATION_TOPIC.PRINT_PPN_SUDOC, TYPE_SUPPORT.IMPRIME);
                    }

                    default -> {
                        String errorString = "Plusieurs ppn imprimés (" + String.join("OU ", ppnPrintResultList) + ") ont été trouvés.";
                        kbart.setErrorType(errorString);
                        // vérification du forçage
                        if (isForced) {
                            log.error(errorString + " [ " + kbart + " ]");
                            yield new BestPpn("", DESTINATION_TOPIC.BEST_PPN_BACON);
                        } else {
                            throw new BestPpnException(errorString + " [ " + kbart + " ] ");
                        }
                    }
                };
            }
            case 1 ->
                    new BestPpn(ppnElecScore.keySet().stream().findFirst().get(), DESTINATION_TOPIC.BEST_PPN_BACON, TYPE_SUPPORT.ELECTRONIQUE);

            default -> {
                String listPpn = String.join("OU ", ppnElecScore.keySet());
                String errorString = "Plusieurs ppn électroniques (" + listPpn + ") ont le même score.";
                kbart.setErrorType(errorString);
                // vérification du forçage
                if (isForced) {
                    log.error(errorString + " [ " + kbart + " ]");
                    yield new BestPpn("", DESTINATION_TOPIC.BEST_PPN_BACON);
                } else {
                    throw new BestPpnException(errorString + " [ " + kbart + " ]");
                }
            }
        };
    }

}
