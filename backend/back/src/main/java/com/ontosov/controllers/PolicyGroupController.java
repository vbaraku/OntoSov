package com.ontosov.controllers;

import com.ontosov.dto.PolicyAssignmentDTO;
import com.ontosov.dto.PolicyGroupDTO;
import com.ontosov.dto.PolicyStatusDTO;
// No need for these imports

import com.ontosov.services.ODRLService;
import com.ontosov.services.PolicyGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/policy-groups")
public class PolicyGroupController {

    @Autowired
    private PolicyGroupService policyGroupService;

    @Autowired
    private ODRLService odrlService;

    @GetMapping("/{subjectId}")
    public ResponseEntity<List<PolicyGroupDTO>> getPolicyGroups(@PathVariable Long subjectId) {
        try {
            List<PolicyGroupDTO> groups = policyGroupService.getPolicyGroupsBySubject(subjectId);
            return ResponseEntity.ok(groups);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<String> createPolicyGroup(
            @RequestBody PolicyGroupDTO policyGroupDTO,
            @RequestParam Long subjectId) {
        try {
            String groupId = policyGroupService.createPolicyGroup(policyGroupDTO, subjectId);
            return ResponseEntity.ok(groupId);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{groupId}")
    public ResponseEntity<Void> updatePolicyGroup(
            @PathVariable String groupId,
            @RequestBody PolicyGroupDTO policyGroupDTO,
            @RequestParam Long subjectId) {
        try {
            policyGroupService.updatePolicyGroup(groupId, policyGroupDTO, subjectId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deletePolicyGroup(
            @PathVariable String groupId,
            @RequestParam Long subjectId) {
        try {
            policyGroupService.deletePolicyGroup(groupId, subjectId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{groupId}/assign")
    public ResponseEntity<Void> assignDataToPolicy(
            @PathVariable String groupId,
            @RequestBody PolicyAssignmentDTO assignmentDTO,
            @RequestParam Long subjectId) {
        try {
            // Get the policy group details
            List<PolicyGroupDTO> groups = policyGroupService.getPolicyGroupsBySubject(subjectId);
            PolicyGroupDTO policyGroup = groups.stream()
                    .filter(g -> g.getId().equals(groupId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Policy group not found"));

            // Assign data to policy and generate ODRL policies
            policyGroupService.assignDataToPolicy(groupId, assignmentDTO, subjectId);
            odrlService.generatePoliciesFromAssignment(groupId, policyGroup, assignmentDTO, subjectId);

            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{groupId}/assignments")
    public ResponseEntity<Map<String, Set<String>>> getPolicyGroupAssignments(
            @PathVariable String groupId,
            @RequestParam Long subjectId) {
        try {
            Map<String, Set<String>> assignments = odrlService.getAssignmentsForPolicyGroup(groupId, subjectId);
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            e.printStackTrace();  // Log the error for debugging
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/status/{subjectId}")
    public ResponseEntity<PolicyStatusDTO> getPolicyStatus(@PathVariable Long subjectId) {
        try {
            PolicyStatusDTO status = new PolicyStatusDTO();
            status.setAssignedPolicies(odrlService.getSubjectPolicies(subjectId));
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}