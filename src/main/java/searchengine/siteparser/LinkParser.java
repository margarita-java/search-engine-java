package searchengine.siteparser;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.CrawlerConfig;
import searchengine.model.*;
import searchengine.repository.PageIndexRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.LemmaFinder;
import searchengine.services.PageProcessingService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;
@Slf4j
public class LinkParser extends RecursiveAction {
    private final String url;
    //private final Site site;
    private final Long siteId;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final CrawlerConfig crawlerConfig;
    private final PageProcessingService pageProcessingService;
    private final PageIndexRepository pageIndexRepository;
    private final LemmaFinder lemmaFinder;
    private final java.util.concurrent.ConcurrentHashMap.KeySetView<String, Boolean> visitedSet;
    private static final int UPDATE_INTERVAL = 10;
    private final java.util.concurrent.ConcurrentHashMap<Site, AtomicInteger> sitePageCounters;
    public LinkParser(String url,
                      Long siteId,
                      SiteRepository siteRepository,
                      PageRepository pageRepository,
                      CrawlerConfig crawlerConfig,
                      PageProcessingService pageProcessingService,
                      PageIndexRepository pageIndexRepository,
                      LemmaFinder lemmaFinder,
                      java.util.concurrent.ConcurrentHashMap.KeySetView<String, Boolean> visitedSet,
                      java.util.concurrent.ConcurrentHashMap<Site, AtomicInteger> sitePageCounters) {
        this.url = url;
        //this.site = site;
        this.siteId = siteId;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.crawlerConfig = crawlerConfig;
        this.pageProcessingService = pageProcessingService;
        this.pageIndexRepository = pageIndexRepository;
        this.lemmaFinder = lemmaFinder;
        this.visitedSet = visitedSet;
        this.sitePageCounters = sitePageCounters;
    }
    @Override
    protected void compute() {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalStateException("Site not found: id=" + siteId));
        try {
            if (!visitedSet.add(url)) {
                return;
            }
            Thread.sleep(500);
            log.debug("Connecting to URL: {}", url);
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(crawlerConfig.getUserAgent())
                    .referrer(crawlerConfig.getReferrer())
                    .execute();
            Document doc = response.parse();
            int statusCode = response.statusCode();
            if (statusCode >= 400) {
                log.warn("Skipping URL with bad status {}: {}", statusCode, url);
                return;
            }
            String path = url.replace(site.getUrl(), "");
            log.debug("Processing page (site={}, path={})", site.getUrl(), path);
            Optional<Page> existingPageOpt = pageRepository.findByPathAndSite(path, site);
            if (existingPageOpt.isPresent()) {
                pageProcessingService.deletePageAndUpdateLemmas(existingPageOpt.get());
            }
            String html = doc.html();
            String cleanText = lemmaFinder.extractText(html);
            Map<String, Integer> lemmas = lemmaFinder.
                    collectLemmas(cleanText);
            pageProcessingService.savePageAndLemmas(site, path, statusCode, html, lemmas);
            sitePageCounters.putIfAbsent(site, new AtomicInteger(0));
            int count = sitePageCounters.get(site).incrementAndGet();
            if (count % UPDATE_INTERVAL == 0) {
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            }
            Elements links = doc.select("a[href]");
            Set<LinkParser> tasks = new HashSet<>();
            for (Element link : links) {
                String absHref = link.absUrl("href");
                if (absHref.startsWith(site.getUrl() + "/")
                        && !absHref.contains("#")
                        && !absHref.equals(url)) {
                    tasks.add(new LinkParser(absHref, siteId, siteRepository, pageRepository, crawlerConfig,
                            pageProcessingService, pageIndexRepository, lemmaFinder, visitedSet, sitePageCounters));
                }
            }
            invokeAll(tasks);
        } catch (IOException e) {
            log.error("IO error while parsing {}: {}", url, e.getMessage());
            site.setStatus(Status.FAILED);
            site.setLastError("Ошибка обхода: " + e.getMessage());
            siteRepository.save(site);
        } catch (InterruptedException e) {
            log.warn("Parsing interrupted for {}: {}", url, e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Unexpected error during parsing {}: {}", url, e.getMessage(), e);
            site.setStatus(Status.FAILED);
            site.setLastError("Ошибка обхода: " + e.getMessage());
            siteRepository.save(site);
        }
    }
}