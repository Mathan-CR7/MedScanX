package com.kovanlabs.ai_medicine_assistant.service;

import org.springframework.stereotype.Service;
import java.util.Locale;

@Service
public class OCRNormalizationService {

    /**
     * Normalizes text for matching:
     * - Converts to uppercase
     * - Removes punctuation, trademark symbols, hyphens, and non-alphanumeric noise
     * - Collapses multiple spaces
     */
    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[™®©]", "")
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Compact normalization (removes all whitespace as well, e.g. "PAN-D" -> "PAND", "Pan D" -> "PAND")
     */
    public String normalizeCompact(String text) {
        return normalize(text).replaceAll("\\s+", "");
    }
}
