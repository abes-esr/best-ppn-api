package fr.abes.bestppn.entity.bacon;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;


@Embeddable
@Getter
@NoArgsConstructor
public class ProviderPackageId implements Serializable {
    @Column(name = "PACKAGE")
    private String packageName;
    @Column(name = "DATE_P")
    private Date dateP;
    @Column(name = "PROVIDER_IDT_PROVIDER")
    private Integer providerIdtProvider;

    public ProviderPackageId(String packageName, Date datePackage, Integer idtProvider) {
        this.packageName = packageName;
        this.dateP = datePackage;
        this.providerIdtProvider = idtProvider;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProviderPackageId)) return false;
        ProviderPackageId that = (ProviderPackageId) o;
        return Objects.equals(getProviderIdtProvider(), that.getProviderIdtProvider()) &&
                Objects.equals(getDateP(), that.getDateP()) &&
                Objects.equals(getPackageName(), that.getPackageName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getProviderIdtProvider(), getDateP(), getPackageName());
    }
}
