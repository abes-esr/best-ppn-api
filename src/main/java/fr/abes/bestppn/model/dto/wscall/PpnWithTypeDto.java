package fr.abes.bestppn.model.dto.wscall;

import fr.abes.bestppn.utils.TYPE_DOCUMENT;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PpnWithTypeDto {
    private String ppn;
    private TYPE_SUPPORT typeSupport;

    private TYPE_DOCUMENT typeDocument;
    private Boolean providerPresent = false;


    public PpnWithTypeDto(String ppn, TYPE_SUPPORT typeSupport, TYPE_DOCUMENT typeDocument) {
        this.ppn = ppn;
        this.typeSupport = typeSupport;
        this.typeDocument = typeDocument;
    }

    public PpnWithTypeDto(String ppn, TYPE_SUPPORT typeSupport) {
        this.ppn = ppn;
        this.typeSupport = typeSupport;
    }

    public PpnWithTypeDto(String ppn) {
        this.ppn = ppn;
    }

    public boolean isProviderPresent() {
        return providerPresent;
    }

}
