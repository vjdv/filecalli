package net.vjdv.filecalli.exceptions;

/**
 * Access db errors
 */
public class DataException extends RuntimeException {

    public DataException(String message) {
        super(message);
    }

    public DataException(String message, Throwable cause) {
        super(message, cause);
    }

}
