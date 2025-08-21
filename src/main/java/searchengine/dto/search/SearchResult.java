package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResult {
    private String site;
    private String siteName;
    private String url;
    private String title;
    private String snippet;
    private float relevance;
}
