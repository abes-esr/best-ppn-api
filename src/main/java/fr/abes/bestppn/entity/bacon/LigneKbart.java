package fr.abes.bestppn.entity.bacon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "LIGNE_KBART")
@Getter
@Setter
@NoArgsConstructor
public class LigneKbart {
    @Column(name = "IDT_LIGNE_KBART")
    @Id
    private Integer id;

    @Column(name = "ID_PROVIDER_PACKAGE")
    private Integer idProviderPackage;
}
