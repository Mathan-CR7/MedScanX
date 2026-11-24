package com.kovanlabs.ai_medicine_assistant.controller;

import com.kovanlabs.ai_medicine_assistant.dto.ConfirmBrandRequest;
import com.kovanlabs.ai_medicine_assistant.dto.MedicineIdentificationResponse;
import com.kovanlabs.ai_medicine_assistant.dto.MedicineSearchResponse;
import com.kovanlabs.ai_medicine_assistant.dto.TrackMedicineRequest;
import com.kovanlabs.ai_medicine_assistant.model.Medicine;
import com.kovanlabs.ai_medicine_assistant.model.MedicinePrice;
import com.kovanlabs.ai_medicine_assistant.service.MedicinePriceService;
import com.kovanlabs.ai_medicine_assistant.service.MedicineReportService;
import com.kovanlabs.ai_medicine_assistant.service.MedicineSearchService;
import com.kovanlabs.ai_medicine_assistant.service.TrackedMedicineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/medicines")
@RequiredArgsConstructor
@Slf4j
public class MedicineController {

    private final TrackedMedicineService trackedMedicineService;
    private final MedicinePriceService medicinePriceService;
    private final MedicineSearchService medicineSearchService;
    private final MedicineReportService medicineReportService;

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "medicine_image.png";
        }
        // Remove special characters, spaces, control symbols
        String clean = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (clean.length() > 60) {
            clean = clean.substring(clean.length() - 60);
        }
        return clean;
    }

    // POST /api/medicines/search-image
    @PostMapping("/search-image")
    public ResponseEntity<?> searchByImage(@RequestParam(value = "file", required = false) MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Please upload a valid image file using key 'file' in Body -> form-data.",
                    "status", 400
            ));
        }
        String safeFilename = sanitizeFilename(file.getOriginalFilename());
        log.info("Received image search request. Original: '{}', Sanitized: '{}'", file.getOriginalFilename(), safeFilename);
        try {
            byte[] bytes = file.getBytes();
            MedicineSearchResponse result = medicineSearchService.searchMedicineByImage(bytes, safeFilename);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to process image search request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error processing image: " + e.getMessage(), "status", 500));
        }
    }

    // POST /api/medicines/identify
    @PostMapping("/identify")
    public ResponseEntity<?> identifyDetails(@RequestParam(value = "file", required = false) MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Please upload a valid image file using key 'file' in Body -> form-data.",
                    "status", 400
            ));
        }
        String safeFilename = sanitizeFilename(file.getOriginalFilename());
        log.info("Received image detail identification request. Original: '{}', Sanitized: '{}'", file.getOriginalFilename(), safeFilename);
        try {
            byte[] bytes = file.getBytes();
            MedicineIdentificationResponse result = medicineSearchService.identifyMedicineDetails(bytes, safeFilename);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to process detail identification request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error processing image: " + e.getMessage(), "status", 500));
        }
    }

    // Exception handler for non-multipart or missing multipart file requests
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<?> handleMultipartException(MultipartException e) {
        log.warn("Invalid multipart request received: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "Invalid request format. Please select 'Body -> form-data' in Postman and set key to 'file' with type 'File'.",
                        "status", 400
                ));
    }

    // POST /api/medicines/confirm-brand
    @PostMapping("/confirm-brand")
    public ResponseEntity<MedicineSearchResponse> confirmBrand(@Valid @RequestBody ConfirmBrandRequest request) {
        log.info("Received user brand confirmation request for: '{}'", request.getBrandName());
        MedicineSearchResponse response = medicineSearchService.confirmUserBrandSelection(request.getBrandName());
        return ResponseEntity.ok(response);
    }

    // GET /api/medicines/search?name={name}
    @GetMapping("/search")
    public ResponseEntity<List<MedicinePrice>> searchByName(@RequestParam("name") String name) {
        log.info("Received name search request for: '{}'", name);
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<MedicinePrice> prices = medicinePriceService.getPricesForMedicine(name);
        return ResponseEntity.ok(prices);
    }

    // POST /api/medicines
    @PostMapping
    public ResponseEntity<Medicine> addTrackedMedicine(@RequestBody TrackMedicineRequest request) {
        Medicine medicine = trackedMedicineService.addTrackedMedicine(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(medicine);
    }

    // GET /api/medicines
    @GetMapping
    public ResponseEntity<List<Medicine>> getTrackedMedicines() {
        List<Medicine> medicines = trackedMedicineService.getTrackedMedicines();
        return ResponseEntity.ok(medicines);
    }

    // POST /api/medicines/scrape
    @PostMapping("/scrape")
    public ResponseEntity<?> manualScrape(@RequestParam(value = "name", required = false) String name) {
        if (name != null && !name.trim().isEmpty()) {
            log.info("Manual scrape triggered for medicine: '{}'", name);
            List<MedicinePrice> prices = medicinePriceService.scrapePricesForMedicine(name);
            return ResponseEntity.ok(prices);
        } else {
            log.info("Manual scrape triggered for all tracked medicines...");
            CompletableFuture.runAsync(medicinePriceService::scrapeAllMedicines);
            return ResponseEntity.accepted().body("Manual scrape for all tracked medicines started in background.");
        }
    }

    // GET /api/medicines/{name}/report
    @GetMapping("/{name}/report")
    public ResponseEntity<Map<String, String>> getMedicineReport(@PathVariable("name") String name) {
        log.info("Received full report request for medicine: '{}'", name);
        String report = medicineReportService.getFullMedicineReportFormatted(name);
        return ResponseEntity.ok(Map.of("medicineName", name, "reportHtml", report));
    }
}
