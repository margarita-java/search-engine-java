package searchengine.services;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageIndexRepository;
import searchengine.repository.PageRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
@Slf4j
public class PageProcessingService {
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final PageIndexRepository pageIndexRepository;
    @Transactional
    public void savePageAndLemmas(Site site, String path, int statusCode, String htmlContent, Map<String, Integer> lemmasMap) {
        log.info("savePageAndLemmas: site={}, path='{}', lemmas={}", site.getUrl(), path, lemmasMap.size());
        Page page = new Page();
        page.setPath(path);
        page.setSite(site);
        page.setCode(statusCode);
        page.setContent(htmlContent);
        page = pageRepository.save(page);
        pageRepository.flush();
        for (Map.Entry<String, Integer> entry : lemmasMap.entrySet()) {
            String lemmaText = entry.getKey();
            int countOnPage = entry.getValue();
            if (countOnPage <= 0) continue;
            Lemma lemma = lemmaRepository.findByLemmaAndSite(lemmaText, site).orElse(null);
            if (lemma == null) {
                lemma = new Lemma();
                lemma.setSite(site);
                lemma.setLemma(lemmaText);
                lemma.setFrequency(countOnPage);
                try {
                    lemma = lemmaRepository.saveAndFlush(lemma);
                } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                    // при гонке — получаем существующую лемму и обновляем частоту
                    lemma = lemmaRepository.findByLemmaAndSite(lemmaText, site)
                            .orElseThrow(() -> new RuntimeException("Не удалось получить лемму после ошибки уникальности: " + lemmaText, ex));
                    lemma.setFrequency(lemma.getFrequency() + countOnPage);
                    lemma = lemmaRepository.saveAndFlush(lemma);
                }

            } else {
                lemma.setFrequency(lemma.getFrequency() + countOnPage);
                lemma = lemmaRepository.saveAndFlush(lemma);
            }
            if (lemma == null || lemma.getId() == null) {
                log.error("Lemma not persisted for '{}', skipping PageIndex", lemmaText);
                continue;
            }
            PageIndex index = new PageIndex();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank(countOnPage);
            pageIndexRepository.save(index);
        }
        pageIndexRepository.flush();
        lemmaRepository.flush();
        pageRepository.flush();
    }
    @Transactional
    public void deletePageAndUpdateLemmas(Page page) {
        List<PageIndex> indices = pageIndexRepository.findAllByPage(page);
        Map<Long, Integer> lemmaIdToDecrement = new HashMap<>();
        Map<Long, Lemma> lemmaCache = new HashMap<>();
        for (PageIndex idx : indices) {
            Lemma lemma = idx.getLemma();
            if (lemma == null) continue;
            Long lemmaId = lemma.getId();
            int dec = Math.round(idx.getRank());
            lemmaIdToDecrement.put(lemmaId, lemmaIdToDecrement.getOrDefault(lemmaId, 0) + dec);
            lemmaCache.putIfAbsent(lemmaId, lemma);
        }
        pageIndexRepository.deleteAllByPage(page);
        for (Map.Entry<Long, Integer> entry : lemmaIdToDecrement.entrySet()) {
            Long lemmaId = entry.getKey();
            int dec = entry.getValue();
            Lemma lemma = lemmaCache.get(lemmaId);
            if (lemma == null) {
                lemma = lemmaRepository.findById(lemmaId).orElse(null);
            }
            if (lemma == null) continue;

            lemma.setFrequency(lemma.getFrequency() - dec);
            if (lemma.getFrequency() <= 0) {
                lemmaRepository.delete(lemma);
            } else {
                lemmaRepository.save(lemma);
            }
        }
        pageRepository.delete(page);
    }
}