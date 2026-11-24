package com.kovanlabs.ai_medicine_assistant.repository;

import com.kovanlabs.ai_medicine_assistant.model.MedicineMaster;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicineMasterRepository extends JpaRepository<MedicineMaster, Long> {

    Optional<MedicineMaster> findByNormalizedBrandNameAndStrengthAndNormalizedManufacturer(
            String normalizedBrandName, String strength, String normalizedManufacturer);

    @Query("SELECT m FROM MedicineMaster m WHERE m.normalizedBrandName LIKE :prefix% OR m.normalizedBrandName LIKE %:term% OR REPLACE(m.normalizedBrandName, ' ', '') LIKE %:compactTerm%")
    List<MedicineMaster> findCandidatesByPrefixOrTerm(@Param("prefix") String prefix, @Param("term") String term, @Param("compactTerm") String compactTerm, Pageable pageable);

    @Query("SELECT m FROM MedicineMaster m WHERE m.normalizedGenericName = :genericName OR m.normalizedGenericName LIKE %:genericName% OR REPLACE(m.normalizedGenericName, ' ', '') LIKE %:compactGeneric%")
    List<MedicineMaster> findByNormalizedGenericName(@Param("genericName") String genericName, @Param("compactGeneric") String compactGeneric);
}
