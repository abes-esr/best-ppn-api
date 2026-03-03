package fr.abes.bestppn.dto;

import fr.abes.bestppn.model.dto.wscall.NoticeSummaryDto;
import fr.abes.bestppn.model.dto.wscall.ResultWsSudocDto;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class ResultWsSudocDtoTest {

    @Test
    void getppnWithTypeElectronique() {
        NoticeSummaryDto ppn1 = new NoticeSummaryDto("000000000", TYPE_SUPPORT.ELECTRONIQUE);
        NoticeSummaryDto ppn2 = new NoticeSummaryDto("111111111", TYPE_SUPPORT.ELECTRONIQUE);
        NoticeSummaryDto ppn3 = new NoticeSummaryDto("222222222", TYPE_SUPPORT.ELECTRONIQUE);
        NoticeSummaryDto ppn4 = new NoticeSummaryDto("333333333", TYPE_SUPPORT.IMPRIME);
        List<NoticeSummaryDto> ppnsList = new ArrayList<>();
        ppnsList.add(ppn1);
        ppnsList.add(ppn2);
        ppnsList.add(ppn3);
        ppnsList.add(ppn4);
        List<String> erreurs = new ArrayList<>();
        erreurs.add("Erreurs 1");
        erreurs.add("Erreurs 2");
        ResultWsSudocDto result = new ResultWsSudocDto();
        result.setPpns(ppnsList);
        result.setErreurs(erreurs);

        ResultWsSudocDto resultWithTypeElectronique = result.getNoticeElectronique();
        Assertions.assertEquals(3, resultWithTypeElectronique.getPpns().size());
        Assertions.assertEquals(2, resultWithTypeElectronique.getErreurs().size());

        ResultWsSudocDto resultWithTypeImprime = result.getNoticeImprime();
        Assertions.assertEquals(1, resultWithTypeImprime.getPpns().size());
        Assertions.assertEquals(2, resultWithTypeImprime.getErreurs().size());
    }
}
