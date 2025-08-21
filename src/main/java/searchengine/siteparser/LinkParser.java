package searchengine.siteparser;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.CrawlerConfig;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageIndexRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.LemmaFinder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;

public class LinkParser extends RecursiveAction {

    private final String url;
    private final Site site;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final CrawlerConfig crawlerConfig;
    private final LemmaRepository lemmaRepository;
    private final PageIndexRepository pageIndexRepository;
    private final LemmaFinder lemmaFinder;

    private static final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<Site, AtomicInteger> sitePageCounters = new ConcurrentHashMap<>();
    private static final int UPDATE_INTERVAL = 10;

    public LinkParser(String url, Site site, SiteRepository siteRepository, PageRepository pageRepository, CrawlerConfig crawlerConfig,
                      LemmaRepository lemmaRepository,PageIndexRepository pageIndexRepository,LemmaFinder lemmaFinder) {
        this.url = url;
        this.site = site;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.pageIndexRepository = pageIndexRepository;
        this.lemmaFinder = lemmaFinder;
        this.crawlerConfig = crawlerConfig;

    }

    @Override
    protected void compute() {
        try {
            if (!visitedUrls.add(url)) {
                return;
            }

            Thread.sleep(500);

            Connection.Response response = Jsoup.connect(url)
                    .userAgent(crawlerConfig.getUserAgent())
                    .referrer(crawlerConfig.getReferrer())
                    .execute();

            Document doc = response.parse();


            Page page = new Page();
            page.setSite(site);
            page.setPath(url.replace(site.getUrl(), ""));
            int statusCode = response.statusCode();
            if (statusCode >=400) {
                return;
            }
            page.setCode(statusCode);
            page.setContent(doc.html());
            pageRepository.save(page);

            // Очистка HTML от тегов и извлечение текста
            String cleanText = LemmaFinder.extractText(doc.html());

            // Получение лемм и их количества
            Map<String, Integer> lemmas = lemmaFinder.collectLemmas(cleanText);

            // Обновление таблиц lemma и page_index
            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                String lemmaText = entry.getKey();
                int frequencyOnPage = entry.getValue();

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
                index.setRank(frequencyOnPage);
                pageIndexRepository.save(index);
            }
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
                if (absHref.startsWith(site.getUrl()) && !absHref.contains("#") && !absHref.equals(url)) {
                    tasks.add(new LinkParser(absHref, site, siteRepository, pageRepository, crawlerConfig,
                            lemmaRepository, pageIndexRepository, lemmaFinder));
                }
            }


            invokeAll(tasks);

        } catch (IOException | InterruptedException e) {
            site.setStatus(Status.FAILED);
            site.setLastError("Ошибка обхода: " + e.getMessage());
            siteRepository.save(site);
        }
    }
}