package fr.abes.bestppn.dto.kafka;

import fr.abes.bestppn.utils.DESTINATION_TOPIC;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Getter
@Setter
@NoArgsConstructor
public class PpnDto {

    @Nullable
    private String ppn;

    @Nullable
    private TYPE_SUPPORT typeSupport;

    @Nullable
    private DESTINATION_TOPIC destination;

    @Nullable
    private String error;

    public PpnDto (String ppn, DESTINATION_TOPIC destination) {
        this.ppn = ppn;
        this.destination = destination;
    }

    public PpnDto (String ppn, DESTINATION_TOPIC destination, TYPE_SUPPORT typeSupport) {
        this.ppn = ppn;
        this.destination = destination;
        this.typeSupport = typeSupport;
    }

    public PpnDto (String ppn, DESTINATION_TOPIC destination, String error) {
        this.ppn = ppn;
        this.destination = destination;
        this.error = error;
    }

    public PpnDto (String ppn, DESTINATION_TOPIC destination, TYPE_SUPPORT typeSupport, String error) {
        this.ppn = ppn;
        this.destination = destination;
        this.typeSupport = typeSupport;
        this.error = error;
    }
}
