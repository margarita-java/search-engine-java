package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    private final StatisticsService statisticsService;
    private final SearchService searchService;
    private final IndexingService indexingService;
    @GetMapping("/statistics")
    public StatisticsResponse statistics() {
        return statisticsService.getStatistics();
    }
    @GetMapping("/startIndexing")
    public IndexingResponse startIndexing() {
        return indexingService.startIndexing();
    }
    @GetMapping("/stopIndexing")
    public IndexingResponse stopIndexing() {
        return indexingService.stopIndexing();
    }
    @PostMapping("/indexPage")
    public IndexingResponse indexPage(@RequestParam String url) {
        return indexingService.indexPage(url);
    }
    @GetMapping("/search")
    public SearchResponse search(@RequestParam(name = "query") String query,
                                                 @RequestParam(name = "site", required = false) String site,
                                                 @RequestParam(name = "offset", defaultValue = "0") int offset,
                                                 @RequestParam(name = "limit", defaultValue = "20") int limit
                                                 ) {
        return searchService.search(query, site, offset, limit);
    }
}
