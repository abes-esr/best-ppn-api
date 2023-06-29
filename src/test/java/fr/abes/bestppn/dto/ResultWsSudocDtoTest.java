package fr.abes.bestppn.dto;

import fr.abes.bestppn.dto.wscall.PpnWithTypeDto;
import fr.abes.bestppn.dto.wscall.ResultWsSudocDto;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class ResultWsSudocDtoTest {

    @Test
    void getppnWithTypeElectronique() {
        PpnWithTypeDto ppn1 = new PpnWithTypeDto("000000000", TYPE_SUPPORT.ELECTRONIQUE);
        PpnWithTypeDto ppn2 = new PpnWithTypeDto("111111111", TYPE_SUPPORT.ELECTRONIQUE);
        PpnWithTypeDto ppn3 = new PpnWithTypeDto("222222222", TYPE_SUPPORT.ELECTRONIQUE);
        PpnWithTypeDto ppn4 = new PpnWithTypeDto("333333333", TYPE_SUPPORT.IMPRIME);
        List<PpnWithTypeDto> ppnsList = new ArrayList<>();
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

        ResultWsSudocDto resultWithTypeElectronique = result.getPpnWithTypeElectronique();
        Assertions.assertEquals(3, resultWithTypeElectronique.getPpns().size());
        Assertions.assertEquals(2, resultWithTypeElectronique.getErreurs().size());

        ResultWsSudocDto resultWithTypeImprime = result.getPpnWithTypeImprime();
        Assertions.assertEquals(1, resultWithTypeImprime.getPpns().size());
        Assertions.assertEquals(2, resultWithTypeImprime.getErreurs().size());
    }
}
