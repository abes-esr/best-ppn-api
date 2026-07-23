# SOA-501 — Départage des PPN ex aequo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Départager de manière conservatrice les PPN électroniques ayant le même score maximal en appliquant successivement SUPPORT, DIFFUSEUR, VOLUME et ANNÉE.

**Architecture:** `BestPpnService` conserve les recherches et les scores actuels, puis délègue uniquement les égalités maximales à `BestPpnTieBreakerService`. Le nouveau service charge chaque notice XML une seule fois, confie l’extraction à `CandidateMetadataExtractor`, construit une clé lexicographique et ne renvoie un PPN que si le meilleur candidat est unique et sans contradiction forte.

**Tech Stack:** Java 21, Spring Boot 3.5.6, JUnit 5, Mockito, Maven, données UNIMARC XML de la base APISUDOC.

## Global Constraints

- Dépôt modifié : `best-ppn-api` uniquement.
- Branche existante : `SOA-501-affiner-departage-bestppn`, créée depuis `origin/develop`.
- Aucun changement du contrat JSON ni du code de `sudoc-api`.
- Aucun changement des scores existants : 10, 8, 6, 15 et 20.
- Aucun changement de l’enchaînement `dat2ppn`.
- L’arbitrage ne s’exécute que pour plusieurs PPN électroniques au score maximal.
- Ordre lexicographique obligatoire : score actuel, SUPPORT, DIFFUSEUR, VOLUME, ANNÉE.
- SUPPORT physique, VOLUME explicitement incompatible et ANNÉE explicitement incompatible sont des contradictions fortes.
- Le rang DIFFUSEUR 0 ne constitue pas à lui seul une contradiction forte.
- Le booléen agrégé `providerPresent` n’est pas utilisé pour calculer le rang DIFFUSEUR.
- Pour une publication en série, seuls le score actuel et SUPPORT sont discriminants.
- Une notice absente, supprimée ou illisible conserve l’égalité.
- Aucune nouvelle dépendance Maven.
- Les titres et descriptions de commits sont rédigés en français.
- Les fichiers Avro `src/main/java/fr/abes/LigneKbartConnect.java` et `src/main/java/fr/abes/LigneKbartImprime.java`, régénérés par Maven, ne sont jamais ajoutés aux commits SOA-501.

---

## File Structure

### Fichiers créés

- `src/main/java/fr/abes/bestppn/model/tiebreak/AccessMode.java` : mode d’accès explicite extrait d’une notice.
- `src/main/java/fr/abes/bestppn/model/tiebreak/CandidateMetadata.java` : métadonnées normalisées et preuves issues d’une notice.
- `src/main/java/fr/abes/bestppn/model/tiebreak/CandidateDecisionKey.java` : clé lexicographique et contrôle des contradictions fortes.
- `src/main/java/fr/abes/bestppn/model/tiebreak/TieBreakDecision.java` : résultat résolu ou indécidable avec clés et motif.
- `src/main/java/fr/abes/bestppn/utils/TieBreakNormalizer.java` : normalisation textuelle, années et volumes.
- `src/main/java/fr/abes/bestppn/service/CandidateMetadataExtractor.java` : extraction pure des zones UNIMARC.
- `src/main/java/fr/abes/bestppn/service/BestPpnTieBreakerService.java` : chargement XML, calcul des rangs, cascade et journalisation.
- `src/test/java/fr/abes/bestppn/utils/TieBreakNormalizerTest.java` : tests de normalisation.
- `src/test/java/fr/abes/bestppn/model/tiebreak/CandidateDecisionKeyTest.java` : tests de comparaison et veto.
- `src/test/java/fr/abes/bestppn/service/CandidateMetadataExtractorTest.java` : tests des zones XML.
- `src/test/java/fr/abes/bestppn/service/BestPpnTieBreakerServiceTest.java` : tests unitaires des règles d’arbitrage.
- `src/test/java/fr/abes/bestppn/service/BestPpnServiceTieBreakTest.java` : tests de l’intégration au calcul actuel.
- `src/test/java/fr/abes/bestppn/service/BestPpnTieBreakerFlowTest.java` : non-régression du flux XML complet.

### Fichiers modifiés

- `src/main/java/fr/abes/bestppn/service/BestPpnService.java:40-54` : remplacer la dépendance XML directe par le service d’arbitrage.
- `src/main/java/fr/abes/bestppn/service/BestPpnService.java:81` : transmettre le provider au calcul final.
- `src/main/java/fr/abes/bestppn/service/BestPpnService.java:199-247` : appeler l’arbitrage avant l’erreur d’égalité actuelle.
- `src/test/java/fr/abes/bestppn/service/BestPpnServiceTest.java:35-51` : fournir le mock du nouveau service.
- `src/test/java/fr/abes/bestppn/service/BestPpnServiceTest.java:358-401` : adapter la signature publique testée.

---

### Task 1: Primitives de décision et normalisation

**Files:**

- Create: `src/main/java/fr/abes/bestppn/model/tiebreak/AccessMode.java`
- Create: `src/main/java/fr/abes/bestppn/model/tiebreak/CandidateMetadata.java`
- Create: `src/main/java/fr/abes/bestppn/model/tiebreak/CandidateDecisionKey.java`
- Create: `src/main/java/fr/abes/bestppn/model/tiebreak/TieBreakDecision.java`
- Create: `src/main/java/fr/abes/bestppn/utils/TieBreakNormalizer.java`
- Test: `src/test/java/fr/abes/bestppn/utils/TieBreakNormalizerTest.java`
- Test: `src/test/java/fr/abes/bestppn/model/tiebreak/CandidateDecisionKeyTest.java`

**Interfaces:**

- Consumes: chaînes brutes KBART ou UNIMARC.
- Produces: `TieBreakNormalizer.normalizeText(String)`, `extractYear(String)`, `extractDate1From100(String)`, `extractYears(String)`, `normalizeVolume(String)`, `extractVolumeFromTitle(String)` et `containsWord(String, String)`.
- Produces: `CandidateDecisionKey implements Comparable<CandidateDecisionKey>`.
- Produces: `TieBreakDecision.selected(...)`, `TieBreakDecision.unresolved(...)` et `TieBreakDecision.resolved()`.

- [ ] **Step 1: Write the failing normalization tests**

Créer `src/test/java/fr/abes/bestppn/utils/TieBreakNormalizerTest.java` :

```java
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
```

- [ ] **Step 2: Run the normalization test to verify it fails**

Run:

```powershell
mvn "-Dtest=TieBreakNormalizerTest" test
```

Expected: `FAIL` pendant `testCompile`, car `TieBreakNormalizer` n’existe pas.

- [ ] **Step 3: Implement the normalizer**

Créer `src/main/java/fr/abes/bestppn/utils/TieBreakNormalizer.java` :

```java
package fr.abes.bestppn.utils;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TieBreakNormalizer {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(?:18|19|20|21)\\d{2}(?!\\d)");
    private static final Pattern VOLUME = Pattern.compile(
            "^(?:(?:tome|volume|vol|partie|part|band)\\s*)?([0-9]+|[ivxlcdm]+)(?:\\s.*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VOLUME_IN_TITLE = Pattern.compile(
            "(?:^|\\s)(?:tome|volume|vol|partie|part|band)\\s+([0-9]+|[ivxlcdm]+)(?:\\s|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VALID_ROMAN = Pattern.compile("[IVXLCDM]+");

    private TieBreakNormalizer() {
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(decomposed).replaceAll("");
        String withoutPunctuation = NON_ALPHANUMERIC.matcher(withoutDiacritics).replaceAll(" ");
        return SPACES.matcher(withoutPunctuation.toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
    }

    public static OptionalInt extractYear(String value) {
        Matcher matcher = YEAR.matcher(value == null ? "" : value);
        return matcher.find() ? OptionalInt.of(Integer.parseInt(matcher.group())) : OptionalInt.empty();
    }

    public static OptionalInt extractDate1From100(String value) {
        if (value == null || value.length() < 13) {
            return OptionalInt.empty();
        }
        String date1 = value.substring(9, 13);
        return date1.matches("(?:18|19|20|21)\\d{2}")
                ? OptionalInt.of(Integer.parseInt(date1))
                : OptionalInt.empty();
    }

    public static Set<Integer> extractYears(String value) {
        Set<Integer> years = new LinkedHashSet<>();
        Matcher matcher = YEAR.matcher(value == null ? "" : value);
        while (matcher.find()) {
            years.add(Integer.parseInt(matcher.group()));
        }
        return Set.copyOf(years);
    }

    public static Optional<String> normalizeVolume(String value) {
        String normalized = normalizeText(value);
        Matcher matcher = VOLUME.matcher(normalized);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String token = matcher.group(1);
        if (token.chars().allMatch(Character::isDigit)) {
            return Optional.of(Integer.toString(Integer.parseInt(token)));
        }
        return romanToArabic(token).map(String::valueOf);
    }

    public static Optional<String> extractVolumeFromTitle(String title) {
        Matcher matcher = VOLUME_IN_TITLE.matcher(normalizeText(title));
        return matcher.find() ? normalizeVolume(matcher.group(1)) : Optional.empty();
    }

    public static boolean containsWord(String text, String word) {
        String normalizedText = normalizeText(text);
        String normalizedWord = normalizeText(word);
        if (normalizedWord.isBlank()) {
            return false;
        }
        Pattern completeWord = Pattern.compile("(^|\\s)" + Pattern.quote(normalizedWord) + "($|\\s)");
        return completeWord.matcher(normalizedText).find();
    }

    private static Optional<Integer> romanToArabic(String value) {
        String roman = value.toUpperCase(Locale.ROOT);
        if (!VALID_ROMAN.matcher(roman).matches()) {
            return Optional.empty();
        }
        int total = 0;
        int previous = 0;
        for (int index = roman.length() - 1; index >= 0; index--) {
            int current = romanValue(roman.charAt(index));
            total += current < previous ? -current : current;
            previous = current;
        }
        return total > 0 && toRoman(total).equals(roman) ? Optional.of(total) : Optional.empty();
    }

    private static int romanValue(char value) {
        return switch (value) {
            case 'I' -> 1;
            case 'V' -> 5;
            case 'X' -> 10;
            case 'L' -> 50;
            case 'C' -> 100;
            case 'D' -> 500;
            case 'M' -> 1000;
            default -> 0;
        };
    }

    private static String toRoman(int value) {
        int[] arabic = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] roman = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        int remaining = value;
        for (int index = 0; index < arabic.length; index++) {
            while (remaining >= arabic[index]) {
                result.append(roman[index]);
                remaining -= arabic[index];
            }
        }
        return result.toString();
    }
}
```

