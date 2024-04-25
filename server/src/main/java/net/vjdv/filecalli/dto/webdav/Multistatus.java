package net.vjdv.filecalli.dto.webdav;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "multistatus", namespace = "DAV:")
@XmlAccessorType(XmlAccessType.FIELD)
public class Multistatus {

    @Getter
    @Setter
    @XmlElement(name = "response", namespace = "DAV:")
    private Response[] responses;

    public Multistatus() {
    }

    public Multistatus(Response[] responses) {
        this.responses = responses;
    }

    public static MultistatusBuilder builder() {
        return new MultistatusBuilder();
    }

    public static class MultistatusBuilder {
        private final List<Response> responses = new ArrayList<>();

        public MultistatusBuilder() {
        }

        public MultistatusBuilder directory(String name, String path, long createdAt, long lastModified) {
            var response = new Response.ResponseBuilder().directory(name, path, createdAt, lastModified).build();
            responses.add(response);
            return MultistatusBuilder.this;
        }

        public MultistatusBuilder file(String name, String path, String mime, long size, long createdAt, long lastModified) {
            var response = new Response.ResponseBuilder().file(name, path, mime, size, createdAt, lastModified).build();
            responses.add(response);
            return MultistatusBuilder.this;
        }

        public Multistatus build() {
            return new Multistatus(responses.toArray(new Response[0]));
        }
    }

}

