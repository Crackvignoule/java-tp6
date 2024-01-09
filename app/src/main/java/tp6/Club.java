package tp6;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Version;

@Entity
public class Club {

    @Id
    private Long id;

    @Version
    private int version;

    private String fabricant;

    private Double poids;

    public void setFabricant(String fabricant) {
        this.fabricant = fabricant;
    }

    public void setPoids(Double poids) {
        this.poids = poids;
    }

    public Double getPoids() {
        return poids;
    }

    public String getFabricant() {
        return fabricant;
    }

    public Long getId() {
        return id;
    }

    // No setters for id and version as they are managed by JPA
}