- [ ] **Step 4: Run the normalization tests**

Run:

```powershell
mvn "-Dtest=TieBreakNormalizerTest" test
```

Expected: `Tests run: 6, Failures: 0, Errors: 0` puis `BUILD SUCCESS`.

- [ ] **Step 5: Write the failing decision-model tests**

Créer `src/test/java/fr/abes/bestppn/model/tiebreak/CandidateDecisionKeyTest.java` :

```java
package fr.abes.bestppn.model.tiebreak;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CandidateDecisionKeyTest {

    @Test
    void compareDansLOrdreScoreSupportDiffuseurVolumeAnnee() {
        CandidateDecisionKey supportFort = new CandidateDecisionKey(10, 2, 0, 0, 0);
        CandidateDecisionKey diffuseurFort = new CandidateDecisionKey(10, 1, 2, 3, 3);
        CandidateDecisionKey scoreFort = new CandidateDecisionKey(11, 0, 0, 0, 0);

        assertTrue(supportFort.compareTo(diffuseurFort) > 0);
        assertTrue(scoreFort.compareTo(supportFort) > 0);
    }

    @Test
    void identifieUniquementLesContradictionsFortes() {
        assertTrue(new CandidateDecisionKey(10, 0, 2, 3, 3).hasStrongContradiction());
        assertTrue(new CandidateDecisionKey(10, 2, 2, 0, 3).hasStrongContradiction());
        assertTrue(new CandidateDecisionKey(10, 2, 2, 3, 0).hasStrongContradiction());
        assertFalse(new CandidateDecisionKey(10, 2, 0, 3, 3).hasStrongContradiction());
    }

    @Test
    void exposeUneDecisionResolueOuIndecidable() {
        CandidateDecisionKey key = new CandidateDecisionKey(10, 2, 1, 1, 1);
        TieBreakDecision selected = TieBreakDecision.selected("123456789", Map.of("123456789", key), "SUPPORT");
        TieBreakDecision unresolved = TieBreakDecision.unresolved(Map.of("123456789", key), "égalité");

        assertTrue(selected.resolved());
        assertEquals("123456789", selected.selectedPpn());
        assertFalse(unresolved.resolved());
        assertNull(unresolved.selectedPpn());
    }

    @Test
    void detecteDesAnneesSudocContradictoires() {
        CandidateMetadata metadata = new CandidateMetadata(
                "123456789",
                AccessMode.ONLINE,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(2021),
                Set.of(2020, 2021),
                List.of(),
                false);

        assertTrue(metadata.hasContradictoryYears());
    }
}
```

- [ ] **Step 6: Run the decision-model test to verify it fails**

Run:

```powershell
mvn "-Dtest=CandidateDecisionKeyTest" test
```

Expected: `FAIL` pendant `testCompile`, car les types `model.tiebreak` n’existent pas.

- [ ] **Step 7: Implement the decision models**

Créer `src/main/java/fr/abes/bestppn/model/tiebreak/AccessMode.java` :

```java
package fr.abes.bestppn.model.tiebreak;

public enum AccessMode {
    ONLINE,
    PHYSICAL,
    UNKNOWN
}
```

Créer `src/main/java/fr/abes/bestppn/model/tiebreak/CandidateMetadata.java` :

```java
package fr.abes.bestppn.model.tiebreak;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record CandidateMetadata(
        String ppn,
        AccessMode accessMode,
        Set<String> distributors,
        Set<String> volumeNumbers,
        Set<String> partTitles,
        Set<Integer> yearsFrom100,
        Set<Integer> yearsFrom214,
        List<String> evidence,
        boolean unreadable) {

    public CandidateMetadata {
        distributors = immutableSet(distributors);
        volumeNumbers = immutableSet(volumeNumbers);
        partTitles = immutableSet(partTitles);
        yearsFrom100 = immutableSet(yearsFrom100);
        yearsFrom214 = immutableSet(yearsFrom214);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static CandidateMetadata unreadable(String ppn, String reason) {
        return new CandidateMetadata(
                ppn,
                AccessMode.UNKNOWN,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                List.of(reason),
                true);
    }

    public Set<Integer> allYears() {
        Set<Integer> years = new LinkedHashSet<>(yearsFrom100);
        years.addAll(yearsFrom214);
        return Collections.unmodifiableSet(years);
    }

    public boolean hasContradictoryYears() {
        return allYears().size() > 1;
    }

    private static <T> Set<T> immutableSet(Set<T> values) {
        return values == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
```

Créer `src/main/java/fr/abes/bestppn/model/tiebreak/CandidateDecisionKey.java` :

```java
package fr.abes.bestppn.model.tiebreak;

import java.util.Comparator;

public record CandidateDecisionKey(
        int currentScore,
        int supportRank,
        int distributorRank,
        int volumeRank,
        int yearRank) implements Comparable<CandidateDecisionKey> {

    private static final Comparator<CandidateDecisionKey> COMPARATOR =
            Comparator.comparingInt(CandidateDecisionKey::currentScore)
                    .thenComparingInt(CandidateDecisionKey::supportRank)
                    .thenComparingInt(CandidateDecisionKey::distributorRank)
                    .thenComparingInt(CandidateDecisionKey::volumeRank)
                    .thenComparingInt(CandidateDecisionKey::yearRank);

    @Override
    public int compareTo(CandidateDecisionKey other) {
        return COMPARATOR.compare(this, other);
    }

    public boolean hasStrongContradiction() {
        return supportRank == 0 || volumeRank == 0 || yearRank == 0;
    }
}
```

Créer `src/main/java/fr/abes/bestppn/model/tiebreak/TieBreakDecision.java` :

```java
package fr.abes.bestppn.model.tiebreak;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record TieBreakDecision(
        String selectedPpn,
        Map<String, CandidateDecisionKey> keys,
        String reason) {

    public TieBreakDecision {
        keys = keys == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(keys));
    }

    public static TieBreakDecision selected(
            String ppn,
            Map<String, CandidateDecisionKey> keys,
            String decisiveCriterion) {
        return new TieBreakDecision(ppn, keys, decisiveCriterion);
    }

    public static TieBreakDecision unresolved(
            Map<String, CandidateDecisionKey> keys,
            String reason) {
        return new TieBreakDecision(null, keys, reason);
    }

    public boolean resolved() {
        return selectedPpn != null;
    }
}
```

- [ ] **Step 8: Run all Task 1 tests**

Run:

```powershell
mvn "-Dtest=TieBreakNormalizerTest,CandidateDecisionKeyTest" test
```

Expected: `Tests run: 10, Failures: 0, Errors: 0` puis `BUILD SUCCESS`.

- [ ] **Step 9: Commit Task 1**

Run:

```powershell
git add src/main/java/fr/abes/bestppn/model/tiebreak src/main/java/fr/abes/bestppn/utils/TieBreakNormalizer.java src/test/java/fr/abes/bestppn/model/tiebreak src/test/java/fr/abes/bestppn/utils/TieBreakNormalizerTest.java
git -c user.name="Jerome Villiseck" -c user.email="jvk@abes.fr" commit -m "feat: ajouter les primitives de décision SOA-501"
```

Expected: un commit contenant uniquement les sept fichiers de la tâche.

---

### Task 2: Extraction des métadonnées UNIMARC

**Files:**

- Create: `src/main/java/fr/abes/bestppn/service/CandidateMetadataExtractor.java`
- Test: `src/test/java/fr/abes/bestppn/service/CandidateMetadataExtractorTest.java`

**Interfaces:**

- Consumes: `CandidateMetadataExtractor.extract(String ppn, NoticeXml notice)`.
- Produces: `CandidateMetadata` avec `AccessMode`, `214 #2$c`, `200$h`, `200$i`, date 1 de `100$a`, années de `214$d` et preuves `zone$sous-zone=valeur`.

