package searchengine.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;
import java.util.Optional;
@Repository
public interface PageRepository extends JpaRepository<Page, Long> {
    void deleteAllBySite(Site site);
    boolean existsByPathAndSite(String path, Site site);
    Optional<Page> findByPathAndSite(String path, Site site);
    int countBySite(Site site);
}