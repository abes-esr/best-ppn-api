package fr.abes.bestppn.utils;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TieBreakNormalizerTest {

    @Test
    void normaliseTexteSansPerdreLesChiffres() {
        assertEquals("edition numero 2", TieBreakNormalizer.normalizeText("  Édition, numéro 2  "));
    }

    @Test
    void normaliseLesVariantesDeVolume() {
        assertEquals(Optional.of("2"), TieBreakNormalizer.normalizeVolume("02"));
        assertEquals(Optional.of("2"), TieBreakNormalizer.normalizeVolume("Tome 2"));
        assertEquals(Optional.of("2"), TieBreakNormalizer.normalizeVolume("Vol. 2"));
        assertEquals(Optional.of("2"), TieBreakNormalizer.normalizeVolume("Part II"));
        assertEquals(Optional.empty(), TieBreakNormalizer.normalizeVolume("IIII"));
    }

    @Test
    void extraitUnVolumeUniquementAvecUnPrefixeDansLeTitre() {
        assertEquals(Optional.of("12"), TieBreakNormalizer.extractVolumeFromTitle("Traité pratique, volume XII"));
        assertEquals(Optional.empty(), TieBreakNormalizer.extractVolumeFromTitle("Les douze travaux"));
    }

    @Test
    void extraitLaDate1DeLaZone100EtNonLaDateDeSaisie() {
        assertEquals(OptionalInt.of(2021), TieBreakNormalizer.extractDate1From100("20240723d2021    m  y0frey50      ba"));
        assertEquals(OptionalInt.empty(), TieBreakNormalizer.extractDate1From100("valeur courte"));
    }

    @Test
    void extraitLesAnneesDesMentionsDePublication() {
        assertEquals(OptionalInt.of(2021), TieBreakNormalizer.extractYear("DL 2021"));
        assertEquals(Set.of(2020, 2021), TieBreakNormalizer.extractYears("cop. 2020-2021"));
    }

    @Test
    void reconnaitUnCodeProviderCommeMotComplet() {
        assertTrue(TieBreakNormalizer.containsWord("diffusion cairn info", "cairn"));
        assertFalse(TieBreakNormalizer.containsWord("diffusion cairninfo", "cairn"));
    }
}
