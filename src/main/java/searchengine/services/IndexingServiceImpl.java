package searchengine.services;

import lombok.RequiredArgsConstructor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageIndexRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.siteparser.SiteMapBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingServise {

    private boolean indexing = false;

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SiteMapBuilder siteMapBuilder;
    private final LemmaRepository lemmaRepository;
    private final PageIndexRepository pageIndexRepository;
    private final LemmaFinder lemmaFinder;

    @Override
    public boolean isIndexing() {
        return indexing;
    }

    @Override
    public void startIndexing() {
        indexing = true; // Индексация началась

        for (SiteConfig siteConfig : sitesList.getSites()) {
            String url = siteConfig.getUrl();

            // Удаление старых записей
            Site existingSite = siteRepository.findByUrl(url);
            if (existingSite != null) {
                pageRepository.deleteAllBySite(existingSite);
                siteRepository.delete(existingSite);
            }

            // Создание новой записи о сайте
            Site site = new Site();
            site.setUrl(url);
            site.setName(siteConfig.getName());
            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(null);
            siteRepository.save(site);

            // Запуск обхода сайта в ForkJoinPool
            siteMapBuilder.build(site);

        }

        indexing = false; // Индексация завершена
    }

    @Override
    public void stopIndexing() {
        indexing = false;
        siteMapBuilder.stopAll();

    }

    @Override
    public ResponseEntity<IndexingResponse> indexPage(String url) {
        List<Site> sites = siteRepository.findAll();
        Site site = sites.stream()
                .filter(s -> url.startsWith(s.getUrl()))
                .findFirst()
                .orElse(null);

        if (site == null) {
            return ResponseEntity.ok(new IndexingResponse(false,
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле"));
        }

        String path = url.replaceFirst(site.getUrl(), "");

        Optional<Page> existingPage = pageRepository.findByPathAndSite(path, site);
        if (existingPage.isPresent()) {
            Page page = existingPage.get();

            List<PageIndex> indices = pageIndexRepository.findAllByPage(page);
            for (PageIndex index : indices) {
                Lemma lemma = index.getLemma();
                lemma.setFrequency(lemma.getFrequency() - 1);
                if (lemma.getFrequency() <= 0) {
                    lemmaRepository.delete(lemma);
                } else {
                    lemmaRepository.save(lemma);
                }
            }

            pageIndexRepository.deleteAllByPage(page);
            pageRepository.delete(page);
        }

        Document doc;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            return ResponseEntity.ok(new IndexingResponse(false,
                    "Ошибка при попытке получить содержимое страницы: " + e.getMessage()));
        }

        Page page = new Page();
        page.setPath(path);
        page.setSite(site);
        page.setCode(200);
        page.setContent(doc.html());
        pageRepository.save(page);

        String cleanText = LemmaFinder.extractText(doc.html());
        Map<String, Integer> lemmas = lemmaFinder.collectLemmas(cleanText);
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String lemmaText = entry.getKey();
            int count = entry.getValue();

            Lemma lemma = lemmaRepository.findByLemmaAndSite(lemmaText, site).orElse(null);
            if (lemma == null) {
                lemma = new Lemma();
                lemma.setSite(site);
                lemma.setLemma(lemmaText);
                lemma.setFrequency(1);
            } else {
                lemma.setFrequency(lemma.getFrequency() + 1);
            }
            lemmaRepository.save(lemma);

            PageIndex index = new PageIndex();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank(count);
            pageIndexRepository.save(index);
        }

        return ResponseEntity.ok(new IndexingResponse(true));
    }
}