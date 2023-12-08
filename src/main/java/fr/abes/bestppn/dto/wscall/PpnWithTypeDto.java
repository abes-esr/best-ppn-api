package fr.abes.bestppn.dto.wscall;

import fr.abes.bestppn.dto.PpnDto;
import fr.abes.bestppn.utils.TYPE_DOCUMENT;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class PpnWithTypeDto extends PpnDto {

    private TYPE_DOCUMENT typeDocument;
    private Boolean providerPresent = false;


    public PpnWithTypeDto(String ppn, TYPE_SUPPORT typeSupport, TYPE_DOCUMENT typeDocument) {
        super(ppn, typeSupport);
        this.typeDocument = typeDocument;
    }

    public PpnWithTypeDto(String ppn, TYPE_SUPPORT typeSupport) {
        super(ppn, typeSupport);
    }

    public PpnWithTypeDto(String ppn) {
        super.setPpn(ppn);
    }

    public boolean isProviderPresent() {
        return providerPresent;
    }

}
