package fr.abes.bestppn.utils;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fournit les normalisations communes aux critères de départage SOA-501.
 *
 * <p>Cette classe sans état transforme les valeurs KBART et UNIMARC en formes
 * comparables. Elle ne décide jamais quel PPN doit être retenu.</p>
 */
public final class TieBreakNormalizer {
    /**
     * Identifie les signes diacritiques produits par la décomposition Unicode.
     */
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    /**
     * Identifie toute ponctuation à remplacer par un séparateur.
     */
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

    /**
     * Identifie les suites d'espaces à réduire à un espace unique.
     */
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /**
     * Identifie une année comprise entre 1800 et 2199 sans l'extraire d'un
     * nombre plus long.
     */
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(?:18|19|20|21)\\d{2}(?!\\d)");

    /**
     * Identifie une désignation de volume isolée, avec ou sans préfixe usuel.
     */
    private static final Pattern VOLUME = Pattern.compile(
            "^(?:(?:tome|volume|vol|partie|part|band)\\s*)?([0-9]+|[ivxlcdm]+)(?:\\s.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Identifie dans un titre une désignation de volume précédée d'un préfixe
     * explicite.
     */
    private static final Pattern VOLUME_IN_TITLE = Pattern.compile(
            "(?:^|\\s)(?:tome|volume|vol|partie|part|band)\\s+([0-9]+|[ivxlcdm]+)(?:\\s|$)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Limite la conversion romaine aux symboles autorisés.
     */
    private static final Pattern VALID_ROMAN = Pattern.compile("[IVXLCDM]+");

    /**
     * Empêche l'instanciation d'une classe composée uniquement de méthodes
     * statiques.
     */
    private TieBreakNormalizer() {
    }

    /**
     * Produit une forme textuelle comparable.
     *
     * <p>La méthode passe le texte en minuscules, retire les diacritiques,
     * remplace la ponctuation par des espaces et réduit les espaces multiples.
     * Les lettres et les chiffres sont conservés. Une valeur nulle produit une
     * chaîne vide.</p>
     *
     * @param value texte KBART ou UNIMARC
     * @return texte normalisé, jamais nul
     */
    public static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(decomposed).replaceAll("");
        String withoutPunctuation = NON_ALPHANUMERIC.matcher(withoutDiacritics).replaceAll(" ");
        return SPACES.matcher(withoutPunctuation.toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
    }

    /**
     * Extrait la première année explicite d'une valeur libre.
     *
     * @param value date ou mention de publication
     * @return année trouvée, ou résultat vide si aucune année valide n'existe
     */
    public static OptionalInt extractYear(String value) {
        Matcher matcher = YEAR.matcher(value == null ? "" : value);
        return matcher.find() ? OptionalInt.of(Integer.parseInt(matcher.group())) : OptionalInt.empty();
    }

    /**
     * Extrait la date 1 de la sous-zone UNIMARC {@code 100$a}.
     *
     * <p>La date de saisie occupe les huit premiers caractères, suivie du code
     * de type de date. La date 1 est donc lue aux index 9 à 12 inclus. Une
     * valeur trop courte ou une année hors de l'intervalle 1800-2199 produit
     * un résultat vide.</p>
     *
     * @param value contenu brut de {@code 100$a}
     * @return date 1 sous forme d'année
     */
    public static OptionalInt extractDate1From100(String value) {
        if (value == null || value.length() < 13) {
            return OptionalInt.empty();
        }
        String date1 = value.substring(9, 13);
        return date1.matches("(?:18|19|20|21)\\d{2}")
                ? OptionalInt.of(Integer.parseInt(date1))
                : OptionalInt.empty();
    }

    /**
     * Extrait toutes les années distinctes d'une valeur libre.
     *
     * @param value mention de publication pouvant contenir plusieurs années
     * @return ensemble des années trouvées
     */
    public static Set<Integer> extractYears(String value) {
        Set<Integer> years = new LinkedHashSet<>();
        Matcher matcher = YEAR.matcher(value == null ? "" : value);
        while (matcher.find()) {
            years.add(Integer.parseInt(matcher.group()));
        }
        return Set.copyOf(years);
    }

    /**
     * Normalise une désignation de volume.
     *
     * <p>Les préfixes {@code tome}, {@code volume}, {@code vol},
     * {@code partie}, {@code part} et {@code band} sont ignorés. Les zéros de
     * tête sont supprimés et un nombre romain canonique est converti en nombre
     * arabe. Une désignation non reconnue produit un résultat vide.</p>
     *
     * @param value désignation brute du volume
     * @return clé numérique normalisée sous forme de chaîne
     */
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

    /**
     * Recherche une désignation explicite de volume dans un titre.
     *
     * <p>Un préfixe de volume est obligatoire afin de ne pas interpréter un
     * nombre appartenant normalement au titre comme un numéro de partie.</p>
     *
     * @param title titre de publication KBART
     * @return volume normalisé trouvé dans le titre
     */
    public static Optional<String> extractVolumeFromTitle(String title) {
        Matcher matcher = VOLUME_IN_TITLE.matcher(normalizeText(title));
        return matcher.find() ? normalizeVolume(matcher.group(1)) : Optional.empty();
    }

    /**
     * Vérifie la présence d'un code comme mot ou expression complète.
     *
     * <p>Les deux valeurs sont normalisées avant la comparaison. Les bornes
     * évitent notamment qu'un code provider court comme {@code cairn}
     * corresponde à {@code cairninfo}.</p>
     *
     * @param text texte dans lequel effectuer la recherche
     * @param word code ou expression recherchée
     * @return {@code true} si la valeur recherchée est délimitée par le début,
     * la fin ou des espaces
     */
    public static boolean containsWord(String text, String word) {
        String normalizedText = normalizeText(text);
        String normalizedWord = normalizeText(word);
        if (normalizedWord.isBlank()) {
            return false;
        }
        Pattern completeWord = Pattern.compile("(^|\\s)" + Pattern.quote(normalizedWord) + "($|\\s)");
        return completeWord.matcher(normalizedText).find();
    }

    /**
     * Convertit un nombre romain canonique en nombre arabe.
     *
     * <p>La reconversion du résultat vers sa forme romaine permet de refuser
     * les écritures non canoniques telles que {@code IIII}.</p>
     *
     * @param value nombre romain à convertir
     * @return valeur arabe, ou résultat vide si l'écriture n'est pas canonique
     */
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

    /**
     * Retourne la valeur élémentaire d'un symbole romain.
     *
     * @param value symbole romain
     * @return valeur du symbole, ou 0 pour un caractère inconnu
     */
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

    /**
     * Produit la représentation romaine canonique d'un entier positif.
     *
     * @param value valeur arabe à convertir
     * @return représentation romaine canonique
     */
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
