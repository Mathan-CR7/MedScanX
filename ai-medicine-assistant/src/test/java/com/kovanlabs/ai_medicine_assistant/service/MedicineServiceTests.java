package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.MedicineSearchResponse;
import com.kovanlabs.ai_medicine_assistant.dto.OCRTextRegion;
import com.kovanlabs.ai_medicine_assistant.model.Medicine;
import com.kovanlabs.ai_medicine_assistant.model.MedicinePrice;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MedicineServiceTests {

    @Autowired
    private MedicineService medicineService;

    @MockitoBean
    private OcrService ocrService;

    @MockitoBean
    private ScraperService scraperService;

    @Test
    public void testAddAndGetTrackedMedicines() {
        Medicine med = medicineService.addTrackedMedicine("Dolo 650");
        assertNotNull(med);
        assertEquals("Dolo 650", med.getName());

        List<Medicine> tracked = medicineService.getTrackedMedicines();
        assertFalse(tracked.isEmpty());
        assertTrue(tracked.stream().anyMatch(m -> m.getName().equalsIgnoreCase("Dolo 650")));
    }

    @Test
    public void testMockOcrExtraction() {
        byte[] dummyImageBytes = new byte[]{1, 2, 3, 4, 5};
        Mockito.when(ocrService.extractTextRegions(Mockito.any(), Mockito.anyString()))
                .thenReturn(List.of(
                        OCRTextRegion.builder()
                                .text("Dolo 650")
                                .confidence(0.98)
                                .x(10.0).y(10.0).width(150.0).height(40.0).area(6000.0)
                                .build(),
                        OCRTextRegion.builder()
                                .text("Paracetamol 650 mg")
                                .confidence(0.95)
                                .x(10.0).y(60.0).width(200.0).height(20.0).area(4000.0)
                                .build()
                ));

        MedicineSearchResponse response = medicineService.searchMedicineByImage(dummyImageBytes, "dolo_650.jpg");
        assertNotNull(response);
        assertNotNull(response.medicineName());
    }

    @Test
    public void testScrapingAndQuery() {
        medicineService.addTrackedMedicine("Dolo 650");

        Mockito.when(scraperService.scrapeWebsite(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(invocation -> {
                    String name = invocation.getArgument(0);
                    String site = invocation.getArgument(1);
                    return CompletableFuture.completedFuture(
                        MedicinePrice.builder()
                            .medicineName(name)
                            .website(site)
                            .price(new BigDecimal("30.00"))
                            .availability("AVAILABLE")
                            .lastUpdated(LocalDateTime.now())
                            .build()
                    );
                });

        List<MedicinePrice> prices = medicineService.scrapePricesForMedicine("Dolo 650");
        assertEquals(4, prices.size());

        List<MedicinePrice> queryPrices = medicineService.getPricesForMedicine("Dolo 650");
        assertEquals(4, queryPrices.size());
    }
}
