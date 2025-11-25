package searchengine.services;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingServiceImpl implements IndexingService {
    private static final AtomicBoolean indexing = new AtomicBoolean(false);
    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final PageIndexRepository pageIndexRepository;
    private final SiteMapBuilder siteMapBuilder;
    private final PageIndexingService pageIndexingService;
    @Override
    public boolean isIndexing() {
        return indexing.get();
    }
    @Override
    public IndexingResponse startIndexing() {
        log.info("startIndexing called - currentFlag = {}", indexing.get());
        boolean started = indexing.compareAndSet(false, true);
        if (!started) {
            return new IndexingResponse(false, "Индексация уже запущена");
        }
        List<SiteConfig> configuredSites = Optional.ofNullable(sitesList)
                .map(SitesList::getSites)
                .orElse(Collections.emptyList());
        if (configuredSites.isEmpty()) {
            indexing.set(false);
            return new IndexingResponse(false, "Список сайтов пуст. Проверьте конфигурацию.");
        }
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (SiteConfig siteConfig : configuredSites) {
            tasks.add(CompletableFuture.runAsync(() -> indexSite(siteConfig)));
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
        all.whenComplete((v, ex) -> {
            if (ex != null) log.error("allOf completed with exception: {}", ex.getMessage(), ex);
            indexing.set(false);
            log.info("Indexing completed, flag reset to false");
        });
        return new IndexingResponse(true);
    }
    @Transactional
    private void indexSite(SiteConfig siteConfig) {
        String url = siteConfig.getUrl();
        try {
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
            List<String> pageUrls = siteMapBuilder.build(site); // теперь возвращает List<String>
            for (String pageUrl : pageUrls) {
                try {
                    String content = pageIndexingService.fetchPageContent(pageUrl);
                    pageIndexingService.createOrUpdatePage(site, pageUrl.replaceFirst(url, ""), content);
                } catch (IOException e) {
                    log.error("Error indexing page {}: {}", pageUrl, e.getMessage());
                }
            }
            site.setStatus(Status.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        } catch (Exception e) {
            log.error("Task error for site {}: {}", url, e.getMessage(), e);
            Site site = siteRepository.findByUrl(url);
            if (site != null) {
                site.setStatus(Status.FAILED);
                site.setLastError("Ошибка индексации: " + e.getMessage());
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            }
        }
    }
    @Override
    public IndexingResponse stopIndexing() {
        if (!indexing.get()) {
            return new IndexingResponse(false, "Индексация не запущена");
        }
        indexing.set(false);
        siteMapBuilder.stopAll();
        return new IndexingResponse(true);
    }
    @Override
    @Transactional
    public IndexingResponse indexPage(String url) {
        Site site = siteRepository.findAll().stream()
                .filter(s -> url.startsWith(s.getUrl()))
                .findFirst()
                .orElse(null);

        if (site == null) {
            return new IndexingResponse(false,
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }

        String path = url.replaceFirst(site.getUrl(), "");
        try {
            String content = pageIndexingService.fetchPageContent(url);
            pageIndexingService.createOrUpdatePage(site, path, content);
        } catch (IOException e) {
            return new IndexingResponse(false, "Ошибка при получении страницы: " + e.getMessage());
        }

        return new IndexingResponse(true);
    }
}