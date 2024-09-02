package fr.abes.bestppn.model.dto;

import fr.abes.bestppn.model.dto.kafka.LigneKbartDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@Slf4j
public class PackageKbartDto {

    private String packageName;
    private Date datePackage;
    private String provider;
    private List<LigneKbartDto> kbartDtos = Collections.synchronizedList(new ArrayList<>());

    public void addKbartDto(LigneKbartDto ligneKbartDto) {
        this.kbartDtos.add(ligneKbartDto);
    }

}
