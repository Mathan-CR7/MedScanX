package com.kovanlabs.ai_medicine_assistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MedicineSideEffectResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String medicineName;
    private String genericName;
    private String strength;
    private String manufacturer;
    private String whatItDoes;
    private String avoidIf;
    private String dosageGuidelines;
    private String storageInstructions;
    private List<String> commonSideEffects;
    private List<String> lessCommonSideEffects;
    private List<String> seriousWarnings;
    private String disclaimer;

    @com.fasterxml.jackson.annotation.JsonSetter("avoid_if")
    public void setAvoidIf(Object avoidIfObj) {
        this.avoidIf = parseFlexibleString(avoidIfObj);
    }

    @com.fasterxml.jackson.annotation.JsonSetter("storage_instructions")
    public void setStorageInstructions(Object storageObj) {
        this.storageInstructions = parseFlexibleString(storageObj);
    }

    private String parseFlexibleString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item != null) {
                    if (!sb.isEmpty()) sb.append(" ");
                    sb.append("• ").append(item.toString());
                }
            }
            return sb.toString();
        }
        return obj.toString();
    }
}
