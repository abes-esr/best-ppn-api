package fr.abes.bestppn.model.dto.wscall;

import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ResultWsSudocDto {

    private List<PpnWithTypeDto> ppns = new ArrayList<>();

    private List<String> erreurs = new ArrayList<>();

    private String url;

    public ResultWsSudocDto getPpnWithTypeElectronique() {
        ResultWsSudocDto result = new ResultWsSudocDto();
        List<PpnWithTypeDto> ppnsSorted = new ArrayList<>(this.ppns.stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE)).toList());
        result.setPpns(ppnsSorted);
        result.setErreurs(this.erreurs);
        return result;
    }

    public ResultWsSudocDto getPpnWithTypeImprime() {
        ResultWsSudocDto result = new ResultWsSudocDto();
        List<PpnWithTypeDto> ppnsSorted = new ArrayList<>(this.ppns.stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getTypeSupport().equals(TYPE_SUPPORT.IMPRIME)).toList());
        result.setPpns(ppnsSorted);
        result.setErreurs(this.erreurs);
        return result;
    }

    public ResultWsSudocDto getPpnWithTypeAutre() {
        ResultWsSudocDto result = new ResultWsSudocDto();
        List<PpnWithTypeDto> ppnsSorted = new ArrayList<>(this.ppns.stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getTypeSupport().equals(TYPE_SUPPORT.AUTRE)).toList());
        result.setPpns(ppnsSorted);
        result.setErreurs(this.erreurs);
        return result;
    }

    public ResultWsSudocDto changePpnWithTypeAutreToTypeElectronique() {
        ResultWsSudocDto result = new ResultWsSudocDto();
        List<PpnWithTypeDto> ppnsRacines = new ArrayList<>();
        this.ppns.stream().filter(ppnWithTypeDto -> ppnWithTypeDto.getTypeSupport().equals(TYPE_SUPPORT.AUTRE)).forEach(ppnWithTypeDto -> ppnsRacines.add(new PpnWithTypeDto(ppnWithTypeDto.getPpn(), TYPE_SUPPORT.ELECTRONIQUE, ppnWithTypeDto.getTypeDocument(), ppnWithTypeDto.getProviderPresent())));
        result.setPpns(ppnsRacines);
        return result;
    }

    public ResultWsSudocDto getPpnRacineWithErrorType() {
        ResultWsSudocDto result = new ResultWsSudocDto();
        List<PpnWithTypeDto> ppnsRacines = new ArrayList<>();
        this.erreurs.stream().filter(message -> message.startsWith("Le PPN ") && message.endsWith(" n'est pas une ressource imprimée"))
                .forEach(
                        message -> ppnsRacines.add(new PpnWithTypeDto(message.substring(7, 16), TYPE_SUPPORT.ELECTRONIQUE))
                );
        result.setPpns(ppnsRacines);
        result.setErreurs(this.erreurs);
        return result;
    }

    @Override
    public String toString() {
        return "url : " + url + " / ppn(s) : " + ppns + " / erreur(s) : " + erreurs;
    }
}