- [ ] **Step 1: Write the failing extractor tests**

Créer `src/test/java/fr/abes/bestppn/service/CandidateMetadataExtractorTest.java` :

```java
package fr.abes.bestppn.service;

import fr.abes.bestppn.model.entity.basexml.notice.Controlfield;
import fr.abes.bestppn.model.entity.basexml.notice.Datafield;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.model.entity.basexml.notice.SubField;
import fr.abes.bestppn.model.tiebreak.AccessMode;
import fr.abes.bestppn.model.tiebreak.CandidateMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CandidateMetadataExtractorTest {
    private final CandidateMetadataExtractor extractor = new CandidateMetadataExtractor();

    @Test
    void extraitToutesLesMetadonneesUtiles() {
        NoticeXml notice = notice(
                field("530", " ", "b", "Ressource en ligne"),
                field("214", "2", "c", "Cairn.info", "d", "[2021]"),
                field("200", " ", "h", "Part II", "i", "Méthodes numériques"),
                field("100", " ", "a", "20240723d2021    m  y0frey50      ba"));

        CandidateMetadata metadata = extractor.extract("123456789", notice);

        assertEquals(AccessMode.ONLINE, metadata.accessMode());
        assertEquals(Set.of("cairn info"), metadata.distributors());
        assertEquals(Set.of("2"), metadata.volumeNumbers());
        assertEquals(Set.of("methodes numeriques"), metadata.partTitles());
        assertEquals(Set.of(2021), metadata.yearsFrom100());
        assertEquals(Set.of(2021), metadata.yearsFrom214());
        assertTrue(metadata.evidence().contains("530$b=Ressource en ligne"));
        assertFalse(metadata.unreadable());
    }

    @Test
    void reconnaitUnSupportPhysiqueDansLesZonesDescriptives() {
        NoticeXml notice = notice(
                field("531", " ", "b", "CD-ROM"),
                field("215", " ", "a", "1 disque optique numérique (DVD-ROM)"));

        assertEquals(AccessMode.PHYSICAL, extractor.extract("123456789", notice).accessMode());
    }

    @Test
    void conserveUnSupportHybrideCommeInconnu() {
        NoticeXml notice = notice(
                field("530", " ", "b", "Ressource en ligne"),
                field("215", " ", "a", "1 CD-ROM"));

        assertEquals(AccessMode.UNKNOWN, extractor.extract("123456789", notice).accessMode());
    }

    @Test
    void neClassePasLaSeuleValeurODe008CommeAccesEnLigne() {
        NoticeXml notice = notice();

        assertEquals(AccessMode.UNKNOWN, extractor.extract("123456789", notice).accessMode());
    }

    @Test
    void neConserveQueLes214QuiPortentLeSecondIndicateur2() {
        NoticeXml notice = notice(
                field("214", "0", "c", "Autre diffuseur"),
                field("214", "2", "c", "Cairn.info"));

        assertEquals(
                Set.of("cairn info"),
                extractor.extract("123456789", notice).distributors());
    }

    @Test
    void extraitPlusieursAnnees214CommeDonneesContradictoires() {
        NoticeXml notice = notice(
                field("214", "0", "d", "DL 2020"),
                field("214", "0", "d", "cop. 2021"));

        CandidateMetadata metadata = extractor.extract("123456789", notice);

        assertEquals(Set.of(2020, 2021), metadata.yearsFrom214());
        assertTrue(metadata.hasContradictoryYears());
    }

    @Test
    void marqueUneNoticeAbsenteOuSupprimeeCommeIllisible() {
        assertTrue(extractor.extract("123456789", null).unreadable());

        NoticeXml deleted = notice();
        deleted.setLeader("     dam0 22        450 ");
        assertTrue(extractor.extract("123456789", deleted).unreadable());
    }

    @Test
    void tolereLesListesEtValeursNulles() {
        NoticeXml notice = new NoticeXml();
        notice.setLeader("     nam0 22        450 ");
        notice.setControlfields(null);
        notice.setDatafields(List.of(field("200", " ", "h", null)));

        CandidateMetadata metadata = extractor.extract("123456789", notice);

        assertFalse(metadata.unreadable());
        assertTrue(metadata.volumeNumbers().isEmpty());
    }

    private static NoticeXml notice(Datafield... fields) {
        NoticeXml notice = new NoticeXml();
        notice.setLeader("     nam0 22        450 ");
        Controlfield ppn = new Controlfield();
        ppn.setTag("001");
        ppn.setValue("123456789");
        Controlfield type = new Controlfield();
        type.setTag("008");
        type.setValue("Oax3");
        notice.setControlfields(List.of(ppn, type));
        notice.setDatafields(List.of(fields));
        return notice;
    }

    private static Datafield field(String tag, String ind2, String... codeValues) {
        Datafield field = new Datafield();
        field.setTag(tag);
        field.setInd1(" ");
        field.setInd2(ind2);
        List<SubField> subFields = new ArrayList<>();
        for (int index = 0; index < codeValues.length; index += 2) {
            SubField subField = new SubField();
            subField.setCode(codeValues[index]);
            subField.setValue(codeValues[index + 1]);
            subFields.add(subField);
        }
        field.setSubFields(subFields);
        return field;
    }
}
```

- [ ] **Step 2: Run the extractor test to verify it fails**

Run:

```powershell
mvn "-Dtest=CandidateMetadataExtractorTest" test
```

Expected: `FAIL` pendant `testCompile`, car `CandidateMetadataExtractor` n’existe pas.

- [ ] **Step 3: Implement the XML extractor**

Créer `src/main/java/fr/abes/bestppn/service/CandidateMetadataExtractor.java` :

```java
package fr.abes.bestppn.service;

import fr.abes.bestppn.model.entity.basexml.notice.Controlfield;
import fr.abes.bestppn.model.entity.basexml.notice.Datafield;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.model.entity.basexml.notice.SubField;
import fr.abes.bestppn.model.tiebreak.AccessMode;
import fr.abes.bestppn.model.tiebreak.CandidateMetadata;
import fr.abes.bestppn.utils.TieBreakNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class CandidateMetadataExtractor {
    private static final List<String> ONLINE_MARKERS =
            List.of("online", "en ligne", "ressource en ligne", "acces internet");
    private static final List<String> PHYSICAL_MARKERS =
            List.of("cd rom", "cederom", "dvd rom", "disque optique");

    public CandidateMetadata extract(String ppn, NoticeXml notice) {
        if (notice == null) {
            return CandidateMetadata.unreadable(ppn, "notice absente");
        }
        if (notice.getLeader() != null
                && notice.getLeader().length() > 5
                && notice.getLeader().charAt(5) == 'd') {
            return CandidateMetadata.unreadable(ppn, "notice supprimée");
        }

        Set<String> supportValues = new LinkedHashSet<>();
        Set<String> distributors = new LinkedHashSet<>();
        Set<String> volumeNumbers = new LinkedHashSet<>();
        Set<String> partTitles = new LinkedHashSet<>();
        Set<Integer> yearsFrom100 = new LinkedHashSet<>();
        Set<Integer> yearsFrom214 = new LinkedHashSet<>();
        List<String> evidence = new ArrayList<>();

        for (Controlfield controlfield : safeControlfields(notice)) {
            if (controlfield != null && Objects.equals("008", controlfield.getTag())) {
                addEvidence(evidence, "008", null, controlfield.getValue());
            }
        }

        for (Datafield field : safeDatafields(notice)) {
            if (field == null || field.getTag() == null) {
                continue;
            }
            switch (field.getTag()) {
                case "530", "531" -> values(field, "b").forEach(value -> {
                    addNormalized(supportValues, value);
                    addEvidence(evidence, field.getTag(), "b", value);
                });
                case "215" -> safeSubfields(field).forEach(subfield -> {
                    addNormalized(supportValues, subfield.getValue());
                    addEvidence(evidence, "215", subfield.getCode(), subfield.getValue());
                });
                case "214" -> extractPublicationField(
                        field, distributors, yearsFrom214, evidence);
                case "200" -> extractTitleField(
                        field, volumeNumbers, partTitles, evidence);
                case "100" -> values(field, "a").forEach(value -> {
                    TieBreakNormalizer.extractDate1From100(value).ifPresent(yearsFrom100::add);
                    addEvidence(evidence, "100", "a", value);
                });
                default -> {
                }
            }
        }

        return new CandidateMetadata(
                ppn,
                accessMode(supportValues),
                distributors,
                volumeNumbers,
                partTitles,
                yearsFrom100,
                yearsFrom214,
                evidence,
                false);
    }

    private void extractPublicationField(
            Datafield field,
            Set<String> distributors,
            Set<Integer> years,
            List<String> evidence) {
        values(field, "d").forEach(value -> {
            years.addAll(TieBreakNormalizer.extractYears(value));
            addEvidence(evidence, "214", "d", value);
        });
        if (Objects.equals("2", field.getInd2())) {
            values(field, "c").forEach(value -> {
                addNormalized(distributors, value);
                addEvidence(evidence, "214", "c", value);
            });
        }
    }

    private void extractTitleField(
            Datafield field,
            Set<String> volumeNumbers,
            Set<String> partTitles,
            List<String> evidence) {
        values(field, "h").forEach(value -> {
            TieBreakNormalizer.normalizeVolume(value).ifPresent(volumeNumbers::add);
            addEvidence(evidence, "200", "h", value);
        });
        values(field, "i").forEach(value -> {
            addNormalized(partTitles, value);
            addEvidence(evidence, "200", "i", value);
        });
    }

    private AccessMode accessMode(Set<String> supportValues) {
        boolean online = containsMarker(supportValues, ONLINE_MARKERS);
        boolean physical = containsMarker(supportValues, PHYSICAL_MARKERS);
        if (online == physical) {
            return AccessMode.UNKNOWN;
        }
        return online ? AccessMode.ONLINE : AccessMode.PHYSICAL;
    }

    private boolean containsMarker(Set<String> values, List<String> markers) {
        return values.stream().anyMatch(value ->
                markers.stream().anyMatch(value::contains));
    }

    private List<String> values(Datafield field, String code) {
        return safeSubfields(field).stream()
                .filter(subfield -> Objects.equals(code, subfield.getCode()))
                .map(SubField::getValue)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<Datafield> safeDatafields(NoticeXml notice) {
        return notice.getDatafields() == null ? List.of() : notice.getDatafields();
    }

    private List<Controlfield> safeControlfields(NoticeXml notice) {
        return notice.getControlfields() == null ? List.of() : notice.getControlfields();
    }

    private List<SubField> safeSubfields(Datafield field) {
        return field.getSubFields() == null ? List.of() : field.getSubFields();
    }

    private void addNormalized(Set<String> target, String value) {
        String normalized = TieBreakNormalizer.normalizeText(value);
        if (!normalized.isBlank()) {
            target.add(normalized);
        }
    }

    private void addEvidence(List<String> evidence, String tag, String code, String value) {
        if (value != null && !value.isBlank()) {
            evidence.add(tag + (code == null ? "" : "$" + code) + "=" + value);
        }
    }
}
```

