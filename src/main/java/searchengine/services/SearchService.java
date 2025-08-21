package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResult;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageIndexRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final SiteRepository siteRepository;
    private final LemmaFinder lemmaFinder;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final PageIndexRepository pageIndexRepository;

    public ResponseEntity<SearchResponse> search(String query, String site, int offset, int limit) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new SearchResponse(false, "Задан пустой поисковый запрос"));
        }

        Site siteEntity = null;
        if (site != null && !site.isEmpty()) {
            siteEntity = siteRepository.findByUrl(site);
            if (siteEntity == null || siteEntity.getStatus() != Status.INDEXED) {
                return ResponseEntity.badRequest().body(new SearchResponse(false,"Указанный сайт не проиндексирован"));
            }
        }

        // получение лемм из запроса
        Map<String, Integer> lemmas = lemmaFinder.collectLemmas(query);
        if (lemmas.isEmpty()) {
            return ResponseEntity.ok(new SearchResponse(true, 0, List.of()));
        }

        //фильтруем слишком частые леммы
        long totalPages = pageRepository.count();
        double threshold = totalPages * 0.8;
        final Site finalSiteEntity =siteEntity;
        List<Lemma> relevantLemmas = lemmas.keySet().stream()
                .map(lemma -> lemmaRepository.findByLemmaAndSite(lemma, finalSiteEntity))
                .flatMap(Optional::stream)
                .filter(lemma -> lemma.getFrequency() < threshold)
                .sorted(Comparator.comparingInt(Lemma::getFrequency))
                .collect(Collectors.toList());

        // ищес страницы, где встречаются леммы
        Set<Page> resultPages = new HashSet<>();
        boolean first = true;

        for (Lemma lemma : relevantLemmas) {
            List<PageIndex> indexes = pageIndexRepository.findByLemma(lemma);
            Set<Page> pagesForLemma = indexes.stream().map(PageIndex::getPage).collect(Collectors.toSet());

            if (first) {
                resultPages.addAll(pagesForLemma);
                first = false;
            } else {
                resultPages.retainAll(pagesForLemma); // пересечение
            }

            if (resultPages.isEmpty()) break;
        }

        // рассчитываем абсолютную релевантность
        Map<Page, Float> pageRelevance = new HashMap<>();
        float maxAbsRel = 0f;

        for (Page page : resultPages) {
            float absRel = 0f;
            for (Lemma lemma : relevantLemmas) {
                PageIndex index = pageIndexRepository.findByPageAndLemma(page, lemma);
                if (index != null) {
                    absRel += index.getRank();
                }
            }
            pageRelevance.put(page, absRel);
            maxAbsRel = Math.max(maxAbsRel, absRel);
        }

        final Map<String, Integer> finalLemmas = lemmas;
        final float finalMaxAbsRel = maxAbsRel;

        List<SearchResult> results = pageRelevance.entrySet().stream()
                .sorted(Map.Entry.<Page, Float>comparingByValue().reversed())
                .skip(offset)
                .limit(limit)
                .map(entry -> {
                    Page page = entry.getKey();
                    float rel = entry.getValue() / finalMaxAbsRel;

                    Document doc = Jsoup.parse(page.getContent());
                    String title = doc.title();
                    String text = LemmaFinder.extractText(page.getContent());
                    String snippet = SnippetBuilder.buildSnippet(text, finalLemmas.keySet());

                    return new SearchResult(
                            page.getSite().getUrl(),
                            page.getSite().getName(),
                            page.getPath(),
                            title,
                            snippet,
                            rel
                    );
                })
                .collect(Collectors.toList());

        SearchResponse response = new SearchResponse();
        response.setResult(true);
        response.setCount(resultPages.size());
        response.setData(results);
        return ResponseEntity.ok(response);

    }
}
