package fr.abes.bestppn.dto;

import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LigneKbartDtoTest {

    @Test
    void toStringTest() {
        LigneKbartDto ligne = new LigneKbartDto();
        ligne.setOnline_identifier("");
        ligne.setPrint_identifier("");
        ligne.setPublication_title("test");
        ligne.setPublication_type("monograph");

        Assertions.assertEquals("publication title : test / publication_type : monograph", ligne.toString());

        ligne.setOnline_identifier("11111111");
        Assertions.assertEquals("publication title : test / publication_type : monograph / online_identifier : 11111111", ligne.toString());

        ligne.setPrint_identifier("987123456789");
        Assertions.assertEquals("publication title : test / publication_type : monograph / online_identifier : 11111111 / print_identifier : 987123456789", ligne.toString());

        ligne.setOnline_identifier("");
        Assertions.assertEquals("publication title : test / publication_type : monograph / print_identifier : 987123456789", ligne.toString());
    }

//    @Test
//    void isBestPpnEmpty() {
//        LigneKbartDto ligneWithoutBestPpn = new LigneKbartDto();
//        LigneKbartDto ligneWithBestPpn = new LigneKbartDto();
//        ligneWithBestPpn.setBestPpn("123456789");
//
//        Assertions.assertTrue(ligneWithoutBestPpn.isBestPpnEmpty());
//
//        Assertions.assertEquals("123456789", ligneWithBestPpn.getBestPpn());
//    }
}
