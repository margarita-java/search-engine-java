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

        // --- 1. Проверяем существующую страницу ---
        Optional<Page> existingPageOpt = pageRepository.findByPathAndSite(path, site);

        if (existingPageOpt.isPresent()) {
            Page existingPage = existingPageOpt.get();

            // --- 1.1 Корректируем частоты лемм ---
            for (PageIndex idx : existingPage.getIndices()) {
                Lemma lemma = idx.getLemma();
                if (lemma != null) {
                    int updatedFreq = lemma.getFrequency() - Math.round(idx.getRank());
                    lemma.setFrequency(Math.max(updatedFreq, 0)); // исключаем отрицательные
                    lemmaRepository.save(lemma);
                }
            }

            // --- 1.2 Удаляем страницу ---
            // КАСКАД САМ УДАЛИТ ВСЕ PageIndex
            pageRepository.delete(existingPage);
        }

        // --- 2. Создаём новую страницу ---
        Page page = new Page();
        page.setSite(site);
        page.setPath(path);
        page.setCode(200);
        page.setContent(content);
        pageRepository.save(page);

        // --- 3. Извлекаем текст и леммы ---
        String text = lemmaFinder.extractText(content);
        Map<String, Integer> lemmaMap = lemmaFinder.collectLemmas(text);

        // --- 4. Создаём / обновляем леммы + PageIndex ---
        for (Map.Entry<String, Integer> entry : lemmaMap.entrySet()) {

            String lemmaText = entry.getKey();
            int count = entry.getValue();

            // 4.1 Ищем лемму или создаём новую
            Lemma lemma = lemmaRepository.findByLemmaAndSite(lemmaText, site)
                    .orElseGet(() -> {
                        Lemma newLemma = new Lemma();
                        newLemma.setSite(site);
                        newLemma.setLemma(lemmaText);
                        newLemma.setFrequency(0);
                        return lemmaRepository.save(newLemma);
                    });

            // 4.2 Обновляем частоту
            lemma.setFrequency(lemma.getFrequency() + count);
            lemmaRepository.save(lemma);

            // 4.3 Создаём индекс
            PageIndex pageIndex = new PageIndex();
            pageIndex.setPage(page);
            pageIndex.setLemma(lemma);
            pageIndex.setRank(count);

            pageIndexRepository.save(pageIndex);
        }
    }
}