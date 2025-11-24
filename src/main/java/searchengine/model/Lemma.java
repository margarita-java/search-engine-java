package searchengine.model;
import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;
import java.util.Objects;
@Entity
@Table(name = "lemma", uniqueConstraints = @UniqueConstraint(columnNames = {"site_id", "lemma"}))
@Getter
@Setter
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;
    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String lemma;
    @Column(nullable = false)
    private int frequency;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lemma)) return false;
        Lemma other = (Lemma) o;
        if (this.id != null && other.id != null) {
            return this.id.equals(other.id);
        }
        String thisSiteUrl = this.site != null ? this.site.getUrl() : null;
        String otherSiteUrl = other.site != null ? other.site.getUrl() : null;
        return Objects.equals(this.lemma, other.lemma) &&
                Objects.equals(thisSiteUrl, otherSiteUrl);
    }

    @Override
    public int hashCode() {
        if (this.id != null) {
            return this.id.hashCode();
        }
        String siteUrl = site != null ? site.getUrl() : null;
        return Objects.hash(lemma, siteUrl);
    }

}