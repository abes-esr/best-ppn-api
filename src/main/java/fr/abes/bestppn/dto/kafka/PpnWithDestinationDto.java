package fr.abes.bestppn.dto.kafka;

import fr.abes.bestppn.dto.PpnDto;
import fr.abes.bestppn.utils.DESTINATION_TOPIC;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PpnWithDestinationDto extends PpnDto  {
    private DESTINATION_TOPIC destination;

    public PpnWithDestinationDto(String ppn, TYPE_SUPPORT typeSupport, DESTINATION_TOPIC destination) {
        super(ppn, typeSupport);
        this.destination = destination;
    }
}
