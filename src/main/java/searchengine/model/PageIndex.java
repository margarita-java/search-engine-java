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
    private int id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false)
    private Lemma lemma;

    @Column(name = "`rank`", nullable = false)
    private float rank;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PageIndex that = (PageIndex) o;
        return Objects.equals(page, that.page) &&
                Objects.equals(lemma, that.lemma);
    }

    @Override
    public int hashCode() {
        return Objects.hash(page, lemma);
    }
}
