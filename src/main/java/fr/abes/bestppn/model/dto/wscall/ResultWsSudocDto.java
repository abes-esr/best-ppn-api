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

    private List<NoticeSummaryDto> ppns = new ArrayList<>();

    private List<String> erreurs = new ArrayList<>();

    private String url;

    public ResultWsSudocDto getNoticeElectronique() {
        ResultWsSudocDto result = new ResultWsSudocDto();
        List<NoticeSummaryDto> ppnsSorted = new ArrayList<>(this.ppns.stream().filter(noticeSummaryDto -> noticeSummaryDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE) && noticeSummaryDto.isFoundByRebond()).toList());
        result.setPpns(ppnsSorted);
        result.setErreurs(this.erreurs);
        return result;
    }

    public ResultWsSudocDto getNoticeElectroniqueNotByRebound() {
        ResultWsSudocDto result = new ResultWsSudocDto();
        List<NoticeSummaryDto> ppnsSorted = new ArrayList<>(this.ppns.stream().filter(noticeSummaryDto -> noticeSummaryDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE) && !noticeSummaryDto.isFoundByRebond()).toList());
        result.setPpns(ppnsSorted);
        result.setErreurs(this.erreurs);
        return result;
    }

    public ResultWsSudocDto getNoticeImprime() {
        ResultWsSudocDto result = new ResultWsSudocDto();
        List<NoticeSummaryDto> ppnsSorted = new ArrayList<>(this.ppns.stream().filter(noticeSummaryDto -> noticeSummaryDto.getTypeSupport().equals(TYPE_SUPPORT.IMPRIME)).toList());
        result.setPpns(ppnsSorted);
        result.setErreurs(this.erreurs);
        return result;
    }

    public ResultWsSudocDto getNoticeAutre() {
        ResultWsSudocDto result = new ResultWsSudocDto();
        List<NoticeSummaryDto> ppnsSorted = new ArrayList<>(this.ppns.stream().filter(noticeSummaryDto -> noticeSummaryDto.getTypeSupport().equals(TYPE_SUPPORT.AUTRE)).toList());
        result.setPpns(ppnsSorted);
        result.setErreurs(this.erreurs);
        return result;
    }

    public ResultWsSudocDto changeNoticeAutreToElectronique() {
        ResultWsSudocDto result = new ResultWsSudocDto();
        List<NoticeSummaryDto> ppnsRacines = new ArrayList<>();
        this.ppns.stream().filter(noticeSummaryDto -> noticeSummaryDto.getTypeSupport().equals(TYPE_SUPPORT.AUTRE)).forEach(noticeSummaryDto -> ppnsRacines.add(new NoticeSummaryDto(noticeSummaryDto.getPpn(), TYPE_SUPPORT.ELECTRONIQUE, noticeSummaryDto.getTypeDocument(), noticeSummaryDto.getProviderPresent())));
        result.setPpns(ppnsRacines);
        return result;
    }

    public ResultWsSudocDto getPpnRacineWithErrorType() {
        ResultWsSudocDto result = new ResultWsSudocDto();
        List<NoticeSummaryDto> ppnsRacines = new ArrayList<>();
        this.erreurs.stream().filter(message -> message.startsWith("Le PPN ") && message.endsWith(" n'est pas une ressource imprimée"))
                .forEach(
                        message -> ppnsRacines.add(new NoticeSummaryDto(message.substring(7, 16), TYPE_SUPPORT.ELECTRONIQUE))
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
