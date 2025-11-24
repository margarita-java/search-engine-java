package searchengine.model;
import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
@Entity
@Table(name = "page")
@Getter
@Setter
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String path;
    private int code;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PageIndex> indices = new ArrayList<>();
}
