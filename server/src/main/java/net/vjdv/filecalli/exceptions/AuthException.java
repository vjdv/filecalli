package net.vjdv.filecalli.exceptions;

import org.springframework.http.ResponseEntity;

/**
 * General errors that should not happen
 */
public class AuthException extends RuntimeException {

    private final int statusCode;

    public AuthException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public ResponseEntity<String> getResponseEntity() {
        if (statusCode == 401) {
            return ResponseEntity.status(401).header("WWW-Authenticate", "Basic realm=\"FileCalli\"").body(getMessage());
        } else {
            return ResponseEntity.status(403).body(getMessage());
        }
    }

}
