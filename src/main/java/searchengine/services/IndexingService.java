package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.statistics.IndexingResponse;

public interface IndexingService {

    boolean isIndexing();
    void startIndexing();
    void  stopIndexing();
    IndexingResponse indexPage(String url);
}
