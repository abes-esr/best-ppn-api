package fr.abes.bestppn.exception;

/**
 * Exception levée en cas de PPN Null lors d'une recherche dans la base XML
 */
public class IllegalPpnException extends Exception {
    public IllegalPpnException(String s) {
        super(s);
    }
}
