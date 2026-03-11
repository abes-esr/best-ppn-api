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
import java.util.Map;

import static fr.abes.bestppn.utils.LogMarkers.FUNCTIONAL;
import static fr.abes.bestppn.utils.LogMarkers.TECHNICAL;

@Service
@Slf4j
public class CheckUrlService {
    private final NoticeService noticeService;

    public CheckUrlService(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    public boolean checkUrlInNotice(String ppn, String titleUrl) throws IOException, URISyntaxException {
        log.debug(TECHNICAL, "entrée dans checkUrlInNotice " + titleUrl);
        if (titleUrl == null || titleUrl.contains("doi.org")) {
            log.debug(TECHNICAL, "titleUrl null ou contient doi.org");
            return true;
        }
        String domain = Utils.extractDomainFromUrl(titleUrl);
        //récupération notice dans la base pour analyse
        NoticeXml notice = noticeService.getNoticeByPpn(ppn);
        if (notice == null || notice.isDeleted()) {
            log.warn(FUNCTIONAL, "Notice {} introuvable ou supprimée", ppn);
            return false;
        }

        Map<String, List<Datafield>> zones856And859 = Map.of(
                "856", notice.getZoneDollarUWithoutDollar5("856"),
                "859", notice.getZoneDollarUWithoutDollar5("859")
        );

        for (Map.Entry<String, List<Datafield>> zones : zones856And859.entrySet()) {
            boolean found = zones.getValue().stream()
                    .flatMap(zone -> zone.getSubFields().stream())
                    .filter(sousZone -> sousZone.getCode().equals("u"))
                    .anyMatch(sousZone -> sousZone.getValue().contains(domain));

            if (found) {
                log.debug(TECHNICAL, "Url trouvée dans {}", zones.getKey());
                return true;
            }
        }
        log.warn(FUNCTIONAL, "Pas de correspondance trouvée dans la notice avec l'url du provider.");
        log.warn(FUNCTIONAL, "Le PPN {} n'a pas de provider trouvé", ppn);
        return false;
    }
}
