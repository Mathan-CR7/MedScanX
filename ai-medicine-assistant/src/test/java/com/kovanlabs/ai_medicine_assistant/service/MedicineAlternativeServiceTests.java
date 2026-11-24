package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.AiAlternativeSuggestion;
import com.kovanlabs.ai_medicine_assistant.dto.MedicineAlternativeResponse;
import com.kovanlabs.ai_medicine_assistant.model.MedicinePrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for MedicineAlternativeService.
 * Mocks MedicineAlternativeAiService, MedicinePriceService
 * so tests run without a real OpenAI key or Playwright.
 */
public class MedicineAlternativeServiceTests {

    private MedicineAlternativeService alternativeService;
    private MedicineAlternativeAiService aiAlternativeService;
    private MedicinePriceService medicinePriceService;

    @BeforeEach
    void setUp() {
        aiAlternativeService = mock(MedicineAlternativeAiService.class);
        medicinePriceService = mock(MedicinePriceService.class);

        // Stub AI suggestion: Dolo 650 → Paracetamol 650mg, alternatives = Calpol 650 + P 650
        AiAlternativeSuggestion suggestion =
                AiAlternativeSuggestion.builder()
                        .originalMedicine("Dolo 650")
                        .composition("Paracetamol")
                        .strength("650 mg")
                        .dosageForm("Tablet")
                        .alternativeBrands(List.of("Calpol 650", "P 650"))
                        .build();
        when(aiAlternativeService.getAlternativeBrandsFromAi("Dolo 650")).thenReturn(suggestion);

        // Stub Playwright prices:
        //   Dolo 650 original price = ₹30.00
        //   Calpol 650 alt price    = ₹22.00  (cheaper ✓)
        //   P 650 alt price         = ₹18.00  (cheaper ✓)
        MedicinePrice originalPrice = mockPrice("Dolo 650", new BigDecimal("30.00"));
        MedicinePrice calpolPrice   = mockPrice("Calpol 650", new BigDecimal("22.00"));
        MedicinePrice p650Price     = mockPrice("P 650", new BigDecimal("18.00"));

        when(medicinePriceService.scrapePricesForMedicine("Dolo 650")).thenReturn(List.of(originalPrice));
        when(medicinePriceService.scrapePricesForMedicine("Calpol 650")).thenReturn(List.of(calpolPrice));
        when(medicinePriceService.scrapePricesForMedicine("P 650")).thenReturn(List.of(p650Price));

        alternativeService = new MedicineAlternativeService(aiAlternativeService, medicinePriceService);
    }

    private MedicinePrice mockPrice(String medicineName, BigDecimal amount) {
        MedicinePrice mp = mock(MedicinePrice.class);
        when(mp.getMedicineName()).thenReturn(medicineName);
        when(mp.getPrice()).thenReturn(amount);
        when(mp.getWebsite()).thenReturn("1mg");
        when(mp.getAvailability()).thenReturn("AVAILABLE");
        return mp;
    }

    @Test
    public void testFindLowerCostAlternativesForDolo650() {
        MedicineAlternativeResponse response = alternativeService.findLowerCostAlternatives("Dolo 650");

        assertNotNull(response);
        assertEquals("Dolo 650", response.getOriginalMedicine());
        assertNotNull(response.getComposition());
        assertNotNull(response.getDisclaimer());
        assertTrue(response.getDisclaimer().contains("Consult a doctor or pharmacist"));
        assertNotNull(response.getAlternatives());
        assertFalse(response.getAlternatives().isEmpty());

        // Verify alternatives are sorted from lowest to highest price
        for (int i = 0; i < response.getAlternatives().size() - 1; i++) {
            assertTrue(response.getAlternatives().get(i).getLowestPrice()
                    .compareTo(response.getAlternatives().get(i + 1).getLowestPrice()) <= 0);
        }
    }

    @Test
    public void testFindLowerCostAlternativesForEmptyName() {
        MedicineAlternativeResponse response = alternativeService.findLowerCostAlternatives("");
        assertNotNull(response);
        assertTrue(response.getAlternatives().isEmpty());
    }
}
