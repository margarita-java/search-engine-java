package searchengine.model;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Objects;


@Entity
@Table(name = "lemma")
@Getter
@Setter
public class Lemma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

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
        if (o == null || getClass() != o.getClass()) return false;
        Lemma lemma1 = (Lemma) o;
        return Objects.equals(lemma, lemma1.lemma) &&
                Objects.equals(site, lemma1.site);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lemma, site);
    }
}