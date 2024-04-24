package net.vjdv.filecalli.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.dto.WebdavSessionDTO;
import net.vjdv.filecalli.dto.webdav.Multistatus;
import net.vjdv.filecalli.exceptions.AuthException;
import net.vjdv.filecalli.exceptions.LoginException;
import net.vjdv.filecalli.exceptions.ResourceNotFoundException;
import net.vjdv.filecalli.services.SessionService;
import net.vjdv.filecalli.services.WebdavService;
import net.vjdv.filecalli.util.TempFileSystemResource;
import net.vjdv.filecalli.util.Utils;
import org.glassfish.jaxb.runtime.marshaller.NamespacePrefixMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
@RequestMapping("/webdav")
@RestController
public class WebDavController {

    private final SessionService sessionService;
    private final WebdavService webdavService;

    public WebDavController(SessionService sessionService, WebdavService webdavService) {
        this.sessionService = sessionService;
        this.webdavService = webdavService;
    }

    @GetMapping(value = "/**")
    public ResponseEntity<TempFileSystemResource> getResource(HttpServletRequest request) {
        WebdavSessionDTO session = parseSession(request);
        String requestPath = request.getRequestURI();
        try {
            var datafile = webdavService.retrieve(requestPath, session);
            return ResponseEntity
                    .ok()
                    .header("Content-Type", datafile.mimeType())
                    .header("Last-Modified", Utils.toRFC7231(datafile.lastModified()))
                    .body(new TempFileSystemResource(datafile.path()));
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @RequestMapping(value = "/**")
    public ResponseEntity<String> handleRequest(HttpServletRequest request) {
        WebdavSessionDTO session = parseSession(request);
        String method = request.getMethod();
        String requestPath = request.getRequestURI();
        log.info("method={} requestPath={}", method, requestPath);
        //prints parameters
        request.getParameterMap().forEach((k, v) -> log.info("param {}: {}", k, String.join(", ", v)));
        //prints all headers
        request.getHeaderNames().asIterator().forEachRemaining(name -> log.info("{}: {}", name, request.getHeader(name)));
        //prints body
        /*try {
            request.getReader().lines().forEach(log::info);
        } catch (Exception e) {
            log.error("Error reading body", e);
        }*/
        //process by method
        switch (method) {
            case "PROPFIND": {
                var multistatus = webdavService.propfind(requestPath, session.rootDir());
                String xmlResponse = marshalToXml(multistatus);
                log.info("xmlResponse={}", xmlResponse);
                return ResponseEntity.status(207).contentType(MediaType.APPLICATION_XML).body(xmlResponse);
            }
            case "PUT": {
                String mime = request.getHeader("Content-Type");
                String sizeStr = request.getHeader("Content-Length");
                try {
                    long size = Long.parseLong(sizeStr);
                    webdavService.store(requestPath, mime, size, request.getInputStream(), session);
                    return ResponseEntity.created(new URI(requestPath)).build();
                } catch (IOException ex) {
                    return ResponseEntity.badRequest().body("Error reading body");
                } catch (NumberFormatException ex) {
                    return ResponseEntity.badRequest().body("Invalid Content-Length");
                } catch (URISyntaxException ex) {
                    return ResponseEntity.badRequest().body("Invalid URI");
                }
            }
            case "DELETE":
                break;
            case "MKCOL":
                break;
            case "COPY":
                break;
            case "MOVE":
                break;
            case "OPTIONS":
                break;
            case "LOCK":
                break;
            case "UNLOCK":
                break;
            default:
                break;
        }
        return ResponseEntity.badRequest().body("Method not supported");
    }

    private WebdavSessionDTO parseSession(HttpServletRequest request) throws AuthException {
        //Reads authorization header
        String auth = request.getHeader("Authorization");
        if (auth == null) {
            throw new AuthException(401, "Authorization required");
        }
        //Validates authorization
        if (!auth.startsWith("Basic ")) {
            throw new AuthException(401, "Invalid authorization");
        }
        auth = auth.substring(6);
        WebdavSessionDTO session;
        try {
            session = sessionService.getSessionFromBasicAuth(auth);
        } catch (LoginException ex) {
            throw new AuthException(401, ex.getMessage());
        }
        //request path validation
        String requestPath = request.getRequestURI();
        if (!requestPath.startsWith(session.path())) {
            log.info("Request path {} outside of token path {}", requestPath, session.path());
            throw new AuthException(403, "Forbidden");
        }
        return session;
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<String> handleAuthException(AuthException ex) {
        return ex.getResponseEntity();
    }

    /**
     * Marshals objects to XML
     *
     * @param multistatus object
     * @return xml
     */
    private String marshalToXml(Multistatus multistatus) {
        try {
            // Create JAXB context and marshaller
            JAXBContext context = JAXBContext.newInstance(Multistatus.class);
            Marshaller marshaller = context.createMarshaller();
            // Configure marshaller for pretty-printing
            marshaller.setProperty("org.glassfish.jaxb.namespacePrefixMapper", new MyNamespacePrefixMapper());
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            // Marshal the Multistatus object to XML
            StringWriter writer = new StringWriter();
            marshaller.marshal(multistatus, writer);
            return writer.toString();
        } catch (JAXBException ex) {
            throw new RuntimeException("Error marshalling to XML", ex);
        }
    }

    public static class MyNamespacePrefixMapper extends NamespacePrefixMapper {
        @Override
        public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
            if ("DAV:".equals(namespaceUri)) {
                return "D";
            }
            return suggestion;
        }
    }

}