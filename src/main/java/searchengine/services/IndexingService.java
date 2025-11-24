package searchengine.services;
import searchengine.dto.statistics.IndexingResponse;
public interface IndexingService {
    boolean isIndexing();
    IndexingResponse startIndexing();
    IndexingResponse  stopIndexing();
    IndexingResponse indexPage(String url);
}
