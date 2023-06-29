package fr.abes.bestppn.dto.mail;

import fr.abes.bestppn.dto.kafka.LigneKbartDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PackageKbartDto {
    private List<LigneKbartDto> kbartDtos = new ArrayList<>();

    public void addKbartDto(LigneKbartDto ligneKbartDto) {
        this.kbartDtos.add(ligneKbartDto);
    }
}
