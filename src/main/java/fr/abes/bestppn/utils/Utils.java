package fr.abes.bestppn.utils;

import fr.abes.bestppn.exception.IllegalDateException;
import fr.abes.bestppn.exception.IllegalPackageException;
import fr.abes.bestppn.exception.IllegalProviderException;
import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class Utils {

    public static String extractDomainFromUrl(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String host = uri.getHost();
        if (host == null) {
            throw new URISyntaxException(url, "Format d'URL incorrect");
        }
        return host;
    }

    public static String extractDOI(LigneKbartDto kbart) {
        String doiPattern = "10\\.\\d{0,15}.\\d{0,15}.+";

        if (kbart.getTitleUrl() != null && !kbart.getTitleUrl().isEmpty()){
            Pattern pattern = Pattern.compile(doiPattern);
            Matcher matcher = pattern.matcher(kbart.getTitleUrl());
            if (matcher.find()) {
                return matcher.group(0);
            }
        }
        if (kbart.getTitleId() != null && !kbart.getTitleId().isEmpty()){
            Pattern pattern = Pattern.compile(doiPattern);
            Matcher matcher = pattern.matcher(kbart.getTitleId());
            if (matcher.find()) {
                return matcher.group(0);
            }
        }
        return "";
    }

    public static <K, V extends Comparable<? super V>> Map<K, V> getMaxValuesFromMap(Map<K, V> map) {
        Map<K, V> maxKeys = new HashMap<>();
        if (!map.isEmpty()) {
            V maxValue = Collections.max(map.values());
            for (Map.Entry<K, V> entry : map.entrySet()) {
                if (entry.getValue().equals(maxValue)) {
                    maxKeys.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return maxKeys;
    }

    public static String extractProvider(String filename) throws IllegalProviderException {
        try {
            return filename.substring(0, filename.indexOf('_'));
        } catch (Exception e) {
            throw new IllegalProviderException(e);
        }
    }

    public static String extractPackageName(String filename) throws IllegalPackageException {
        try {
            if (filename.contains("_FORCE")) {
                String tempsStr =  filename.substring(0, filename.indexOf("_FORCE"));
                return tempsStr.substring(tempsStr.indexOf('_') + 1, tempsStr.lastIndexOf('_'));
            } else if (filename.contains("_BYPASS")) {
                String tempStr = filename.substring(0, filename.indexOf("_BYPASS"));
                return tempStr.substring(tempStr.indexOf('_') + 1, tempStr.lastIndexOf('_'));
            } else {
                return filename.substring(filename.indexOf('_') + 1, filename.lastIndexOf('_'));
            }
        } catch (Exception e) {
            throw new IllegalPackageException(e);
        }
    }

    public static Date extractDate(String filename) throws IllegalDateException {
        Date date = new Date();
        try {
            Matcher matcher = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})", Pattern.CASE_INSENSITIVE).matcher(filename);
            if(matcher.find()){
                date = new SimpleDateFormat("yyyy-MM-dd").parse(matcher.group(1));
            }
            return date;
        } catch (Exception e) {
            throw new IllegalDateException(e);
        }
    }

    public static LocalDate convertDateToLocalDate(Date dateToConvert) {
        return dateToConvert.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * Permet de formatter une date en entrée dans un fichier kbart et de renvoyer la date prête à être envoyée
     * @param date : date à formatter
     * @param debut : indique s'il s'agit d'une date de début : true : date de début, false : date de fin
     * @return la date formattée
     */
    public static String formatDate(String date, boolean debut) {
        if (date == null) return null;
        String yearRegExp = "([\\d]{4})";
        String dateRegExp = "\\d{4}-\\d{2}-\\d{2}";
        int day = (debut) ? 1 : 31;
        int month = (debut) ? Calendar.JANUARY : Calendar.DECEMBER;
        if (date.matches(yearRegExp)) {
            return new GregorianCalendar(Integer.parseInt(date), month, day).toZonedDateTime().toLocalDate().toString();
        }
        if (date.matches(dateRegExp)) {
            return date;
        }
        return null;
    }
}
