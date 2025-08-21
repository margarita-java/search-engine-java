package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "page")
@Getter
@Setter
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;


    @Column(columnDefinition = "TEXT", nullable = false)
    private String path;

    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
