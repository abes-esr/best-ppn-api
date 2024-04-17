package fr.abes.bestppn.exception;

public class IllegalProviderException extends Exception {
    public IllegalProviderException(Exception e) {
        super(e);
    }

    public IllegalProviderException(String message) {
        super(message);
    }
}
