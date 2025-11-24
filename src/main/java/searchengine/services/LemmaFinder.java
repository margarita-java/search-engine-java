package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component
public class LemmaFinder {

    private final LuceneMorphology morphology;
    private static final List<String> EXCLUDED_PARTS = List.of(
            "СОЮЗ", "ПРЕДЛ", "ЧАСТ", "МЕЖД"
    );

    public LemmaFinder() throws IOException {
        this.morphology = new RussianLuceneMorphology();
    }

    // Не статический метод теперь
    public String extractText(String html) {
        Document doc = Jsoup.parse(html);
        return doc.text();
    }

    public Map<String, Integer> collectLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();
        String[] words = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^а-яё\\s]", " ")
                .trim()
                .split("\\s+");
        for (String word : words) {
            if (word.isBlank() || word.length() <= 1) continue;
            if (!morphology.checkString(word)) continue;
            List<String> morphInfos = morphology.getMorphInfo(word);
            if (containsExcludedPartOfSpeech(morphInfos)) continue;
            List<String> normalForms = morphology.getNormalForms(word);
            if (!normalForms.isEmpty()) {
                String lemma = normalForms.get(0);
                lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
            }
        }
        return lemmas;
    }

    private boolean containsExcludedPartOfSpeech(List<String> morphInfos) {
        for (String morphInfo : morphInfos) {
            for (String part : EXCLUDED_PARTS) {
                if (morphInfo.toUpperCase().contains(part)) {
                    return true;
                }
            }
        }
        return false;
    }
}