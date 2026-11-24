package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.MedicineIdentificationResponse;
import com.kovanlabs.ai_medicine_assistant.dto.MedicineSearchResponse;
import com.kovanlabs.ai_medicine_assistant.model.Medicine;
import com.kovanlabs.ai_medicine_assistant.model.MedicinePrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade Service maintaining backward compatibility by delegating domain operations
 * to specialized, single-responsibility services.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MedicineService {

    private final TrackedMedicineService trackedMedicineService;
    private final MedicinePriceService medicinePriceService;
    private final MedicineSearchService medicineSearchService;

    public List<MedicinePrice> getPricesForMedicine(String medicineName) {
        return medicinePriceService.getPricesForMedicine(medicineName);
    }

    public MedicineSearchResponse searchMedicineByImage(byte[] imageBytes, String originalFilename) {
        return medicineSearchService.searchMedicineByImage(imageBytes, originalFilename);
    }

    public MedicineIdentificationResponse identifyMedicineDetails(byte[] imageBytes, String originalFilename) {
        return medicineSearchService.identifyMedicineDetails(imageBytes, originalFilename);
    }

    public MedicineSearchResponse confirmUserBrandSelection(String confirmedBrandName) {
        return medicineSearchService.confirmUserBrandSelection(confirmedBrandName);
    }

    public Medicine addTrackedMedicine(String name) {
        return trackedMedicineService.addTrackedMedicine(name);
    }

    public List<Medicine> getTrackedMedicines() {
        return trackedMedicineService.getTrackedMedicines();
    }

    public List<MedicinePrice> scrapePricesForMedicine(String medicineName) {
        return medicinePriceService.scrapePricesForMedicine(medicineName);
    }

    public void scrapeAllMedicines() {
        medicinePriceService.scrapeAllMedicines();
    }
}
