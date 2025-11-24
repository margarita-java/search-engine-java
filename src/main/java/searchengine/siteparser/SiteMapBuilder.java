package searchengine.siteparser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import searchengine.config.CrawlerConfig;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageIndexRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.LemmaFinder;
import searchengine.services.PageProcessingService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
@Component
@RequiredArgsConstructor
@Slf4j
public class SiteMapBuilder {
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final CrawlerConfig crawlerConfig;
    private final LemmaRepository lemmaRepository;
    private final PageIndexRepository pageIndexRepository;
    private final LemmaFinder lemmaFinder;
    private final PageProcessingService pageProcessingService;
    private final Map<Site, ForkJoinPool> runningPools = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.ConcurrentHashMap.KeySetView<String, Boolean>> visitedBySite = new ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Site, AtomicInteger> sitePageCounters = new ConcurrentHashMap<>();
    public List<String> build(Site site) {
        List<String> urls = new ArrayList<>();
        String siteUrl = site.getUrl();
        log.info("Starting crawl for site: {}", siteUrl);
        visitedBySite.putIfAbsent(siteUrl, ConcurrentHashMap.newKeySet());
        java.util.concurrent.ConcurrentHashMap.KeySetView<String, Boolean> visitedSet =
                visitedBySite.get(siteUrl);
        ForkJoinPool pool = new ForkJoinPool();
        runningPools.put(site, pool);
        LinkParser parserTask = new LinkParser(siteUrl, site, siteRepository, pageRepository, crawlerConfig,
                pageProcessingService, pageIndexRepository, lemmaFinder, visitedSet, sitePageCounters);
        pool.invoke(parserTask);
        runningPools.remove(site);
        visitedBySite.remove(siteUrl);
        sitePageCounters.remove(site);
        if (site.getStatus() != Status.FAILED) {
            site.setStatus(Status.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(null);
            siteRepository.save(site);
            log.info("Finished crawl for site: {} (INDEXED)", siteUrl);
        } else {
            log.warn("Finished crawl for site: {} (FAILED). Error: {}", siteUrl, site.getLastError());
        }
        return urls;
    }
    public void stopAll() {
        for (Map.Entry<Site, ForkJoinPool> entry : runningPools.entrySet()) {
            Site site = entry.getKey();
            ForkJoinPool pool = entry.getValue();
            log.info("Shutting down crawl pool for site {}", site.getUrl());
            pool.shutdownNow();
            site.setStatus(Status.FAILED);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError("Индексация остановлена пользователем");
            siteRepository.save(site);
        }
        runningPools.clear();
        visitedBySite.clear();
        sitePageCounters.clear();
    }
}
