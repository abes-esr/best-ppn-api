package fr.abes.bestppn.dto;

import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public abstract class PpnDto {

    private String ppn;

    private TYPE_SUPPORT typeSupport;

    public PpnDto(String ppn, TYPE_SUPPORT typeSupport) {
        this.ppn = ppn;
        this.typeSupport = typeSupport;
    }

}
