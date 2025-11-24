package searchengine.services;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    @Override
    public StatisticsResponse getStatistics() {
        List<Site> sitesFromDb = siteRepository.findAll();
        TotalStatistics total = new TotalStatistics();
        total.setSites(sitesFromDb.size());
        total.setIndexing(true);
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        for (Site site : sitesFromDb) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            item.setStatus(site.getStatus().name());
            item.setStatusTime(site.getStatusTime().toEpochSecond(java.time.ZoneOffset.UTC));
            item.setError(site.getLastError());
            int pages = pageRepository.countBySite(site);
            int lemmas = lemmaRepository.countBySite(site);
            item.setPages(pages);
            item.setLemmas(lemmas);
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(data);
        return response;
    }
}
