package fr.abes.bestppn.utils;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public final class LogMarkers {
    public static final Marker FUNCTIONAL = MarkerFactory.getMarker("FUNCTIONAL");
    public static final Marker TECHNICAL = MarkerFactory.getMarker("TECHNICAL");
    private LogMarkers() {}
}
