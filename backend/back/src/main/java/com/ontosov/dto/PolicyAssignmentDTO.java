package com.ontosov.dto;

import lombok.Data;

import java.util.Map;
import java.util.Set;

@Data
public class PolicyAssignmentDTO {
    private String policyGroupId;
    private Map<String, Set<String>> dataAssignments; // Map of data source -> set of properties
    private Map<String, Set<String>> propertyAssignments; // source -> properties
    private Map<String, Set<String>> entityAssignments;   // source -> entityIds

    @Override
    public String toString() {
        return "PolicyAssignmentDTO{" +
                "propertyAssignments=" + propertyAssignments +
                ", entityAssignments=" + entityAssignments +
                ", dataAssignments=" + dataAssignments +
                '}';
    }

}