- [ ] **Step 4: Run the extractor tests**

Run:

```powershell
mvn "-Dtest=CandidateMetadataExtractorTest" test
```

Expected: `Tests run: 8, Failures: 0, Errors: 0` puis `BUILD SUCCESS`.

- [ ] **Step 5: Commit Task 2**

Run:

```powershell
git add src/main/java/fr/abes/bestppn/service/CandidateMetadataExtractor.java src/test/java/fr/abes/bestppn/service/CandidateMetadataExtractorTest.java
git -c user.name="Jerome Villiseck" -c user.email="jvk@abes.fr" commit -m "feat: extraire les métadonnées de départage des notices"
```

Expected: un commit contenant l’extracteur et ses tests uniquement.

---

### Task 3: Moteur d’arbitrage SOA-501

**Files:**

- Create: `src/main/java/fr/abes/bestppn/service/BestPpnTieBreakerService.java`
- Test: `src/test/java/fr/abes/bestppn/service/BestPpnTieBreakerServiceTest.java`

**Interfaces:**

- Consumes: `resolve(LigneKbartDto kbart, String providerCode, Map<String, Integer> tiedCandidates)`.
- Consumes: `NoticeService.getNoticeByPpn(String)` et `ProviderService.findByProvider(String)`.
- Produces: une `TieBreakDecision` résolue uniquement si une clé est strictement maximale et sans contradiction forte.

- [ ] **Step 1: Write the failing arbitration tests**

Créer `src/test/java/fr/abes/bestppn/service/BestPpnTieBreakerServiceTest.java` :

```java
package fr.abes.bestppn.service;

import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.entity.bacon.Provider;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.model.tiebreak.AccessMode;
import fr.abes.bestppn.model.tiebreak.CandidateMetadata;
import fr.abes.bestppn.model.tiebreak.TieBreakDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BestPpnTieBreakerServiceTest {
    @Mock
    private NoticeService noticeService;
    @Mock
    private ProviderService providerService;
    @Mock
    private CandidateMetadataExtractor extractor;

    private BestPpnTieBreakerService service;

    @BeforeEach
    void setUp() {
        service = new BestPpnTieBreakerService(noticeService, providerService, extractor);
    }

    @Test
    void supportEstPrioritairePourUneSerie() throws IOException {
        LigneKbartDto kbart = kbart("serial");
        candidate("100000001", metadata("100000001", AccessMode.ONLINE));
        candidate("100000002", metadata("100000002", AccessMode.UNKNOWN));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertTrue(decision.resolved());
        assertEquals("100000001", decision.selectedPpn());
        assertEquals("SUPPORT", decision.reason());
    }

    @Test
    void diffuseurDepartageDeuxMonographies() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        Provider provider = provider("CAIRN", "Cairn.info");
        when(providerService.findByProvider("CAIRN")).thenReturn(Optional.of(provider));
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of("cairn info"), Set.of(), Set.of(), Set.of(), Set.of()));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of("autre diffuseur"), Set.of(), Set.of(), Set.of(), Set.of()));

        TieBreakDecision decision = service.resolve(kbart, "CAIRN", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals(2, decision.keys().get("100000001").distributorRank());
        assertEquals(0, decision.keys().get("100000002").distributorRank());
    }

    @Test
    void volumeExactDepartageDeuxMonographies() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setMonographVolume("Tome II");
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of(), Set.of("2"), Set.of(), Set.of(), Set.of()));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of(), Set.of("3"), Set.of(), Set.of(), Set.of()));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals(3, decision.keys().get("100000001").volumeRank());
        assertEquals(0, decision.keys().get("100000002").volumeRank());
    }

    @Test
    void titreDePartieDepartageSansMonographVolume() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setPublicationTitle("Traité complet - Méthodes numériques");
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of(), Set.of(), Set.of("methodes numeriques"), Set.of(), Set.of()));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of(), Set.of(), Set.of("autre partie"), Set.of(), Set.of()));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals(2, decision.keys().get("100000001").volumeRank());
        assertEquals(1, decision.keys().get("100000002").volumeRank());
    }

    @Test
    void anneeEnLigneEstPrioritaireSurAnneeImprimee() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setDateMonographPublishedOnline("2021-01-01");
        kbart.setDateMonographPublishedPrint("2020");
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of(), Set.of(), Set.of(), Set.of(2021), Set.of(2021)));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of(), Set.of(), Set.of(), Set.of(2020), Set.of(2020)));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals(3, decision.keys().get("100000001").yearRank());
        assertEquals(2, decision.keys().get("100000002").yearRank());
    }

    @Test
    void egaliteDeClesResteIndecidable() throws IOException {
        LigneKbartDto kbart = kbart("serial");
        candidate("100000001", metadata("100000001", AccessMode.ONLINE));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertFalse(decision.resolved());
        assertEquals("clés identiques", decision.reason());
    }

    @Test
    void lectureXmlImpossibleConserveLegalite() throws IOException {
        LigneKbartDto kbart = kbart("serial");
        when(noticeService.getNoticeByPpn("100000001")).thenThrow(new IOException("base indisponible"));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertFalse(decision.resolved());
        assertEquals("notice absente, supprimée ou illisible", decision.reason());
        verify(noticeService, times(1)).getNoticeByPpn("100000001");
        verify(noticeService, times(1)).getNoticeByPpn("100000002");
    }

    @Test
    void supportPhysiqueInterditLaSelection() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        Provider provider = provider("CAIRN", "Cairn.info");
        when(providerService.findByProvider("CAIRN")).thenReturn(Optional.of(provider));
        candidate("100000001", metadata("100000001", AccessMode.PHYSICAL, Set.of("cairn info"), Set.of(), Set.of(), Set.of(), Set.of()));
        candidate("100000002", metadata("100000002", AccessMode.PHYSICAL, Set.of("autre diffuseur"), Set.of(), Set.of(), Set.of(), Set.of()));

        TieBreakDecision decision = service.resolve(kbart, "CAIRN", ties());

        assertFalse(decision.resolved());
        assertEquals("contradiction forte sur le meilleur candidat", decision.reason());
    }

    @Test
    void volumeExplicitementIncompatibleInterditLaSelection() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setMonographVolume("2");
        kbart.setDateMonographPublishedOnline("2021");
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of(), Set.of("3"), Set.of(), Set.of(2021), Set.of(2021)));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of(), Set.of("4"), Set.of(), Set.of(2020), Set.of(2020)));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertFalse(decision.resolved());
        assertEquals(0, decision.keys().get("100000001").volumeRank());
    }

    @Test
    void anneeExplicitementIncompatibleInterditLaSelection() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setDateMonographPublishedOnline("2021");
        Provider provider = provider("CAIRN", "Cairn.info");
        when(providerService.findByProvider("CAIRN")).thenReturn(Optional.of(provider));
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of("cairn info"), Set.of(), Set.of(), Set.of(2019), Set.of(2019)));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of("autre diffuseur"), Set.of(), Set.of(), Set.of(2018), Set.of(2018)));

        TieBreakDecision decision = service.resolve(kbart, "CAIRN", ties());

        assertFalse(decision.resolved());
        assertEquals(0, decision.keys().get("100000001").yearRank());
    }

    @Test
    void seriesIgnorentDiffuseurVolumeEtAnnee() throws IOException {
        LigneKbartDto kbart = kbart("serial");
        kbart.setMonographVolume("2");
        kbart.setDateMonographPublishedOnline("2021");
        candidate("100000001", metadata("100000001", AccessMode.ONLINE, Set.of("cairn info"), Set.of("2"), Set.of(), Set.of(2021), Set.of()));
        candidate("100000002", metadata("100000002", AccessMode.ONLINE, Set.of("autre"), Set.of("3"), Set.of(), Set.of(2020), Set.of()));

        TieBreakDecision decision = service.resolve(kbart, "CAIRN", ties());

        assertFalse(decision.resolved());
        assertEquals(1, decision.keys().get("100000001").distributorRank());
        assertEquals(1, decision.keys().get("100000001").volumeRank());
        assertEquals(1, decision.keys().get("100000001").yearRank());
    }

    private void candidate(String ppn, CandidateMetadata metadata) throws IOException {
        NoticeXml notice = new NoticeXml();
        when(noticeService.getNoticeByPpn(ppn)).thenReturn(notice);
        when(extractor.extract(ppn, notice)).thenReturn(metadata);
    }

    private static LigneKbartDto kbart(String publicationType) {
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setPublicationType(publicationType);
        kbart.setPublicationTitle("Titre");
        return kbart;
    }

    private static Map<String, Integer> ties() {
        Map<String, Integer> candidates = new LinkedHashMap<>();
        candidates.put("100000001", 10);
        candidates.put("100000002", 10);
        return candidates;
    }

    private static CandidateMetadata metadata(String ppn, AccessMode accessMode) {
        return metadata(ppn, accessMode, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    private static CandidateMetadata metadata(
            String ppn,
            AccessMode accessMode,
            Set<String> distributors,
            Set<String> volumeNumbers,
            Set<String> partTitles,
            Set<Integer> yearsFrom100,
            Set<Integer> yearsFrom214) {
        return new CandidateMetadata(
                ppn,
                accessMode,
                distributors,
                volumeNumbers,
                partTitles,
                yearsFrom100,
                yearsFrom214,
                List.of(),
                false);
    }

    private static Provider provider(String code, String displayName) {
        Provider provider = new Provider(code);
        provider.setDisplayName(displayName);
        return provider;
    }
}
```

