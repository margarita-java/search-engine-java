package searchengine.siteparser;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.config.CrawlerConfig;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageIndexRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.LemmaFinder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

@Component
@RequiredArgsConstructor
public class SiteMapBuilder {


    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final CrawlerConfig crawlerConfig;
    private final Map<Site, ForkJoinPool> runningPools = new ConcurrentHashMap<>();
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private PageIndexRepository pageIndexRepository;
    @Autowired
    private LemmaFinder lemmaFinder;


    /**
     * Метод запускает обход всех страниц сайта с помощью ForkJoinPool.
     * После завершения обхода обновляет статус сайта:
     * - Если не было ошибок: ставит статус INDEXED.
     * - Если ошибки были: статус остаётся FAILED (он уже будет установлен внутри LinkParser).
     */

    public void build(Site site) {
        String siteUrl = site.getUrl(); // URL главной страницы
        ForkJoinPool pool = new ForkJoinPool();
        runningPools.put(site, pool);
        LinkParser parserTask = new LinkParser(siteUrl, site, siteRepository, pageRepository, crawlerConfig,
                lemmaRepository, pageIndexRepository, lemmaFinder);
        pool.invoke(parserTask);
        runningPools.remove(site);

        // Если во время обхода ошибок не было, то меняем статус на INDEXED
        if (site.getStatus() != Status.FAILED) {
            site.setStatus(Status.INDEXED); // успешно завершили обход
            site.setStatusTime(LocalDateTime.now()); // обновляем время завершения
            site.setLastError(null); // очищаем ошибку
            siteRepository.save(site); // сохраняем изменения в базу
        }
    }

    public void stopAll() {
        for (Map.Entry<Site, ForkJoinPool> entry : runningPools.entrySet()) {
            Site site = entry.getKey();
            ForkJoinPool pool = entry.getValue();

            pool.shutdownNow(); // Принудительно останавливаем

            site.setStatus(Status.FAILED);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError("Индексация остановлена пользователем");

            siteRepository.save(site);
        }
        runningPools.clear();
    }



}
