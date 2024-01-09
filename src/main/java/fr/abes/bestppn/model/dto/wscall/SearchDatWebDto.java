package fr.abes.bestppn.model.dto.wscall;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchDatWebDto {
    private Integer date;
    private String auteur;
    private String titre;
    private Boolean isCheckProviderInNotice = true;
    private String providerName;
    public SearchDatWebDto(String titre, String providerName) {
        this.titre = titre;
        this.providerName = providerName;
    }
}
