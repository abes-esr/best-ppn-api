package fr.abes.bestppn.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.abes.bestppn.utils.TYPE_SUPPORT;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.List;

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
    private List<String> logs;

    public PpnControllerDto (String ppn, TYPE_SUPPORT typeSupport, List<String> logs) {
        this.ppn = ppn;
        this.typeSupport = typeSupport;
        this.logs = logs;
    }

    public  PpnControllerDto (List<String> logs) {
        this.logs = logs;
    }
}
