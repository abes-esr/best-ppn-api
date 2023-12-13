package fr.abes.bestppn.model.dto.wscall;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ResultDat2PpnWebDto {
    @JsonProperty("ppns")
    List<String> ppns = new ArrayList<>();
    @JsonProperty("erreurs")
    List<String> erreurs = new ArrayList<>();

    public void addPpn(String ppn) {
        this.ppns.add(ppn);
    }

    public void addPpns(List<String> ppns) {
        this.ppns.addAll(ppns);
    }

    public void addErreur(String erreur) {
        this.erreurs.add(erreur);
    }

}
