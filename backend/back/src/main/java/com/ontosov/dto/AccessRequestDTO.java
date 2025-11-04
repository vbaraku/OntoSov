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
    private String action;              // "read", "use", "share", "aggregate", "modify", "aiTraining"
    private String purpose;             // "Service Provision", "Research", etc. (not used for aiTraining)
    private String dataDescription;     // Brief description of what data
    private String dataSource;          // database ID from getDatabasesForController
    private String tableName;           // NEW: table name
    private String dataProperty;        // column name from getDatabaseTables
    private String aiAlgorithm;         // AI algorithm for aiTraining action (optional)
}