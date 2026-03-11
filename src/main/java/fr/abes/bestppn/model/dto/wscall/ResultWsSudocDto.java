package fr.abes.bestppn.model.dto.wscall;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ResultWsSudocDto {

    private List<NoticeSummaryDto> ppns = new ArrayList<>();

    private List<String> erreurs = new ArrayList<>();

    private String url;


    @Override
    public String toString() {
        return "url : " + url + " / ppn(s) : " + ppns + " / erreur(s) : " + erreurs;
    }
}
