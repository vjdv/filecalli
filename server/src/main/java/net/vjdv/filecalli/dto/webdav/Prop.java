package net.vjdv.filecalli.dto.webdav;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Setter
@Getter
@XmlAccessorType(XmlAccessType.FIELD)
public class Prop {

    private static final DateTimeFormatter CREATED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final DateTimeFormatter MODIFIED_FORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'");

    @XmlElement(name = "displayname", namespace = "DAV:")
    private String displayname;

    @XmlElement(name = "getcontenttype", namespace = "DAV:")
    private String contentType;

    @XmlElement(name = "getcontentlength", namespace = "DAV:")
    private String contentLength;

    @XmlElement(name = "creationdate", namespace = "DAV:")
    private String creationdate;

    @XmlElement(name = "getlastmodified", namespace = "DAV:")
    private String getlastmodified;

    // Add more properties as needed

    public Prop() {
    }

    public Prop(String displayname, long creationdate, long getlastmodified) {
        this.displayname = displayname;
        this.contentType = "directory";
        this.creationdate = Instant.ofEpochMilli(creationdate).atZone(ZoneId.systemDefault()).format(CREATED_FORMAT);
        this.getlastmodified = Instant.ofEpochMilli(getlastmodified).atZone(ZoneId.systemDefault()).format(MODIFIED_FORMAT);
    }

    public Prop(String displayname, String mime, long size, long creationdate, long getlastmodified) {
        this.displayname = displayname;
        this.contentType = mime;
        this.contentLength = String.valueOf(size);
        this.creationdate = Instant.ofEpochMilli(creationdate).atZone(ZoneId.systemDefault()).format(CREATED_FORMAT);
        this.getlastmodified = Instant.ofEpochMilli(getlastmodified).atZone(ZoneId.systemDefault()).format(MODIFIED_FORMAT);
    }

}

