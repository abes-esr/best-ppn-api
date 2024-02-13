package fr.abes.bestppn.service;

import fr.abes.bestppn.model.entity.basexml.notice.Datafield;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.model.entity.basexml.notice.SubField;
import fr.abes.bestppn.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

@Service
@Slf4j
public class CheckUrlService {
    private final NoticeService noticeService;

    public CheckUrlService(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    public boolean checkUrlInNotice(String ppn, String titleUrl) throws IOException, URISyntaxException {
        log.debug("entrée dans checkUrlInNotice " + titleUrl);
        if (titleUrl == null || titleUrl.contains("doi.org")) {
            log.debug("titleUrl null ou contient doi.org");
            return true;
        }
        String domain = Utils.extractDomainFromUrl(titleUrl);
        //récupération notice dans la base pour analyse
        NoticeXml notice = noticeService.getNoticeByPpn(ppn);
        List<Datafield> zones856 = notice.getZoneDollarUWithoutDollar5("856");
        for(Datafield zone : zones856) {
            for (SubField sousZone : zone.getSubFields().stream().filter(sousZone -> sousZone.getCode().equals("u")).toList()) {
                if (sousZone.getValue().contains(domain)) {
                    log.debug("Url trouvée dans 856");
                    return true;
                }
            }
        }
        List<Datafield> zone859 = notice.getZoneDollarUWithoutDollar5("859");
        for (Datafield zone : zone859) {
            for (SubField sousZone : zone.getSubFields().stream().filter(sousZone -> sousZone.getCode().equals("u")).toList()) {
                if (sousZone.getValue().contains(domain)) {
                    log.debug("Url trouvée dans 859");
                    return true;
                }
            }
        }
        log.warn("Pas de correspondance trouvée dans la notice avec l'url du provider.");
        return false;
    }
}
