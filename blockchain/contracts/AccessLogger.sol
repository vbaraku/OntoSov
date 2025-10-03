// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

contract AccessLogger {
    
    struct AccessLogEntry {
        address controller;
        address subject;
        bytes32 purposeHash;      // Hashed for privacy
        string action;
        bool permitted;
        string policyGroupId;
        uint256 policyVersion;
        uint256 timestamp;
    }
    
    // Array of all access logs
    AccessLogEntry[] public logs;
    
    // Mappings for efficient querying
    mapping(address => uint256[]) public logsByController;
    mapping(address => uint256[]) public logsBySubject;
    
    // Events
    event AccessLogged(
        uint256 indexed logIndex,
        address indexed controller,
        address indexed subject,
        string action,
        bool permitted,
        uint256 timestamp
    );
    
    /**
     * Log an access attempt
     */
    function logAccess(
        address controller,
        address subject,
        bytes32 purposeHash,
        string memory action,
        bool permitted,
        string memory policyGroupId,
        uint256 policyVersion
    ) public returns (uint256) {
        // Create new log entry
        AccessLogEntry memory newLog = AccessLogEntry({
            controller: controller,
            subject: subject,
            purposeHash: purposeHash,
            action: action,
            permitted: permitted,
            policyGroupId: policyGroupId,
            policyVersion: policyVersion,
            timestamp: block.timestamp
        });
        
        // Add to logs array
        logs.push(newLog);
        uint256 logIndex = logs.length - 1;
        
        // Update mappings for efficient querying
        logsByController[controller].push(logIndex);
        logsBySubject[subject].push(logIndex);
        
        emit AccessLogged(logIndex, controller, subject, action, permitted, block.timestamp);
        
        return logIndex;
    }
    
    /**
     * Get a specific log entry
     */
    function getLog(uint256 index) public view returns (
        address controller,
        address subject,
        bytes32 purposeHash,
        string memory action,
        bool permitted,
        string memory policyGroupId,
        uint256 policyVersion,
        uint256 timestamp
    ) {
        require(index < logs.length, "Log does not exist");
        AccessLogEntry memory log = logs[index];
        return (
            log.controller,
            log.subject,
            log.purposeHash,
            log.action,
            log.permitted,
            log.policyGroupId,
            log.policyVersion,
            log.timestamp
        );
    }
    
    /**
     * Get total number of logs
     */
    function getLogCount() public view returns (uint256) {
        return logs.length;
    }
    
    /**
     * Get all log indices for a controller
     */
    function getControllerLogs(address controller) public view returns (uint256[] memory) {
        return logsByController[controller];
    }
    
    /**
     * Get all log indices for a subject
     */
    function getSubjectLogs(address subject) public view returns (uint256[] memory) {
        return logsBySubject[subject];
    }
}