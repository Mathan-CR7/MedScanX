package com.kovanlabs.ai_medicine_assistant.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Clean price result DTO — exposed in the API response.
 * Intentionally excludes internal fields: id, medicineName, lastUpdated.
 */
public record PriceResult(
    String website,
    BigDecimal price,
    String availability
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
