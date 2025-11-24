package searchengine.services;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.PageIndex;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageIndexRepository;
import searchengine.repository.PageRepository;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PageIndexingService {

    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final PageIndexRepository pageIndexRepository;
    private final LemmaFinder lemmaFinder;

    public String fetchPageContent(String url) throws IOException {
        Document doc = Jsoup.connect(url).get();
        return doc.html();
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createOrUpdatePage(Site site, String path, String content) {
        Optional<Page> existingPageOpt = pageRepository.findByPathAndSite(path, site);
        if (existingPageOpt.isPresent()) {
            Page existingPage = existingPageOpt.get();
            pageIndexRepository.findAllByPage(existingPage).forEach(idx -> {
                Lemma lemma = idx.getLemma();
                if (lemma != null) {
                    int newFreq = lemma.getFrequency() - Math.round(idx.getRank());
                    if (newFreq < 0) newFreq = 0;
                    lemma.setFrequency(newFreq);
                    lemmaRepository.save(lemma);
                    }

            });
            pageIndexRepository.deleteAllByPage(existingPage);
            pageRepository.delete(existingPage);
        }
        Page page = new Page();
        page.setSite(site);
        page.setPath(path);
        page.setCode(200);
        page.setContent(content);
        pageRepository.save(page);
        String text = lemmaFinder.extractText(content);
        Map<String, Integer> lemmaMap = lemmaFinder.collectLemmas(text);
        for (Map.Entry<String, Integer> entry : lemmaMap.entrySet()) {
            String lemmaText = entry.getKey();
            int count = entry.getValue();
            Lemma lemma = lemmaRepository.findByLemmaAndSite(lemmaText, site).orElseGet(() -> {
                Lemma newLemma = new Lemma();
                newLemma.setSite(site);
                newLemma.setLemma(lemmaText);
                newLemma.setFrequency(0);
                return lemmaRepository.save(newLemma);
            });
            lemma.setFrequency(lemma.getFrequency() + count);
            lemma = lemmaRepository.save(lemma);
            if (lemma == null) {
                throw new RuntimeException("LEMMA IS NULL for lemmaText=" + lemmaText);
            }
            if (lemma.getId() == null) {
                throw new RuntimeException("LEMMA ID IS NULL EVEN AFTER SAVE: " + lemma.getLemma());
            }

            PageIndex pageIndex = new PageIndex();
            pageIndex.setPage(page);
            pageIndex.setLemma(lemma);
            pageIndex.setRank(count);

            if (pageIndex.getLemma() == null) {
                throw new RuntimeException("PageIndex lemma is NULL before save()");
            }
            pageIndexRepository.save(pageIndex);
        }
    }
}