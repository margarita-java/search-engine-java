package searchengine.services;

import lombok.RequiredArgsConstructor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.exceptions.BadRequestException;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageIndexRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.siteparser.SiteMapBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Transactional
public class IndexingServiceImpl implements IndexingService {

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
        indexing = true;

        List<CompletableFuture<Void>> tasks = new ArrayList<>();

        for (SiteConfig siteConfig : sitesList.getSites()) {
            tasks.add(CompletableFuture.runAsync(() -> {
                String url = siteConfig.getUrl();

                Site existingSite = siteRepository.findByUrl(url);
                if (existingSite != null) {
                    siteRepository.delete(existingSite);
                }

                Site site = new Site();
                site.setUrl(url);
                site.setName(siteConfig.getName());
                site.setStatus(Status.INDEXING);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError(null);
                siteRepository.save(site);

                siteMapBuilder.build(site);

            }));
        }

        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                .thenRun(() -> indexing = false);
    }

    @Override
    public void stopIndexing() {
        indexing = false;
        siteMapBuilder.stopAll();

    }

    @Override
    public IndexingResponse indexPage(String url) {
        List<Site> sites = siteRepository.findAll();
        Site site = sites.stream()
                .filter(s -> url.startsWith(s.getUrl()))
                .findFirst()
                .orElse(null);

        if (site == null) {
            throw new BadRequestException("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }

        String path = url.replaceFirst(site.getUrl(), "");

        Optional<Page> existingPage = pageRepository.findByPathAndSite(path, site);
        if (existingPage.isPresent()) {
            Page page = existingPage.get();

            List<PageIndex> indices = pageIndexRepository.findAllByPage(page);
            for (PageIndex index : indices) {
                Lemma lemma = index.getLemma();
                // учитываем rank (вхождений на странице)
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

        Document doc;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            throw new BadRequestException("Ошибка при попытке получить содержимое страницы: " + e.getMessage());
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
                lemma.setFrequency(count);
            } else {
                lemma.setFrequency(lemma.getFrequency() + count);
            }
            lemma = lemmaRepository.save(lemma);

            PageIndex index = new PageIndex();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank(count);
            pageIndexRepository.save(index);
        }

        return new IndexingResponse(true);
    }
}