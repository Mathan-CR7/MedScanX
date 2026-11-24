package com.kovanlabs.ai_medicine_assistant.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "medicine_master",
    indexes = {
        @Index(name = "idx_norm_brand", columnList = "normalized_brand_name")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_brand_strength_mfg", columnNames = {"normalized_brand_name", "strength", "normalized_manufacturer"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicineMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "normalized_brand_name", nullable = false)
    private String normalizedBrandName;

    @Column(name = "generic_name", length = 1000)
    private String genericName;

    @Column(name = "normalized_generic_name", length = 1000)
    private String normalizedGenericName;

    @Column(name = "strength", nullable = false)
    private String strength;

    @Column(name = "dosage_form")
    private String dosageForm;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "normalized_manufacturer", nullable = false)
    private String normalizedManufacturer;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "source_reference", nullable = false)
    private String sourceReference;

    @Column(name = "verified")
    private Boolean verified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.verified == null) {
            this.verified = true;
        }
        if (this.strength == null) {
            this.strength = "";
        }
        if (this.normalizedManufacturer == null) {
            this.normalizedManufacturer = "";
        }
        if (this.manufacturer == null) {
            this.manufacturer = "";
        }
        if (this.source == null) {
            this.source = "Unknown";
        }
        if (this.sourceReference == null) {
            this.sourceReference = "";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.strength == null) {
            this.strength = "";
        }
        if (this.normalizedManufacturer == null) {
            this.normalizedManufacturer = "";
        }
    }
}
