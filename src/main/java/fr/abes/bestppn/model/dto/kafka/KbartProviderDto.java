package fr.abes.bestppn.model.dto.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KbartProviderDto {
    @JsonProperty("kbart")
    private String kbart;
    @JsonProperty("provider")
    private String provider;
    @JsonProperty("forceSetBestPpn")
    private boolean forceSetBestPpn;
}
