package fr.abes.bestppn.dto;

import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public abstract class PpnDto {

    private String ppn;

    private TYPE_SUPPORT typeSupport;

}
