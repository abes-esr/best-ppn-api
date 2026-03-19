package fr.abes.bestppn.service;

import fr.abes.bestppn.model.dto.wscall.NoticeSummaryDto;
import fr.abes.bestppn.model.dto.wscall.ResultWsSudocDto;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;



public class DtoHandlerService {

    public static List<NoticeSummaryDto> getNoticeElectroniqueByRebound(ResultWsSudocDto resultWsSudocDto) {
        return new ArrayList<>(resultWsSudocDto.getPpns().stream().filter(noticeSummaryDto -> noticeSummaryDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE) && noticeSummaryDto.isFoundByRebound()).toList());
    }

    public static List<NoticeSummaryDto> getNoticeElectronique(ResultWsSudocDto resultWsSudocDto) {
        return new ArrayList<>(resultWsSudocDto.getPpns().stream().filter(noticeSummaryDto -> noticeSummaryDto.getTypeSupport().equals(TYPE_SUPPORT.ELECTRONIQUE) && !noticeSummaryDto.isFoundByRebound()).toList());
    }

    public static List<NoticeSummaryDto> getNoticeImprime(ResultWsSudocDto resultWsSudocDto) {
        return new ArrayList<>(
                resultWsSudocDto.getPpns().stream().filter(
                        noticeSummaryDto -> noticeSummaryDto.getTypeSupport().equals(TYPE_SUPPORT.IMPRIME)
                ).toList());
    }

    public static ResultWsSudocDto getNoticeAutre(ResultWsSudocDto resultWsSudocDto) {
        ResultWsSudocDto result = new ResultWsSudocDto();
        List<NoticeSummaryDto> ppnsSorted = new ArrayList<NoticeSummaryDto>(resultWsSudocDto.getPpns().stream().filter(noticeSummaryDto -> noticeSummaryDto.getTypeSupport().equals(TYPE_SUPPORT.AUTRE)).toList());
        result.setPpns(ppnsSorted);
        result.setErreurs(resultWsSudocDto.getErreurs());
        return result;
    }

    public static ResultWsSudocDto changeNoticeAutreToElectronique(ResultWsSudocDto resultWsSudocDto) {
        ResultWsSudocDto result = new ResultWsSudocDto();
        List<NoticeSummaryDto> ppnsRacines = new ArrayList<NoticeSummaryDto>();
        resultWsSudocDto.getPpns().stream().filter(noticeSummaryDto -> noticeSummaryDto.getTypeSupport().equals(TYPE_SUPPORT.AUTRE)).forEach(noticeSummaryDto -> ppnsRacines.add(new NoticeSummaryDto(noticeSummaryDto.getPpn(), TYPE_SUPPORT.ELECTRONIQUE, noticeSummaryDto.getTypeDocument(), noticeSummaryDto.getProviderPresent())));
        result.setPpns(ppnsRacines);
        return result;
    }

    public static ResultWsSudocDto getPpnRacineWithErrorType(ResultWsSudocDto resultWsSudocDto) {
        ResultWsSudocDto result = new ResultWsSudocDto();
        List<NoticeSummaryDto> ppnsRacines = new ArrayList<NoticeSummaryDto>();
        resultWsSudocDto.getErreurs().stream().filter(message -> message.startsWith("Le PPN ") && message.endsWith(" n'est pas une ressource imprimée"))
                .forEach(
                        message -> ppnsRacines.add(new NoticeSummaryDto(message.substring(7, 16), TYPE_SUPPORT.ELECTRONIQUE))
                );
        result.setPpns(ppnsRacines);
        result.setErreurs(resultWsSudocDto.getErreurs());
        return result;
    }
}