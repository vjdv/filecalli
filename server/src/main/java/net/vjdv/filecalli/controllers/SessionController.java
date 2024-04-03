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
        log.info("loggin attempt for user {}", login.userId());
        try {
            String uid = sessionService.login(login.userId(), login.pass());
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
