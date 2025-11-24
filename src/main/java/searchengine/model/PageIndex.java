package searchengine.model;
import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;
import java.util.Objects;
@Entity
@Table(name = "page_index")
@Getter
@Setter
public class PageIndex {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id", nullable = false)
    private Lemma lemma;
    @Column(name = "rank", nullable = false)
    private float rank;
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PageIndex)) return false;
        PageIndex other = (PageIndex) o;
        if (this.id != null && other.id != null) {
            return this.id.equals(other.id);
        }
        Long thisPageId = this.page != null ? this.page.getId() : null;
        Long otherPageId = other.page != null ? other.page.getId() : null;
        Long thisLemmaId = this.lemma != null ? this.lemma.getId() : null;
        Long otherLemmaId = other.lemma != null ? other.lemma.getId() : null;
        return Objects.equals(thisPageId, otherPageId) &&
                Objects.equals(thisLemmaId, otherLemmaId);
    }
    @Override
    public int hashCode() {
        if (this.id != null) {
            return this.id.hashCode();
        }
        Long pageId = page != null ? page.getId() : null;
        Long lemmaId = lemma != null ? lemma.getId() : null;
        return Objects.hash(pageId, lemmaId);
    }
}