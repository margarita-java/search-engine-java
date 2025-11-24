package searchengine.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.PageIndex;
import java.util.List;
import java.util.Optional;

public interface PageIndexRepository extends JpaRepository<PageIndex, Long> {
    void deleteAllByPage(Page page);
    List<PageIndex> findAllByPage(Page page);
    List<PageIndex> findByLemma(Lemma lemma);
    Optional<PageIndex> findByPageAndLemma(Page page, Lemma lemma);
}