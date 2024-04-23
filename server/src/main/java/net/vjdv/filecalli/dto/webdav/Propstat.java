package net.vjdv.filecalli.dto.webdav;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class Propstat {

    @XmlElement(namespace = "DAV:")
    private Prop prop;

    @XmlElement(namespace = "DAV:")
    private String status;

    public Propstat() {
    }

    public Propstat(Prop prop, String status) {
        this.prop = prop;
        this.status = status;
    }

}

