package net.vjdv.filecalli.controllers;

import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.exceptions.LoginException;
import net.vjdv.filecalli.services.SessionService;
import net.vjdv.filecalli.services.StorageService;
import net.vjdv.filecalli.util.Configuration;
import net.vjdv.filecalli.util.Constants;
import net.vjdv.filecalli.util.Utils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

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

    @PostMapping("/upload")
    public ModelAndView upload(@CookieValue(Constants.COOKIE_NAME) String idSession,
                               @RequestParam("file") MultipartFile file,
                               @RequestParam("path") String path) {
        long start = System.currentTimeMillis();
        var session = sessionService.getSession(idSession);
        var mav = new ModelAndView("upload");
        //variables
        var name = file.getOriginalFilename();
        if (name == null || name.isBlank()) name = "noname";
        name = Utils.cleanFileName(name);
        var id = UUID.randomUUID().toString();
        var size = file.getSize();
        var resolvedFile = storageService.resolveFile(path + name, session.rootDir());
        //save file to temporal location
        try {
            if (size == 0) throw new IOException("Empty file");
            file.transferTo(Configuration.getInstance().getTempPath().resolve(id));
        } catch (IOException ex) {
            log.error("Error receiving file", ex);
            return new ModelAndView("error", Map.of("message", "Unable to create temporal file"));
        }
        //fill variables
        mav.addObject("id", id);
        mav.addObject("path", path);
        mav.addObject("name", name);
        mav.addObject("newSize", String.format("%,d", size));
        mav.addObject("exists", resolvedFile.exists());
        mav.addObject("oldSize", String.format("%,d", resolvedFile.size()));
        log.info("upload() path={} {}ms", path, System.currentTimeMillis() - start);
        return mav;
    }

    @ExceptionHandler(LoginException.class)
    public ModelAndView handleLoginException() {
        return new ModelAndView("login");
    }

}
