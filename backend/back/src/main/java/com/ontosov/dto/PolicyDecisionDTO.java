package com.ontosov.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDecisionDTO {
    private DecisionResult result;       // PERMIT or DENY
    private String reason;               // Human-readable explanation
    private String policyGroupId;        // Which policy was evaluated
    private Integer policyVersion;       // Version of the policy
    private List<ObligationDTO> obligations = new ArrayList<>();
}

