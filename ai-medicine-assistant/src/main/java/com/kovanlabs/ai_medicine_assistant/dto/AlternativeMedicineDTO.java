package com.kovanlabs.ai_medicine_assistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlternativeMedicineDTO implements Serializable {

    private static final long serialVersionUID = 1L;
    private String medicineName;
    private String composition;
    private String strength;
    private String dosageForm;
    /** Lowest price found across all stores */
    private BigDecimal lowestPrice;
    /** Store-wise price breakdown from Playwright */
    private List<PriceResult> storePrices;
}
