package fr.abes.bestppn.model.dto.wscall;

import fr.abes.bestppn.utils.TYPE_DOCUMENT;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoticeSummaryDto {
    private String ppn;
    private TYPE_SUPPORT typeSupport;

    private TYPE_DOCUMENT typeDocument;
    private Boolean providerPresent = false;
    boolean isFoundByRebound = false;



    public NoticeSummaryDto(String ppn) {
        this.ppn = ppn;
    }

    public NoticeSummaryDto(String ppn, TYPE_SUPPORT typeSupport, TYPE_DOCUMENT typeDocument, Boolean providerPresent) {
        this.ppn = ppn;
        this.typeSupport = typeSupport;
        this.typeDocument = typeDocument;
        this.providerPresent = providerPresent;
    }

    public NoticeSummaryDto(String ppn, TYPE_SUPPORT typeSupport) {
        this.ppn = ppn;
        this.typeSupport = typeSupport;
    }

    public boolean isProviderPresent() {
        return providerPresent;
    }

}
