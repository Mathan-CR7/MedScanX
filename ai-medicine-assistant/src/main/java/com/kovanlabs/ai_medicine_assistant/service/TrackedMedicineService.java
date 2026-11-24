package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.model.Medicine;
import com.kovanlabs.ai_medicine_assistant.repository.MedicineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackedMedicineService {

    private final MedicineRepository medicineRepository;

    @Transactional
    public Medicine addTrackedMedicine(String name) {
        String cleanName = name.trim();
        if (medicineRepository.existsByNameIgnoreCase(cleanName)) {
            return medicineRepository.findByNameIgnoreCase(cleanName).orElseThrow();
        }
        log.info("Adding new tracked medicine: '{}'", cleanName);
        return medicineRepository.save(Medicine.builder().name(cleanName).build());
    }

    public List<Medicine> getTrackedMedicines() {
        return medicineRepository.findAll();
    }
}
