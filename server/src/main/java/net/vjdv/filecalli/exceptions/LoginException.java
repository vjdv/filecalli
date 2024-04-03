package net.vjdv.filecalli.exceptions;

/**
 * General errors that should not happen
 */
public class LoginException extends RuntimeException {

    public LoginException(String message) {
        super(message);
    }

    public LoginException(String message, Throwable cause) {
        super(message, cause);
    }

}
