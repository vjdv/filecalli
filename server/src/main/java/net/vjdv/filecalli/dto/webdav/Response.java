package net.vjdv.filecalli.dto.webdav;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;
import net.vjdv.filecalli.util.Configuration;

@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class Response {

    @XmlElement(namespace = "DAV:")
    private String href;

    @XmlElement(namespace = "DAV:")
    private Propstat propstat;

    public Response() {
    }

    public Response(String href, Propstat propstat) {
        this.href = href;
        this.propstat = propstat;
    }

    public static class ResponseBuilder {
        private String href;
        private Propstat propstat;

        public ResponseBuilder() {
        }

        public ResponseBuilder directory(String name, long createdAt, long lastModified) {
            Prop prop = new Prop(name, createdAt, lastModified);
            href = Configuration.getInstance().getHost() + "/webdav/" + name + "/";
            propstat = new Propstat(prop, "HTTP/1.1 200 OK");
            return this;
        }

        public ResponseBuilder file(String name, String mime, long size, long createdAt, long lastModified) {
            Prop prop = new Prop(name, mime, size, createdAt, lastModified);
            href = Configuration.getInstance().getHost() + "/webdav/" + name;
            propstat = new Propstat(prop, "HTTP/1.1 200 OK");
            return this;
        }

        public Response build() {
            return new Response(href, propstat);
        }
    }

}

