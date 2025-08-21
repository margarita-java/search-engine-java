package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingServise;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }


    private final IndexingServise indexingServise;

    @GetMapping("/startIndexing")
    @Transactional
    public ResponseEntity<IndexingResponse> startIndexing() {

        if (indexingServise.isIndexing()) {
            return ResponseEntity.ok(new IndexingResponse(false, "Индексация уже запушена"));
        }
        indexingServise.startIndexing();
        return ResponseEntity.ok(new IndexingResponse(true));
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {

        if (!indexingServise.isIndexing()) {
            return ResponseEntity.ok(new IndexingResponse(false, "Индексация не запущена"));
        }
        indexingServise.stopIndexing();
        return ResponseEntity.ok(new IndexingResponse(true));
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(@RequestParam String url) {
        return indexingServise.indexPage(url);
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam(name = "query") String query,
                                                 @RequestParam(name = "site", required = false) String site,
                                                 @RequestParam(name = "offset", defaultValue = "0") int offset,
                                                 @RequestParam(name = "limit", defaultValue = "20") int limit
                                                 ) {
        return searchService.search(query, site, offset, limit);
    }




}
