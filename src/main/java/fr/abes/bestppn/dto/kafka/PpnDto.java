package fr.abes.bestppn.dto.kafka;

import fr.abes.bestppn.utils.DESTINATION_TOPIC;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PpnDto {

    @Nullable
    private String ppn;

    private TYPE_SUPPORT typeSupport;

    private DESTINATION_TOPIC destination;

    private String error;

    public PpnDto(String ppn, DESTINATION_TOPIC destination) {
        this.ppn = ppn;
        this.destination = destination;
    }

    public PpnDto(String ppn, DESTINATION_TOPIC destination, TYPE_SUPPORT typeSupport) {
        this.ppn = ppn;
        this.destination = destination;
        this.typeSupport = typeSupport;
    }

    public PpnDto(String ppn, DESTINATION_TOPIC destination, String error) {
        this.ppn = ppn;
        this.destination = destination;
        this.error = error;
    }

    public PpnDto(String ppn, DESTINATION_TOPIC destination, TYPE_SUPPORT typeSupport, String error) {
        this.ppn = ppn;
        this.destination = destination;
        this.typeSupport = typeSupport;
        this.error = error;
    }

    public  PpnDto (String error) {
        this.error = error;
    }
}
