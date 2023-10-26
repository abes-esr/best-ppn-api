package fr.abes.bestppn.entity.bacon;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "PROVIDER_PACKAGE")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProviderPackage implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PROVIDER_PACKAGE")
    private Integer idProviderPackage;
    @Column(name = "PACKAGE")
    private String packageName;
    @Column(name = "DATE_P")
    private Date dateP;
    @Column(name = "PROVIDER_IDT_PROVIDER")
    private Integer providerIdtProvider;

    @Column(name = "LABEL_ABES")
    private char labelAbes;

    @ManyToOne
    @JoinColumn(referencedColumnName = "IDT_PROVIDER", insertable = false, updatable = false)
    private Provider provider;

    public ProviderPackage(String packageName, Date dateP, Integer providerIdtProvider, char labelAbes) {
        this.packageName = packageName;
        this.dateP = dateP;
        this.providerIdtProvider = providerIdtProvider;
        this.labelAbes = labelAbes;
    }
}
