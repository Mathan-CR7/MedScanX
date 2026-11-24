package com.kovanlabs.ai_medicine_assistant.model;

import lombok.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicinePrice implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String medicineName;
    private String website;
    private BigDecimal price;
    private String availability;
    private LocalDateTime lastUpdated;
}
