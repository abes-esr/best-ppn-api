package fr.abes.bestppn.model;

import fr.abes.bestppn.utils.DESTINATION_TOPIC;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class BestPpn {

    @Nullable
    private String ppn;

    @Nullable
    private TYPE_SUPPORT typeSupport;

    @Nullable
    private DESTINATION_TOPIC destination;

    public BestPpn(String ppn, DESTINATION_TOPIC destination) {
        this.ppn = ppn;
        this.destination = destination;
    }

    public BestPpn(String ppn, DESTINATION_TOPIC destination, TYPE_SUPPORT typeSupport) {
        this.ppn = ppn;
        this.destination = destination;
        this.typeSupport = typeSupport;
    }

    public BestPpn(String ppn, DESTINATION_TOPIC destination, List<String> logs) {
        this.ppn = ppn;
        this.destination = destination;
    }

    public BestPpn(String ppn, DESTINATION_TOPIC destination, TYPE_SUPPORT typeSupport, List<String> logs) {
        this.ppn = ppn;
        this.destination = destination;
        this.typeSupport = typeSupport;
    }
}
