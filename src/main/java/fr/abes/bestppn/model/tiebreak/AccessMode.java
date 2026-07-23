package fr.abes.bestppn.model.tiebreak;

/**
 * Décrit le mode d'accès déduit des mentions explicites d'une notice Sudoc.
 *
 * <p>Cette information alimente le premier critère de départage de SOA-501.
 * Elle ne reprend pas directement le type électronique de la zone 008, car
 * cette zone peut également désigner un CD-ROM ou un DVD-ROM.</p>
 */
public enum AccessMode {
    /**
     * La notice contient une mention explicite d'accès en ligne.
     */
    ONLINE,

    /**
     * La notice décrit explicitement un support matériel, par exemple un
     * CD-ROM, un DVD-ROM ou un disque optique.
     */
    PHYSICAL,

    /**
     * Le support est absent, générique, hybride ou contradictoire.
     */
    UNKNOWN
}
