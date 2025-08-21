package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.statistics.IndexingResponse;

public interface IndexingServise {

    boolean isIndexing();
    void startIndexing();
    void  stopIndexing();
    ResponseEntity<IndexingResponse> indexPage(String url);
}
