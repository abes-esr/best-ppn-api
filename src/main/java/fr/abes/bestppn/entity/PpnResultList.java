package fr.abes.bestppn.entity;

import fr.abes.bestppn.dto.wscall.PpnWithTypeDto;
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
    private Map<PpnWithTypeDto, Integer> mapPpnScore = new HashMap<>();

    public void addPpn(String ppn, Integer value) {
        this.mapPpnScore.put(new PpnWithTypeDto(ppn), value);
    }

    public void addPpnWithType(String ppn, TYPE_SUPPORT type, Integer value) {
        this.mapPpnScore.put(new PpnWithTypeDto(ppn, type), value);
    }
    public void addPpnList(Map<PpnWithTypeDto, Integer> ppnList) {
        this.mapPpnScore.putAll(ppnList);

    }
}
