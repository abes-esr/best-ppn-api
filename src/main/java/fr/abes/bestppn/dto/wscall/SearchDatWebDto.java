package fr.abes.bestppn.dto.wscall;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchDatWebDto {
    private Integer date;
    private String auteur;
    private String titre;

    public SearchDatWebDto(String titre) {
        this.titre = titre;
    }
}
