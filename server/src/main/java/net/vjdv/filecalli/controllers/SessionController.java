package net.vjdv.filecalli.controllers;

import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.dto.LoginDTO;
import net.vjdv.filecalli.dto.Result;
import net.vjdv.filecalli.dto.SessionDTO2;
import net.vjdv.filecalli.exceptions.LoginException;
import net.vjdv.filecalli.services.SessionService;
import net.vjdv.filecalli.util.Constants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/session")
@RestController
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/")
    public ResponseEntity<SessionDTO2> getSession(@CookieValue(name = Constants.COOKIE_NAME, required = false) String uid) {
        try {
            var session = new SessionDTO2(sessionService.getSession(uid));
            return ResponseEntity.ok(session);
        } catch (LoginException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Result> login(@RequestBody LoginDTO login) {
        log.info("login attempt for user {}", login.userId());
        long duration = switch (login.duration()) {
            case "5m" -> 5 * 60 * 1000;
            case "15m" -> 15 * 60 * 1000;
            case "1h" -> 60 * 60 * 1000;
            case "1d" -> 24 * 60 * 60 * 1000;
            case "5d" -> 5 * 24 * 60 * 60 * 1000;
            case "1w" -> 7 * 24 * 60 * 60 * 1000;
            default -> 0;
        };
        if (duration == 0) {
            return ResponseEntity.badRequest().body(Result.failure("Invalid duration"));
        }
        try {
            String uid = sessionService.login(login.userId(), login.pass(), duration);
            var cookie = ResponseCookie.from(Constants.COOKIE_NAME, uid).path("/").httpOnly(true).build();
            return ResponseEntity
                    .ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(Result.success("Welcome"));
        } catch (LoginException ex) {
            return ResponseEntity.ok(Result.failure(ex.getMessage()));
        }
    }

}
