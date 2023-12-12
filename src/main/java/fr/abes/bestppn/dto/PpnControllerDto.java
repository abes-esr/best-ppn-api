package fr.abes.bestppn.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Getter
@Setter
@NoArgsConstructor
public class PpnControllerDto {
    @Nullable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String ppn;

    @Nullable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private TYPE_SUPPORT typeSupport;

    @Nullable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String log;

    public PpnControllerDto (String ppn, TYPE_SUPPORT typeSupport, String log) {
        this.ppn = ppn;
        this.typeSupport = typeSupport;
        this.log = log;
    }

    public  PpnControllerDto (String log) {
        this.log = log;
    }
}
