package fr.abes.bestppn.dto.kafka;

import fr.abes.bestppn.utils.DESTINATION_TOPIC;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PpnWithDestinationDto {
    private String ppn;
    private DESTINATION_TOPIC destination;
}
