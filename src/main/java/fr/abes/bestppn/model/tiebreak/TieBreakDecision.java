package fr.abes.bestppn.model.tiebreak;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Décrit le résultat complet d'une tentative de départage.
 *
 * <p>Une décision résolue contient un PPN sélectionné et le nom du critère
 * décisif. Une décision indécidable conserve un PPN nul et explique pourquoi
 * l'erreur d'égalité existante doit être maintenue.</p>
 *
 * @param selectedPpn PPN retenu, ou {@code null} lorsque l'arbitrage échoue
 * @param keys clés calculées pour chaque candidat, utilisées dans les logs et
 * les tests de diagnostic
 * @param reason critère décisif pour une sélection, ou motif de l'absence de
 * décision
 */
public record TieBreakDecision(
        String selectedPpn,
        Map<String, CandidateDecisionKey> keys,
        String reason) {

    /**
     * Protège la table des clés contre les modifications après construction.
     *
     * <p>Une table nulle est remplacée par une table vide. Une
     * {@link LinkedHashMap} conserve l'ordre déterministe des candidats.</p>
     */
    public TieBreakDecision {
        keys = keys == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(keys));
    }

    /**
     * Fabrique une décision résolue.
     *
     * @param ppn PPN sélectionné
     * @param keys clés de tous les candidats comparés
     * @param decisiveCriterion premier critère ayant rendu le candidat unique
     * @return décision contenant le PPN retenu
     */
    public static TieBreakDecision selected(
            String ppn,
            Map<String, CandidateDecisionKey> keys,
            String decisiveCriterion) {
        return new TieBreakDecision(ppn, keys, decisiveCriterion);
    }

    /**
     * Fabrique une décision indécidable.
     *
     * @param keys clés calculées avant de constater l'égalité ou le veto
     * @param reason motif expliquant l'absence de sélection automatique
     * @return décision sans PPN sélectionné
     */
    public static TieBreakDecision unresolved(
            Map<String, CandidateDecisionKey> keys,
            String reason) {
        return new TieBreakDecision(null, keys, reason);
    }

    /**
     * Indique si l'arbitrage a produit un PPN exploitable.
     *
     * @return {@code true} lorsque {@link #selectedPpn()} n'est pas nul
     */
    public boolean resolved() {
        return selectedPpn != null;
    }
}
