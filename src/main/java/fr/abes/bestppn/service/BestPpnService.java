package fr.abes.bestppn.service;

import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.exception.IllegalDoiException;
import fr.abes.bestppn.kafka.TopicProducer;
import fr.abes.bestppn.model.BestPpn;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.dto.wscall.PpnWithTypeDto;
import fr.abes.bestppn.model.dto.wscall.ResultWsSudocDto;
import fr.abes.bestppn.utils.*;
import io.netty.handler.logging.LogLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

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

    @Setter
    private List<String> kbartLineLogs;

    @Setter
    private Boolean isSendLogs;

    public BestPpnService(WsService service, NoticeService noticeService, TopicProducer topicProducer, CheckUrlService checkUrlService) {
        this.service = service;
        this.noticeService = noticeService;
        this.topicProducer = topicProducer;
        this.checkUrlService = checkUrlService;
    }

    public BestPpn getBestPpn(LigneKbartDto kbart, String provider, boolean injectKafka, boolean isSendLogs) throws IOException, BestPpnException, URISyntaxException, RestClientException, IllegalArgumentException, IllegalDoiException {

        kbartLineLogs = new ArrayList<>();
        this.isSendLogs = isSendLogs;
        Map<String, Integer> ppnElecScoredList = new HashMap<>();
        Set<String> ppnPrintResultList = new HashSet<>();

        if (!kbart.getPublicationType().isEmpty()) {
            provider = kbart.getPublicationType().equals(PUBLICATION_TYPE.serial.toString()) ? "" : provider;
            if (kbart.getOnlineIdentifier() != null && !kbart.getOnlineIdentifier().isEmpty()) {
                sendLog(LogLevel.DEBUG, "paramètres en entrée : type : " + kbart.getPublicationType() + " / id : " + kbart.getOnlineIdentifier() + " / provider : " + provider);
                feedPpnListFromOnline(kbart, provider, ppnElecScoredList, ppnPrintResultList);
            }
            if (kbart.getPrintIdentifier() != null && !kbart.getPrintIdentifier().isEmpty()) {
                sendLog(LogLevel.DEBUG, "paramètres en entrée : type : " + kbart.getPublicationType() + " / id : " + kbart.getPrintIdentifier() + " / provider : " + provider);
                feedPpnListFromPrint(kbart, provider, ppnElecScoredList, ppnPrintResultList);
            }
        }
        String doi = Utils.extractDOI(kbart);
        if (!doi.isBlank()){
            feedPpnListFromDoi(doi, provider, ppnElecScoredList, ppnPrintResultList);
        }

        if (ppnElecScoredList.isEmpty() && ppnPrintResultList.isEmpty()) {
            feedPpnListFromDat(kbart, ppnElecScoredList, ppnPrintResultList, provider);
        }

        return getBestPpnByScore(kbart, ppnElecScoredList, ppnPrintResultList, injectKafka);
    }

    private void feedPpnListFromOnline(LigneKbartDto kbart, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException, URISyntaxException, IllegalArgumentException, BestPpnException {
        sendLog(LogLevel.INFO, "Entrée dans onlineId2Ppn");
        try {
            ResultWsSudocDto result = service.callOnlineId2Ppn(kbart.getPublicationType(), kbart.getOnlineIdentifier(), provider);
            sendLog(LogLevel.INFO, result.toString());
            setScoreToEveryPpnFromResultWS(result, kbart.getTitleUrl(), this.scoreOnlineId2PpnElect, ppnElecScoredList, ppnPrintResultList);
        } catch (RestClientException ex) {
            throw new BestPpnException(ex.getMessage());
        }
    }

    private void feedPpnListFromPrint(LigneKbartDto kbart, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException, URISyntaxException, IllegalArgumentException, BestPpnException {
        sendLog(LogLevel.INFO, "Entrée dans printId2Ppn");
        try {
            ResultWsSudocDto resultCallWs = service.callPrintId2Ppn(kbart.getPublicationType(), kbart.getPrintIdentifier(), provider);
            sendLog(LogLevel.INFO, resultCallWs.toString());
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
        sendLog(LogLevel.INFO, "Entrée dans dat2ppn");
        ResultWsSudocDto resultCallWs = null;
        if (!kbart.getAnneeFromDate_monograph_published_online().isEmpty()) {
            sendLog(LogLevel.DEBUG, "Appel dat2ppn :  date_monograph_published_online : " + kbart.getAnneeFromDate_monograph_published_online() + " / publication_title : " + kbart.getPublicationTitle() + " auteur : " + kbart.getAuthor());
            resultCallWs = service.callDat2Ppn(kbart.getAnneeFromDate_monograph_published_online(), kbart.getAuthor(), kbart.getPublicationTitle(), providerName);
        } else if (ppnElecScoredList.isEmpty() && !kbart.getAnneeFromDate_monograph_published_print().isEmpty()) {
            sendLog(LogLevel.DEBUG, "Appel dat2ppn :  date_monograph_published_print : " + kbart.getAnneeFromDate_monograph_published_print() + " / publication_title : " + kbart.getPublicationTitle() + " auteur : " + kbart.getAuthor());
            resultCallWs = service.callDat2Ppn(kbart.getAnneeFromDate_monograph_published_print(), kbart.getAuthor(), kbart.getPublicationTitle(), providerName);
        }
        if(resultCallWs != null && !resultCallWs.getPpns().isEmpty()) {
            sendLog(LogLevel.INFO, resultCallWs.toString());
            for (PpnWithTypeDto ppn : resultCallWs.getPpns()) {
                if (ppn.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)) {
                    if (ppn.isProviderPresent() || checkUrlService.checkUrlInNotice(ppn.getPpn(), kbart.getTitleUrl())) {
                        sendLog(LogLevel.INFO, "ppn : " + ppn + " / score : " + scoreDat2Ppn);
                        ppnElecScoredList.put(ppn.getPpn(), scoreDat2Ppn);
                    } else {
                        sendLog(LogLevel.WARN, "Le PPN " + ppn + " n'a pas de provider trouvé");
                    }
                } else if (ppn.getTypeSupport().equals(TYPE_SUPPORT.IMPRIME)) {
                    ppnPrintResultList.add(ppn.getPpn());
                }
            }
        }
    }

    private void feedPpnListFromDoi(String doi, String provider, Map<String, Integer> ppnElecScoredList, Set<String> ppnPrintResultList) throws IOException, IllegalDoiException {
        sendLog(LogLevel.INFO, "Entrée dans doi2ppn");
        ResultWsSudocDto resultWS = service.callDoi2Ppn(doi, provider);
        sendLog(LogLevel.INFO, resultWS.toString());
        int nbPpnElec = (int) resultWS.getPpns().stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)).count();
        for(PpnWithTypeDto ppn : resultWS.getPpns()){
            if(ppn.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)){
                setScoreToPpnElect(scoreDoi2Ppn,ppnElecScoredList,nbPpnElec,ppn);
            } else {
                sendLog(LogLevel.INFO, "PPN Imprimé : " + ppn);
                ppnPrintResultList.add(ppn.getPpn());
            }
        }
    }

    private void setScoreToEveryPpnFromResultWS(ResultWsSudocDto resultCallWs, String titleUrl, int score, Map<String, Integer> ppnElecResultList, Set<String> ppnPrintResultList) throws URISyntaxException, IOException {
        if (!resultCallWs.getPpns().isEmpty()) {
            int nbPpnElec = (int) resultCallWs.getPpns().stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)).count();
            for (PpnWithTypeDto ppn : resultCallWs.getPpns()) {
                if(ppn.getTypeSupport().equals(TYPE_SUPPORT.IMPRIME)) {
                    sendLog(LogLevel.INFO, "PPN Imprimé : " + ppn);
                    ppnPrintResultList.add(ppn.getPpn());
                } else if (ppn.getTypeDocument() != TYPE_DOCUMENT.MONOGRAPHIE || ppn.isProviderPresent() || checkUrlService.checkUrlInNotice(ppn.getPpn(), titleUrl)){
                    setScoreToPpnElect(score, ppnElecResultList, nbPpnElec, ppn);
                } else {
                    sendLog(LogLevel.WARN, "Le PPN " + ppn + " n'a pas de provider trouvé");
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
        sendLog(LogLevel.INFO, "PPN Electronique : " + ppn + " / score : " + ppnElecScoredList.get(ppn.getPpn()));
    }

    public BestPpn getBestPpnByScore(LigneKbartDto kbart, Map<String, Integer> ppnElecResultList, Set<String> ppnPrintResultList, boolean isForced) throws BestPpnException {
        Map<String, Integer> ppnElecScore = Utils.getMaxValuesFromMap(ppnElecResultList);
        return switch (ppnElecScore.size()) {
            case 0 -> {
                sendLog(LogLevel.INFO, "Aucun ppn électronique trouvé. " + kbart);
                yield switch (ppnPrintResultList.size()) {
                    case 0 -> {
                        kbart.setErrorType("Aucun ppn trouvé");
                        yield new BestPpn(null, DESTINATION_TOPIC.NO_PPN_FOUND_SUDOC, kbartLineLogs);
                    }

                    case 1 -> {
                        kbart.setErrorType("Ppn imprimé trouvé : " + ppnPrintResultList.stream().toList().get(0));
                        sendLog(LogLevel.DEBUG, "Ppn imprimé trouvé : " + ppnPrintResultList.stream().toList().get(0));
                        yield new BestPpn(ppnPrintResultList.stream().toList().get(0),DESTINATION_TOPIC.PRINT_PPN_SUDOC, TYPE_SUPPORT.IMPRIME, kbartLineLogs);
                    }

                    default -> {
                        String errorString = "Plusieurs ppn imprimés (" + String.join(", ", ppnPrintResultList) + ") ont été trouvés.";
                        kbart.setErrorType(errorString);
                        // vérification du forçage
                        if (isForced) {
                            sendLog(LogLevel.ERROR,"Erreur : plusieurs ppn imprimés (" + String.join(", ", ppnPrintResultList) + ") ont été trouvés. [ " + kbart + " ]");
                            yield new BestPpn("",DESTINATION_TOPIC.BEST_PPN_BACON, kbartLineLogs);
                        } else {
                            throw new BestPpnException(errorString);
                        }
                    }
                };
            }
            case 1 -> new BestPpn(ppnElecScore.keySet().stream().findFirst().get(), DESTINATION_TOPIC.BEST_PPN_BACON, TYPE_SUPPORT.ELECTRONIQUE, kbartLineLogs);

            default -> {
                String listPpn = String.join(", ", ppnElecScore.keySet());
                String errorString = "Erreur : plusieurs ppn électroniques (" + listPpn + ") ont le même score. [ " + kbart + " ]";
                kbart.setErrorType(errorString);
                // vérification du forçage
                if (isForced) {
                    sendLog(LogLevel.ERROR, errorString);
                    yield new BestPpn("", DESTINATION_TOPIC.BEST_PPN_BACON, kbartLineLogs);
                } else {
                    throw new BestPpnException(errorString);
                }
            }
        };
    }

    public void sendLog(LogLevel level, String message) {
        if (isSendLogs) {
            if (level.equals(LogLevel.INFO) || level.equals(LogLevel.ERROR) || level.equals(LogLevel.WARN)) kbartLineLogs.add(message);
        }
        switch (level) {
            case DEBUG -> log.debug(message);
            case TRACE -> log.trace(message);
            case WARN -> log.warn(message);
            case ERROR -> log.error(message);
            default -> log.info(message);
        }
    }
}