- [ ] **Step 2: Run the arbitration test to verify it fails**

Run:

```powershell
mvn "-Dtest=BestPpnTieBreakerServiceTest" test
```

Expected: `FAIL` pendant `testCompile`, car `BestPpnTieBreakerService` n’existe pas.

- [ ] **Step 3: Implement the arbitration service**

Créer `src/main/java/fr/abes/bestppn/service/BestPpnTieBreakerService.java` :

```java
package fr.abes.bestppn.service;

import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.entity.bacon.Provider;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.model.tiebreak.AccessMode;
import fr.abes.bestppn.model.tiebreak.CandidateDecisionKey;
import fr.abes.bestppn.model.tiebreak.CandidateMetadata;
import fr.abes.bestppn.model.tiebreak.TieBreakDecision;
import fr.abes.bestppn.utils.PUBLICATION_TYPE;
import fr.abes.bestppn.utils.TieBreakNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.ToIntFunction;

import static fr.abes.bestppn.utils.LogMarkers.FUNCTIONAL;
import static fr.abes.bestppn.utils.LogMarkers.TECHNICAL;

@Service
@Slf4j
public class BestPpnTieBreakerService {
    private final NoticeService noticeService;
    private final ProviderService providerService;
    private final CandidateMetadataExtractor extractor;

    public BestPpnTieBreakerService(
            NoticeService noticeService,
            ProviderService providerService,
            CandidateMetadataExtractor extractor) {
        this.noticeService = noticeService;
        this.providerService = providerService;
        this.extractor = extractor;
    }

    public TieBreakDecision resolve(
            LigneKbartDto kbart,
            String providerCode,
            Map<String, Integer> tiedCandidates) {
        if (tiedCandidates == null || tiedCandidates.size() < 2) {
            return TieBreakDecision.unresolved(Map.of(), "moins de deux candidats");
        }

        log.info(FUNCTIONAL,
                "Arbitrage SOA-501 : candidats={}, scores={}",
                tiedCandidates.keySet(),
                tiedCandidates);

        Optional<Provider> provider = findProvider(providerCode);
        Map<String, CandidateMetadata> metadataByPpn = loadMetadata(tiedCandidates.keySet());
        log.info(FUNCTIONAL,
                "Arbitrage SOA-501 : KBART provider={}, titre={}, volume={}, annéeEnLigne={}, annéeImprimée={}",
                TieBreakNormalizer.normalizeText(providerCode),
                TieBreakNormalizer.normalizeText(kbart.getPublicationTitle()),
                TieBreakNormalizer.normalizeVolume(kbart.getMonographVolume()).orElse(""),
                TieBreakNormalizer.extractYear(kbart.getDateMonographPublishedOnline())
                        .stream().boxed().findFirst().orElse(null),
                TieBreakNormalizer.extractYear(kbart.getDateMonographPublishedPrint())
                        .stream().boxed().findFirst().orElse(null));
        Map<String, CandidateDecisionKey> keys = buildKeys(
                kbart, provider.orElse(null), tiedCandidates, metadataByPpn);

        keys.forEach((ppn, key) -> log.info(
                FUNCTIONAL,
                "Arbitrage SOA-501 : ppn={}, clé={}, preuves={}",
                ppn,
                key,
                metadataByPpn.get(ppn).evidence()));

        if (metadataByPpn.values().stream().anyMatch(CandidateMetadata::unreadable)) {
            log.warn(FUNCTIONAL,
                    "Arbitrage SOA-501 indécidable : notice absente, supprimée ou illisible");
            return TieBreakDecision.unresolved(
                    keys, "notice absente, supprimée ou illisible");
        }

        CandidateDecisionKey maxKey = keys.values().stream()
                .max(Comparator.naturalOrder())
                .orElseThrow();
        List<String> winners = keys.entrySet().stream()
                .filter(entry -> entry.getValue().equals(maxKey))
                .map(Map.Entry::getKey)
                .toList();

        if (winners.size() != 1) {
            log.warn(FUNCTIONAL,
                    "Arbitrage SOA-501 indécidable : clés identiques pour {}",
                    winners);
            return TieBreakDecision.unresolved(keys, "clés identiques");
        }

        String winner = winners.getFirst();
        String decisiveCriterion = logCascadeAndFindDecisiveCriterion(keys);
        if (maxKey.hasStrongContradiction()) {
            log.warn(FUNCTIONAL,
                    "Arbitrage SOA-501 refusé : ppn={}, clé={}, contradiction forte",
                    winner,
                    maxKey);
            return TieBreakDecision.unresolved(
                    keys, "contradiction forte sur le meilleur candidat");
        }

        log.info(FUNCTIONAL,
                "Arbitrage SOA-501 résolu : ppn={}, critère décisif={}",
                winner,
                decisiveCriterion);
        return TieBreakDecision.selected(winner, keys, decisiveCriterion);
    }

    private Optional<Provider> findProvider(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return Optional.empty();
        }
        try {
            return providerService.findByProvider(providerCode);
        } catch (RuntimeException exception) {
            log.error(TECHNICAL,
                    "Impossible de charger le provider {} pendant l'arbitrage SOA-501",
                    providerCode,
                    exception);
            return Optional.empty();
        }
    }

    private Map<String, CandidateMetadata> loadMetadata(Set<String> ppns) {
        Map<String, CandidateMetadata> result = new LinkedHashMap<>();
        ppns.stream().sorted().forEach(ppn -> {
            try {
                NoticeXml notice = noticeService.getNoticeByPpn(ppn);
                result.put(ppn, extractor.extract(ppn, notice));
            } catch (IOException | RuntimeException exception) {
                log.error(TECHNICAL,
                        "Impossible de charger la notice {} pendant l'arbitrage SOA-501",
                        ppn,
                        exception);
                result.put(ppn, CandidateMetadata.unreadable(
                        ppn, "erreur de lecture XML"));
            }
        });
        return result;
    }

    private Map<String, CandidateDecisionKey> buildKeys(
            LigneKbartDto kbart,
            Provider provider,
            Map<String, Integer> tiedCandidates,
            Map<String, CandidateMetadata> metadataByPpn) {
        Map<String, CandidateDecisionKey> keys = new LinkedHashMap<>();
        tiedCandidates.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CandidateMetadata metadata = metadataByPpn.get(entry.getKey());
                    keys.put(entry.getKey(), new CandidateDecisionKey(
                            entry.getValue(),
                            supportRank(metadata),
                            distributorRank(kbart, provider, metadata),
                            volumeRank(kbart, metadata),
                            yearRank(kbart, metadata)));
                });
        return keys;
    }

    private int supportRank(CandidateMetadata metadata) {
        return switch (metadata.accessMode()) {
            case ONLINE -> 2;
            case UNKNOWN -> 1;
            case PHYSICAL -> 0;
        };
    }

    private int distributorRank(
            LigneKbartDto kbart,
            Provider provider,
            CandidateMetadata metadata) {
        if (!isMonograph(kbart)) {
            return 1;
        }
        if (provider == null || metadata.distributors().isEmpty()) {
            return 1;
        }

        String displayName = TieBreakNormalizer.normalizeText(provider.getDisplayName());
        boolean match = metadata.distributors().stream().anyMatch(distributor ->
                TieBreakNormalizer.containsWord(distributor, provider.getProvider())
                        || (!displayName.isBlank()
                        && (distributor.equals(displayName)
                        || distributor.contains(displayName))));
        return match ? 2 : 0;
    }

    private int volumeRank(LigneKbartDto kbart, CandidateMetadata metadata) {
        if (!isMonograph(kbart)) {
            return 1;
        }

        Optional<String> expectedVolume =
                TieBreakNormalizer.normalizeVolume(kbart.getMonographVolume());
        if (expectedVolume.isEmpty()) {
            expectedVolume = TieBreakNormalizer.extractVolumeFromTitle(
                    kbart.getPublicationTitle());
        }

        if (expectedVolume.isPresent()
                && metadata.volumeNumbers().contains(expectedVolume.get())) {
            return 3;
        }

        String normalizedTitle =
                TieBreakNormalizer.normalizeText(kbart.getPublicationTitle());
        boolean partTitleMatch = metadata.partTitles().stream()
                .anyMatch(partTitle -> !partTitle.isBlank()
                        && normalizedTitle.contains(partTitle));
        if (partTitleMatch) {
            return 2;
        }

        return expectedVolume.isEmpty() ? 1 : 0;
    }

    private int yearRank(LigneKbartDto kbart, CandidateMetadata metadata) {
        if (!isMonograph(kbart)) {
            return 1;
        }

        OptionalInt onlineYear = TieBreakNormalizer.extractYear(
                kbart.getDateMonographPublishedOnline());
        OptionalInt printYear = TieBreakNormalizer.extractYear(
                kbart.getDateMonographPublishedPrint());
        if (onlineYear.isEmpty() && printYear.isEmpty()) {
            return 1;
        }

        Set<Integer> sudocYears = metadata.allYears();
        if (sudocYears.isEmpty() || metadata.hasContradictoryYears()) {
            return 1;
        }
        if (onlineYear.isPresent() && sudocYears.contains(onlineYear.getAsInt())) {
            return 3;
        }
        if (printYear.isPresent() && sudocYears.contains(printYear.getAsInt())) {
            return 2;
        }
        return 0;
    }

    private boolean isMonograph(LigneKbartDto kbart) {
        return kbart != null
                && PUBLICATION_TYPE.MONOGRAPH.name().equalsIgnoreCase(
                kbart.getPublicationType());
    }

    private String logCascadeAndFindDecisiveCriterion(
            Map<String, CandidateDecisionKey> keys) {
        List<String> survivors = new ArrayList<>(keys.keySet());
        String decisive = "AUCUN";
        decisive = decisiveAfterStep(
                survivors, keys, CandidateDecisionKey::currentScore, "SCORE");
        if (!decisive.equals("AUCUN")) {
            return decisive;
        }
        decisive = decisiveAfterStep(
                survivors, keys, CandidateDecisionKey::supportRank, "SUPPORT");
        if (!decisive.equals("AUCUN")) {
            return decisive;
        }
        decisive = decisiveAfterStep(
                survivors, keys, CandidateDecisionKey::distributorRank, "DIFFUSEUR");
        if (!decisive.equals("AUCUN")) {
            return decisive;
        }
        decisive = decisiveAfterStep(
                survivors, keys, CandidateDecisionKey::volumeRank, "VOLUME");
        if (!decisive.equals("AUCUN")) {
            return decisive;
        }
        return decisiveAfterStep(
                survivors, keys, CandidateDecisionKey::yearRank, "ANNÉE");
    }

    private String decisiveAfterStep(
            List<String> survivors,
            Map<String, CandidateDecisionKey> keys,
            ToIntFunction<CandidateDecisionKey> rank,
            String criterion) {
        int max = survivors.stream()
                .map(keys::get)
                .mapToInt(rank)
                .max()
                .orElseThrow();
        List<String> eliminated = survivors.stream()
                .filter(ppn -> rank.applyAsInt(keys.get(ppn)) < max)
                .toList();
        survivors.removeAll(eliminated);
        if (!eliminated.isEmpty()) {
            log.info(FUNCTIONAL,
                    "Arbitrage SOA-501 : critère={}, éliminés={}, restants={}",
                    criterion,
                    eliminated,
                    survivors);
        }
        return survivors.size() == 1 ? criterion : "AUCUN";
    }
}
```

