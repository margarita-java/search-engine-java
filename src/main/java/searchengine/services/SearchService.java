package searchengine.services;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResult;
import searchengine.exceptions.BadRequestException;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageIndexRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.util.*;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {
    private final SiteRepository siteRepository;
    private final LemmaFinder lemmaFinder;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final PageIndexRepository pageIndexRepository;
    public SearchResponse search(String query, String site, int offset, int limit) {
        if (query == null || query.trim().isEmpty()) {
            throw new BadRequestException("Задан пустой поисковый запрос");
        }
        Site siteEntity = null;
        if (site != null && !site.isEmpty()) {
            siteEntity = siteRepository.findByUrl(site);
            if (siteEntity == null) {
                throw new BadRequestException("Указанный сайт не существует");
            }
            if (siteEntity.getStatus() != Status.INDEXED) {
                throw new BadRequestException("Указанный сайт не проиндексирован");
            }
        }
        Map<String, Integer> lemmaMap = lemmaFinder.collectLemmas(query);
        if (lemmaMap.isEmpty()) {
            return new SearchResponse(true, 0, List.of());
        }
        long totalPages = (siteEntity != null)
                ? pageRepository.countBySite(siteEntity)
                : pageRepository.count();
        double threshold = totalPages * 0.8;
        final Site finalSiteEntity = siteEntity;
        List<Lemma> relevantLemmas = lemmaMap.keySet().stream()
                .map(name -> finalSiteEntity != null
                        ? lemmaRepository.findByLemmaAndSite(name, finalSiteEntity).orElse(null)
                        : lemmaRepository.findByLemma(name).orElse(null))
                .filter(Objects::nonNull)
                .filter(lemma -> lemma.getFrequency() < threshold)
                .sorted(Comparator.comparingInt(Lemma::getFrequency))
                .collect(Collectors.toList());
        if (relevantLemmas.isEmpty()) {
            return new SearchResponse(true, 0, List.of());
        }
        Set<Page> finalPages = null;
        for (Lemma lemma : relevantLemmas) {
            List<PageIndex> indexes = pageIndexRepository.findByLemma(lemma);
            Set<Page> pagesForLemma = indexes.stream()
                    .map(PageIndex::getPage)
                    .filter(p -> finalSiteEntity == null || p.getSite().equals(finalSiteEntity))
                    .collect(Collectors.toSet());
            if (finalPages == null) {
                finalPages = new HashSet<>(pagesForLemma);
            } else {
                finalPages.retainAll(pagesForLemma);
            }
            if (finalPages.isEmpty()) {
                break;
            }
        }
        if (finalPages == null || finalPages.isEmpty()) {
            return new SearchResponse(true, 0, List.of());
        }
        Map<Page, Float> relevance = new HashMap<>();
        float maxAbsRel = 0f;
        for (Page page : finalPages) {
            float absRel = 0f;
            for (Lemma lemma : relevantLemmas) {
                absRel += pageIndexRepository.findByPageAndLemma(page, lemma)
                        .map(PageIndex::getRank)
                        .orElse(0f);
            }
            relevance.put(page, absRel);
            if (absRel > maxAbsRel) {
                maxAbsRel = absRel;
            }
        }
        final float finalMaxRel = maxAbsRel;
        final Set<String> finalLemmaNames = lemmaMap.keySet();
        List<SearchResult> results = relevance.entrySet().stream()
                .sorted(Map.Entry.<Page, Float>comparingByValue().reversed())
                .skip(offset)
                .limit(limit)
                .map(entry -> {
                    Page page = entry.getKey();
                    float rel = finalMaxRel == 0 ? 0 : entry.getValue() / finalMaxRel;
                    String html = page.getContent();
                    String title = Jsoup.parse(html).title();
                    String text = lemmaFinder.extractText(html);
                    String snippet = SnippetBuilder.buildSnippet(text, finalLemmaNames);
                    if (snippet == null) snippet = "";
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
        response.setCount(finalPages.size());
        response.setData(results);
        return response;
    }
}