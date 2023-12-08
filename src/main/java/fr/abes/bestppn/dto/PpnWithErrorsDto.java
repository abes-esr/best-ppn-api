package fr.abes.bestppn.dto;

import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PpnWithErrorsDto extends PpnDto {

    private List<String> errors;

    public PpnWithErrorsDto(String ppn, TYPE_SUPPORT typeSupport, List<String> errors){
        super(ppn, typeSupport);
        this.errors = errors;
    }

    public void addError(String error) {
        this.errors.add(error);
    }
}