- [ ] **Step 4: Run the arbitration tests**

Run:

```powershell
mvn "-Dtest=BestPpnTieBreakerServiceTest" test
```

Expected: `Tests run: 11, Failures: 0, Errors: 0` puis `BUILD SUCCESS`.

- [ ] **Step 5: Commit Task 3**

Run:

```powershell
git add src/main/java/fr/abes/bestppn/service/BestPpnTieBreakerService.java src/test/java/fr/abes/bestppn/service/BestPpnTieBreakerServiceTest.java
git -c user.name="Jerome Villiseck" -c user.email="jvk@abes.fr" commit -m "feat: arbitrer les PPN ex aequo selon SOA-501"
```

Expected: un commit contenant le moteur d’arbitrage et ses tests uniquement.

---

### Task 4: Intégration dans BestPpnService

**Files:**

- Modify: `src/main/java/fr/abes/bestppn/service/BestPpnService.java:40-54`
- Modify: `src/main/java/fr/abes/bestppn/service/BestPpnService.java:81`
- Modify: `src/main/java/fr/abes/bestppn/service/BestPpnService.java:199-247`
- Modify: `src/test/java/fr/abes/bestppn/service/BestPpnServiceTest.java:35-51`
- Modify: `src/test/java/fr/abes/bestppn/service/BestPpnServiceTest.java:358-401`
- Create: `src/test/java/fr/abes/bestppn/service/BestPpnServiceTieBreakTest.java`

**Interfaces:**

- Consumes: `BestPpnTieBreakerService.resolve(kbart, provider, ppnElecScore)`.
- Changes: `getBestPpnByScore` reçoit désormais le provider en deuxième paramètre.
- Preserves: texte d’erreur actuel, mode forcé, traitement des PPN imprimés et sélection directe d’un maximum unique.

- [ ] **Step 1: Write the failing BestPpnService integration tests**

Créer `src/test/java/fr/abes/bestppn/service/BestPpnServiceTieBreakTest.java` :

```java
package fr.abes.bestppn.service;

import fr.abes.bestppn.exception.BestPpnException;
import fr.abes.bestppn.kafka.TopicProducer;
import fr.abes.bestppn.model.BestPpn;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.tiebreak.CandidateDecisionKey;
import fr.abes.bestppn.model.tiebreak.TieBreakDecision;
import fr.abes.bestppn.utils.DESTINATION_TOPIC;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BestPpnServiceTieBreakTest {
    @Mock
    private WsService wsService;
    @Mock
    private TopicProducer topicProducer;
    @Mock
    private CheckUrlService checkUrlService;
    @Mock
    private BestPpnTieBreakerService tieBreakerService;

    private BestPpnService service;
    private LigneKbartDto kbart;

    @BeforeEach
    void setUp() {
        service = new BestPpnService(
                wsService, topicProducer, checkUrlService, tieBreakerService);
        kbart = new LigneKbartDto();
        kbart.setPublicationTitle("Titre");
        kbart.setPublicationType("monograph");
    }

    @Test
    void utiliseLePpnRetenuPourUneEgaliteMaximale() throws BestPpnException {
        Map<String, Integer> candidates = ties();
        CandidateDecisionKey selectedKey =
                new CandidateDecisionKey(10, 2, 1, 1, 1);
        when(tieBreakerService.resolve(kbart, "CAIRN", candidates))
                .thenReturn(TieBreakDecision.selected(
                        "100000001",
                        Map.of("100000001", selectedKey),
                        "SUPPORT"));

        BestPpn result = service.getBestPpnByScore(
                kbart, "CAIRN", candidates, Set.of(), false);

        assertEquals("100000001", result.getPpn());
        assertEquals(DESTINATION_TOPIC.BEST_PPN_BACON, result.getDestination());
        assertEquals(TYPE_SUPPORT.ELECTRONIQUE, result.getTypeSupport());
    }

    @Test
    void conserveLexcpetionActuelleSiLarbitrageEchoue() {
        Map<String, Integer> candidates = ties();
        when(tieBreakerService.resolve(kbart, "CAIRN", candidates))
                .thenReturn(TieBreakDecision.unresolved(
                        Map.of(), "clés identiques"));

        BestPpnException exception = assertThrows(
                BestPpnException.class,
                () -> service.getBestPpnByScore(
                        kbart, "CAIRN", candidates, Set.of(), false));

        assertTrue(exception.getMessage().startsWith(
                "Plusieurs ppn électroniques ("));
        assertTrue(exception.getMessage().contains(
                ") ont le même score."));
    }

    @Test
    void conserveLeBestPpnVideEnModeForceSiLarbitrageEchoue()
            throws BestPpnException {
        Map<String, Integer> candidates = ties();
        when(tieBreakerService.resolve(kbart, "CAIRN", candidates))
                .thenReturn(TieBreakDecision.unresolved(
                        Map.of(), "clés identiques"));

        BestPpn result = service.getBestPpnByScore(
                kbart, "CAIRN", candidates, Set.of(), true);

        assertEquals("", result.getPpn());
        assertEquals(DESTINATION_TOPIC.BEST_PPN_BACON, result.getDestination());
    }

    @Test
    void nappellePasLarbitragePourUnMaximumUnique()
            throws BestPpnException {
        Map<String, Integer> candidates = new LinkedHashMap<>();
        candidates.put("100000001", 10);
        candidates.put("100000002", 5);

        BestPpn result = service.getBestPpnByScore(
                kbart, "CAIRN", candidates, Set.of(), false);

        assertEquals("100000001", result.getPpn());
        verifyNoInteractions(tieBreakerService);
    }

    private static Map<String, Integer> ties() {
        Map<String, Integer> candidates = new LinkedHashMap<>();
        candidates.put("100000001", 10);
        candidates.put("100000002", 10);
        return candidates;
    }
}
```

