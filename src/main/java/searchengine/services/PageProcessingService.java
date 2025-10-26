package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageIndexRepository;
import searchengine.repository.PageRepository;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PageProcessingService {

    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final PageIndexRepository pageIndexRepository;


    @Transactional
    public void savePageAndLemmas(Site site, String path, int statusCode, String htmlContent, Map<String, Integer> lemmasMap) {

        Page page = new Page();
        page.setPath(path);
        page.setSite(site);
        page.setCode(statusCode);
        page.setContent(htmlContent);
        page = pageRepository.save(page);

        for (Map.Entry<String, Integer> entry : lemmasMap.entrySet()) {
            String lemmaText = entry.getKey();
            int countOnPage = entry.getValue();
            if (countOnPage <= 0) continue;

            Optional<Lemma> lemmaOpt = lemmaRepository.findByLemmaAndSite(lemmaText, site);
            Lemma lemma;
            if (lemmaOpt.isPresent()) {
                lemma = lemmaOpt.get();
                lemma.setFrequency(lemma.getFrequency() + countOnPage);
            } else {
                lemma = new Lemma();
                lemma.setSite(site);
                lemma.setLemma(lemmaText);
                lemma.setFrequency(countOnPage);
            }
            lemma = lemmaRepository.save(lemma);

            PageIndex index = new PageIndex();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank(countOnPage);
            pageIndexRepository.save(index);
        }
    }


    @Transactional
    public void deletePageAndUpdateLemmas(Page page) {
        for (PageIndex index : pageIndexRepository.findAllByPage(page)) {
            Lemma lemma = index.getLemma();
            int decrement = Math.round(index.getRank());
            lemma.setFrequency(lemma.getFrequency() - decrement);
            if (lemma.getFrequency() <= 0) {
                lemmaRepository.delete(lemma);
            } else {
                lemmaRepository.save(lemma);
            }
        }
        pageIndexRepository.deleteAllByPage(page);
        pageRepository.delete(page);
    }
}