package net.vjdv.filecalli.controllers;

import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.exceptions.LoginException;
import net.vjdv.filecalli.services.SessionService;
import net.vjdv.filecalli.services.StorageService;
import net.vjdv.filecalli.util.Constants;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@Controller
@RequestMapping("/web")
public class WebController {
    private final SessionService sessionService;
    private final StorageService storageService;

    public WebController(SessionService sessionService, StorageService storageService) {
        this.sessionService = sessionService;
        this.storageService = storageService;
    }

    @GetMapping("/")
    public ModelAndView explorer(@CookieValue(value = Constants.COOKIE_NAME, required = false) String idSession,
                                 @RequestParam(defaultValue = "/") String path) {
        long start = System.currentTimeMillis();
        var session = sessionService.getSession(idSession);
        var mav = new ModelAndView("explorer");
        var list = storageService.list(path, session.rootDir());
        mav.addObject("path", path);
        mav.addObject("files", list);
        log.info("explorer() path={} {}ms", path, System.currentTimeMillis() - start);
        return mav;
    }

    @ExceptionHandler(LoginException.class)
    public ModelAndView handleLoginException() {
        return new ModelAndView("login");
    }

}
