// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

contract PolicyRegistry {
    
    enum PolicyStatus { ACTIVE, DELETED }
    
    struct PolicyVersion {
        bytes32 policyHash;
        uint256 versionNumber;
        uint256 timestamp;
        PolicyStatus status;
    }
    
    // Mapping: subjectAddress => policyGroupId => version => PolicyVersion
    mapping(address => mapping(string => mapping(uint256 => PolicyVersion))) public policies;
    
    // Mapping: subjectAddress => policyGroupId => currentVersion
    mapping(address => mapping(string => uint256)) public currentVersion;
    
    // Events
    event PolicyRecorded(
        address indexed subject,
        string policyGroupId,
        uint256 version,
        bytes32 policyHash,
        uint256 timestamp
    );
    
    event PolicyDeleted(
        address indexed subject,
        string policyGroupId,
        uint256 version,
        uint256 timestamp
    );
    
    /**
     * Record a new policy or policy update
     */
    function recordPolicy(
        address subject,
        string memory policyGroupId,
        bytes32 policyHash
    ) public returns (uint256) {
        // Increment version (starts from 1)
        uint256 newVersion = currentVersion[subject][policyGroupId] + 1;
        
        // Create new policy version
        policies[subject][policyGroupId][newVersion] = PolicyVersion({
            policyHash: policyHash,
            versionNumber: newVersion,
            timestamp: block.timestamp,
            status: PolicyStatus.ACTIVE
        });
        
        // Update current version pointer
        currentVersion[subject][policyGroupId] = newVersion;
        
        emit PolicyRecorded(subject, policyGroupId, newVersion, policyHash, block.timestamp);
        
        return newVersion;
    }
    
    /**
     * Mark a policy as deleted (doesn't actually delete - preserves history)
     */
    function deletePolicy(
        address subject,
        string memory policyGroupId
    ) public {
        uint256 version = currentVersion[subject][policyGroupId];
        require(version > 0, "Policy does not exist");
        
        // Mark as deleted
        policies[subject][policyGroupId][version].status = PolicyStatus.DELETED;
        
        emit PolicyDeleted(subject, policyGroupId, version, block.timestamp);
    }
    
    /**
     * Get a specific policy version
     */
    function getPolicy(
        address subject,
        string memory policyGroupId,
        uint256 version
    ) public view returns (
        bytes32 policyHash,
        uint256 versionNumber,
        uint256 timestamp,
        PolicyStatus status
    ) {
        PolicyVersion memory policy = policies[subject][policyGroupId][version];
        return (
            policy.policyHash,
            policy.versionNumber,
            policy.timestamp,
            policy.status
        );
    }
    
    /**
     * Get the current version number for a policy
     */
    function getCurrentVersion(
        address subject,
        string memory policyGroupId
    ) public view returns (uint256) {
        return currentVersion[subject][policyGroupId];
    }
}