- [ ] **Step 2: Run the integration test to verify it fails**

Run:

```powershell
mvn "-Dtest=BestPpnServiceTieBreakTest" test
```

Expected: `FAIL` pendant `testCompile`, car le constructeur et `getBestPpnByScore` n’ont pas encore leur nouvelle signature.

- [ ] **Step 3: Wire the tie-breaker into BestPpnService**

Dans `src/main/java/fr/abes/bestppn/service/BestPpnService.java`, ajouter l’import :

```java
import fr.abes.bestppn.model.tiebreak.TieBreakDecision;
```

Remplacer le champ `NoticeService` et le constructeur par :

```java
    private final BestPpnTieBreakerService tieBreakerService;

    private final TopicProducer topicProducer;

    private final CheckUrlService checkUrlService;

    public BestPpnService(
            WsService service,
            TopicProducer topicProducer,
            CheckUrlService checkUrlService,
            BestPpnTieBreakerService tieBreakerService) {
        this.service = service;
        this.topicProducer = topicProducer;
        this.checkUrlService = checkUrlService;
        this.tieBreakerService = tieBreakerService;
    }
```

Remplacer l’appel final de `getBestPpn` par :

```java
        return getBestPpnByScore(
                kbart,
                provider,
                ppnElecScoredList,
                ppnPrintResultList,
                isForced);
```

Remplacer entièrement `getBestPpnByScore` par :

```java
    public BestPpn getBestPpnByScore(
            LigneKbartDto kbart,
            String provider,
            Map<String, Integer> ppnElecResultList,
            Set<String> ppnPrintResultList,
            boolean isForced) throws BestPpnException {
        Map<String, Integer> ppnElecScore =
                Utils.getMaxValuesFromMap(ppnElecResultList);
        return switch (ppnElecScore.size()) {
            case 0 -> {
                log.info(FUNCTIONAL, "Aucun ppn électronique trouvé. " + kbart);
                yield switch (ppnPrintResultList.size()) {
                    case 0 -> {
                        kbart.setErrorType("Aucun ppn trouvé");
                        yield new BestPpn(
                                null, DESTINATION_TOPIC.NO_PPN_FOUND_SUDOC);
                    }
                    case 1 -> {
                        String printPpn =
                                ppnPrintResultList.stream().toList().getFirst();
                        kbart.setErrorType(
                                "Ppn imprimé trouvé : " + printPpn);
                        log.debug(TECHNICAL, kbart.getErrorType());
                        yield new BestPpn(
                                printPpn,
                                DESTINATION_TOPIC.PRINT_PPN_SUDOC,
                                TYPE_SUPPORT.IMPRIME);
                    }
                    default -> unresolvedPrintTie(
                            kbart, ppnPrintResultList, isForced);
                };
            }
            case 1 -> new BestPpn(
                    ppnElecScore.keySet().stream().findFirst().orElseThrow(),
                    DESTINATION_TOPIC.BEST_PPN_BACON,
                    TYPE_SUPPORT.ELECTRONIQUE);
            default -> {
                TieBreakDecision decision =
                        tieBreakerService.resolve(kbart, provider, ppnElecScore);
                if (decision.resolved()) {
                    yield new BestPpn(
                            decision.selectedPpn(),
                            DESTINATION_TOPIC.BEST_PPN_BACON,
                            TYPE_SUPPORT.ELECTRONIQUE);
                }
                yield unresolvedElectronicTie(
                        kbart, ppnElecScore, isForced);
            }
        };
    }

    private BestPpn unresolvedPrintTie(
            LigneKbartDto kbart,
            Set<String> ppnPrintResultList,
            boolean isForced) throws BestPpnException {
        String errorString = "Plusieurs ppn imprimés ("
                + String.join(" OU ", ppnPrintResultList)
                + ") ont été trouvés.";
        kbart.setErrorType(errorString);
        if (isForced) {
            log.error(FUNCTIONAL, errorString + " [ " + kbart + " ]");
            return new BestPpn("", DESTINATION_TOPIC.BEST_PPN_BACON);
        }
        throw new BestPpnException(errorString + " [ " + kbart + " ] ");
    }

    private BestPpn unresolvedElectronicTie(
            LigneKbartDto kbart,
            Map<String, Integer> ppnElecScore,
            boolean isForced) throws BestPpnException {
        String listPpn = String.join(" OU ", ppnElecScore.keySet());
        String errorString = "Plusieurs ppn électroniques ("
                + listPpn
                + ") ont le même score.";
        kbart.setErrorType(errorString);
        if (isForced) {
            log.error(FUNCTIONAL, errorString + " [ " + kbart + " ]");
            return new BestPpn("", DESTINATION_TOPIC.BEST_PPN_BACON);
        }
        throw new BestPpnException(errorString + " [ " + kbart + " ]");
    }
```

- [ ] **Step 4: Adapt the existing Spring test fixture**

Dans `src/test/java/fr/abes/bestppn/service/BestPpnServiceTest.java`, ajouter :

```java
import fr.abes.bestppn.model.tiebreak.TieBreakDecision;
```

Ajouter le mock suivant aux autres `@MockitoBean` :

```java
    @MockitoBean
    BestPpnTieBreakerService tieBreakerService;
```

À la fin de `init()`, ajouter le comportement indécidable par défaut :

```java
        Mockito.when(tieBreakerService.resolve(
                        Mockito.any(),
                        Mockito.anyString(),
                        Mockito.anyMap()))
                .thenReturn(TieBreakDecision.unresolved(
                        Map.of(), "clés identiques"));
```

Remplacer les quatre appels directs existants :

```java
bestPpnService.getBestPpnByScore(
        kbart, "", ppnElecResultList, ppnPrintResultList, false);
```

```java
bestPpnService.getBestPpnByScore(
        kbart, "", ppnElecResultList, ppnPrintResultList, false);
```

```java
bestPpnService.getBestPpnByScore(
        kbart, "", ppnElecResultList, ppnPrintResultList, true);
```

```java
bestPpnService.getBestPpnByScore(
        kbart, "", ppnElecResultList, ppnPrintResultList, true);
```

- [ ] **Step 5: Run the focused BestPpnService tests**

Run:

```powershell
mvn "-Dtest=BestPpnServiceTieBreakTest" test
```

Expected: `Tests run: 4, Failures: 0, Errors: 0` puis `BUILD SUCCESS`.

Run:

```powershell
mvn "-Dtest=BestPpnServiceTest" test
```

Expected: `BUILD SUCCESS`. Vérifier dans `target/surefire-reports/fr.abes.bestppn.service.BestPpnServiceTest.txt` que `Tests run` est strictement supérieur à zéro ; si la configuration Log4j/Kafka du dépôt empêche encore l’exécution de cette classe Spring, consigner ce défaut de socle sans modifier le périmètre SOA-501.

- [ ] **Step 6: Commit Task 4**

Run:

```powershell
git add src/main/java/fr/abes/bestppn/service/BestPpnService.java src/test/java/fr/abes/bestppn/service/BestPpnServiceTest.java src/test/java/fr/abes/bestppn/service/BestPpnServiceTieBreakTest.java
git -c user.name="Jerome Villiseck" -c user.email="jvk@abes.fr" commit -m "feat: intégrer le départage dans bestppn"
```

Expected: un commit contenant uniquement l’intégration et ses tests.

---

### Task 5: Non-régression du flux XML complet

**Files:**

- Create: `src/test/java/fr/abes/bestppn/service/BestPpnTieBreakerFlowTest.java`

**Interfaces:**

- Consumes: implémentation réelle de `CandidateMetadataExtractor` et `BestPpnTieBreakerService`.
- Mocks: accès Oracle de `NoticeService` et accès BACON de `ProviderService`.
- Proves: les quatre critères SOA-501 fonctionnent depuis les zones XML jusqu’au PPN sélectionné.

- [ ] **Step 1: Write the end-to-end XML flow tests**

Créer `src/test/java/fr/abes/bestppn/service/BestPpnTieBreakerFlowTest.java` :

