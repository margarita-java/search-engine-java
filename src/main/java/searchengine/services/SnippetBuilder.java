package searchengine.services;

import java.util.Set;

public class SnippetBuilder {
    public static String buildSnippet(String text, Set<String> lemmas) {

        for (String lemma : lemmas) {
            if (text.toLowerCase().contains(lemma)) {
                int index = text.toLowerCase().indexOf(lemma);
                int start = Math.max(0, index - 30);
                int end = Math.min(text.length(), index + 70);
                String snippet = text.substring(start, end);
                return snippet.replaceAll("(?i)(" + lemma + ")", "<b>$1</b>");
            }
        }
        return text.substring(0, Math.min(150, text.length()));
    }
}
