package fr.abes.bestppn.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class PpnWithLogsDto extends PpnDto {

    private List<String> logs;

    public PpnWithLogsDto(){

    }
}