```java
package fr.abes.bestppn.service;

import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.model.entity.bacon.Provider;
import fr.abes.bestppn.model.entity.basexml.notice.Controlfield;
import fr.abes.bestppn.model.entity.basexml.notice.Datafield;
import fr.abes.bestppn.model.entity.basexml.notice.NoticeXml;
import fr.abes.bestppn.model.entity.basexml.notice.SubField;
import fr.abes.bestppn.model.tiebreak.TieBreakDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BestPpnTieBreakerFlowTest {
    @Mock
    private NoticeService noticeService;
    @Mock
    private ProviderService providerService;

    private BestPpnTieBreakerService service;

    @BeforeEach
    void setUp() {
        service = new BestPpnTieBreakerService(
                noticeService,
                providerService,
                new CandidateMetadataExtractor());
    }

    @Test
    void supportEnLigneGagneContreCdRom() throws IOException {
        LigneKbartDto kbart = kbart("serial");
        notices(
                notice("100000001", "Ressource en ligne", null, null, null, null),
                notice("100000002", "CD-ROM", null, null, null, null));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertTrue(decision.resolved());
        assertEquals("100000001", decision.selectedPpn());
        assertEquals("SUPPORT", decision.reason());
    }

    @Test
    void casReel164581340EstClasseCommeCdRom() throws IOException {
        LigneKbartDto kbart = kbart("serial");
        NoticeXml physical = notice(
                "164581340", "( CD-ROM)", null, null, null, null);
        physical.getControlfields().get(1).setValue("Obx3");
        physical.getDatafields().add(
                field("531", "#", "b", "(CD-ROM)"));
        when(noticeService.getNoticeByPpn("100000001")).thenReturn(
                notice("100000001", "Ressource en ligne", null, null, null, null));
        when(noticeService.getNoticeByPpn("164581340")).thenReturn(physical);
        Map<String, Integer> candidates = new LinkedHashMap<>();
        candidates.put("100000001", 10);
        candidates.put("164581340", 10);

        TieBreakDecision decision = service.resolve(kbart, "", candidates);

        assertEquals("100000001", decision.selectedPpn());
        assertEquals(0, decision.keys().get("164581340").supportRank());
    }

    @Test
    void diffuseur214Indicateur2DepartageLesNotices() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        Provider provider = new Provider("CAIRN");
        provider.setDisplayName("Cairn.info");
        when(providerService.findByProvider("CAIRN"))
                .thenReturn(Optional.of(provider));
        notices(
                notice("100000001", "Ressource en ligne", "Cairn.info", null, null, null),
                notice("100000002", "Ressource en ligne", "Autre diffuseur", null, null, null));

        TieBreakDecision decision = service.resolve(kbart, "CAIRN", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals("DIFFUSEUR", decision.reason());
    }

    @Test
    void volume200hDepartageLesNotices() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setMonographVolume("Part II");
        notices(
                notice("100000001", "Ressource en ligne", null, "Tome 2", null, null),
                notice("100000002", "Ressource en ligne", null, "Tome 3", null, null));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals("VOLUME", decision.reason());
    }

    @Test
    void titreDePartie200iDepartageLesNotices() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setPublicationTitle("Traité complet - Méthodes numériques");
        notices(
                notice("100000001", "Ressource en ligne", null, null, "Méthodes numériques", null),
                notice("100000002", "Ressource en ligne", null, null, "Autre partie", null));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals("VOLUME", decision.reason());
    }

    @Test
    void annee100Et214DepartageLesNotices() throws IOException {
        LigneKbartDto kbart = kbart("monograph");
        kbart.setDateMonographPublishedOnline("2021-01-01");
        kbart.setDateMonographPublishedPrint("2020");
        notices(
                notice("100000001", "Ressource en ligne", null, null, null, 2021),
                notice("100000002", "Ressource en ligne", null, null, null, 2020));

        TieBreakDecision decision = service.resolve(kbart, "", ties());

        assertEquals("100000001", decision.selectedPpn());
        assertEquals("ANNÉE", decision.reason());
    }

    private void notices(NoticeXml first, NoticeXml second) throws IOException {
        when(noticeService.getNoticeByPpn("100000001")).thenReturn(first);
        when(noticeService.getNoticeByPpn("100000002")).thenReturn(second);
    }

    private static LigneKbartDto kbart(String publicationType) {
        LigneKbartDto kbart = new LigneKbartDto();
        kbart.setPublicationType(publicationType);
        kbart.setPublicationTitle("Titre");
        return kbart;
    }

    private static Map<String, Integer> ties() {
        Map<String, Integer> candidates = new LinkedHashMap<>();
        candidates.put("100000001", 10);
        candidates.put("100000002", 10);
        return candidates;
    }

    private static NoticeXml notice(
            String ppn,
            String support,
            String distributor,
            String volume,
            String partTitle,
            Integer year) {
        NoticeXml notice = new NoticeXml();
        notice.setLeader("     nam0 22        450 ");
        notice.setControlfields(List.of(
                controlfield("001", ppn),
                controlfield("008", "Oax3")));

        List<Datafield> fields = new ArrayList<>();
        fields.add(field("530", " ", "b", support));
        if (distributor != null) {
            fields.add(field("214", "2", "c", distributor));
        }
        if (volume != null || partTitle != null) {
            List<String> titleValues = new ArrayList<>();
            if (volume != null) {
                titleValues.add("h");
                titleValues.add(volume);
            }
            if (partTitle != null) {
                titleValues.add("i");
                titleValues.add(partTitle);
            }
            fields.add(field(
                    "200", " ", titleValues.toArray(String[]::new)));
        }
        if (year != null) {
            fields.add(field(
                    "100",
                    " ",
                    "a",
                    "20240723d" + year + "    m  y0frey50      ba"));
            fields.add(field("214", "0", "d", "[" + year + "]"));
        }
        notice.setDatafields(fields);
        return notice;
    }

    private static Controlfield controlfield(String tag, String value) {
        Controlfield field = new Controlfield();
        field.setTag(tag);
        field.setValue(value);
        return field;
    }

    private static Datafield field(
            String tag,
            String ind2,
            String... codeValues) {
        Datafield field = new Datafield();
        field.setTag(tag);
        field.setInd1(" ");
        field.setInd2(ind2);
        List<SubField> subFields = new ArrayList<>();
        for (int index = 0; index < codeValues.length; index += 2) {
            if (codeValues[index + 1] != null) {
                SubField subField = new SubField();
                subField.setCode(codeValues[index]);
                subField.setValue(codeValues[index + 1]);
                subFields.add(subField);
            }
        }
        field.setSubFields(subFields);
        return field;
    }
}
```

- [ ] **Step 2: Run the complete-flow tests**

Run:

```powershell
mvn "-Dtest=BestPpnTieBreakerFlowTest" test
```

Expected: `Tests run: 6, Failures: 0, Errors: 0` puis `BUILD SUCCESS`. Le test `casReel164581340EstClasseCommeCdRom` reprend les valeurs `008=Obx3`, `530$b=( CD-ROM)` et `531$b=(CD-ROM)` lues dans la base APISUDOC de test.

- [ ] **Step 3: Run every new SOA-501 test**

Run:

```powershell
mvn "-Dtest=TieBreakNormalizerTest,CandidateDecisionKeyTest,CandidateMetadataExtractorTest,BestPpnTieBreakerServiceTest,BestPpnServiceTieBreakTest,BestPpnTieBreakerFlowTest" test
```

Expected: `Tests run: 39, Failures: 0, Errors: 0` puis `BUILD SUCCESS`.

- [ ] **Step 4: Run the complete project test suite**

Run:

```powershell
mvn test
```

Expected: `BUILD SUCCESS`. Contrôler dans `target/surefire-reports` que chacune des six nouvelles classes affiche un nombre de tests strictement supérieur à zéro, sans échec ni erreur. Le défaut de socle déjà observé sur les anciennes classes Spring avec la configuration Kafka/Log4j doit être signalé séparément s’il persiste.

- [ ] **Step 5: Verify diff hygiene**

Run:

```powershell
git diff --check
git status --short
git diff -- src/main/java/fr/abes/LigneKbartConnect.java src/main/java/fr/abes/LigneKbartImprime.java
```

Expected:

- aucune erreur de whitespace ;
- seuls les fichiers explicitement prévus par le plan sont ajoutés ou modifiés ;
- les deux fichiers Avro éventuellement régénérés restent hors index ;
- aucun fichier de `sudoc-api` n’est modifié.

- [ ] **Step 6: Commit Task 5**

Run:

```powershell
git add src/test/java/fr/abes/bestppn/service/BestPpnTieBreakerFlowTest.java
git -c user.name="Jerome Villiseck" -c user.email="jvk@abes.fr" commit -m "test: couvrir le flux complet de départage SOA-501"
```

Expected: un commit contenant uniquement le test de non-régression XML.

- [ ] **Step 7: Record final branch state**

Run:

```powershell
git log --oneline --decorate origin/develop..HEAD
git status --short
```

Expected: le commit de conception, le commit du présent plan et les cinq commits d’implémentation apparaissent sur `SOA-501-affiner-departage-bestppn`. Les seuls changements non commités admis sont les deux sources Avro régénérées déjà identifiées.
