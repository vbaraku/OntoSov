package com.ontosov.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessRequestDTO {
    private Long controllerId;
    private String subjectTaxId;
    private String action;              // "read", "use", "share", "aggregate", "modify"
    private String purpose;             // "Medical Treatment", "Research", "Marketing", etc.
    private String dataDescription;     // Brief description of what data
}