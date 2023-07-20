package fr.abes.bestppn.utils;

import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import fr.abes.bestppn.exception.IllegalProviderException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        String doiPattern = "10.\\d{0,15}.\\d{0,15}.+";

        if (kbart.getTitle_url() != null && !kbart.getTitle_url().isEmpty()){
            Pattern pattern = Pattern.compile(doiPattern);
            Matcher matcher = pattern.matcher(kbart.getTitle_url());
            if (matcher.find()) {
                return matcher.group(0);
            } else {
                return "";
            }
        }
        if (kbart.getTitle_id() != null && !kbart.getTitle_id().isEmpty()){
            Pattern pattern = Pattern.compile(doiPattern);
            Matcher matcher = pattern.matcher(kbart.getTitle_id());
            if (matcher.find()) {
                return matcher.group(0);
            } else {
                return "";
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
}
