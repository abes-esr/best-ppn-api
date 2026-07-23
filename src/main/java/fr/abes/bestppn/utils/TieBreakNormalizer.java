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
