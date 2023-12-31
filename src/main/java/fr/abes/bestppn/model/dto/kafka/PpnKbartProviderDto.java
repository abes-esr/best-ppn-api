package fr.abes.bestppn.model.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class PpnKbartProviderDto {
    private String ppn;
    private LigneKbartDto kbart;
    private String provider;

    @Override
    public String toString() {
        return (this.ppn != null) ? "PPN : " + ppn + " / kbart : " + kbart.toString() + " / provider : " + provider : "kbart : " + kbart.toString() + " / provider : " + provider;
    }

    @Override
    public int hashCode() {
        return (this.ppn != null) ? this.ppn.hashCode() * this.kbart.hashCode() * this.provider.hashCode() : this.kbart.hashCode() * this.provider.hashCode();
    }
}

