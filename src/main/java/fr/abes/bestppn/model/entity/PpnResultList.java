package fr.abes.bestppn.model.entity;

import fr.abes.bestppn.model.dto.wscall.NoticeSummaryDto;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class PpnResultList {
    private Map<NoticeSummaryDto, Integer> mapPpnScore = new HashMap<>();

    public void addPpn(String ppn, Integer value) {
        this.mapPpnScore.put(new NoticeSummaryDto(ppn), value);
    }

    public void addPpnWithType(String ppn, TYPE_SUPPORT type, Integer value) {
        this.mapPpnScore.put(new NoticeSummaryDto(ppn, type), value);
    }
    public void addPpnList(Map<NoticeSummaryDto, Integer> ppnList) {
        this.mapPpnScore.putAll(ppnList);

    }
}
