import React, { useState } from "react";
import {
  Button,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  CircularProgress,
} from "@mui/material";
import {
  Download as DownloadIcon,
  Description as JsonIcon,
  TableChart as CsvIcon,
} from "@mui/icons-material";

const DataExport = ({ data, user, policyStatus, policyGroups }) => {
  const [anchorEl, setAnchorEl] = useState(null);
  const [exporting, setExporting] = useState(false);
  const open = Boolean(anchorEl);

  const handleClick = (event) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const flattenData = () => {
    const flattened = [];

    Object.entries(data).forEach(([source, sourceData]) => {
      // Handle Person data
      if (sourceData.Person) {
        Object.entries(sourceData.Person).forEach(([property, value]) => {
          // Check if property is protected by iterating through policyStatus
          let isProtected = false;
          const policyNames = [];

          for (const groupId in policyStatus) {
            const sourceMap = policyStatus[groupId];
            if (sourceMap && sourceMap[source] && sourceMap[source][property]) {
              const hasActions = Object.keys(sourceMap[source][property]).some(
                (key) => !key.startsWith("__")
              );
              if (hasActions) {
                isProtected = true;
                const normalizedGroupId = groupId.includes("#")
                  ? groupId.split("#").pop()
                  : groupId;
                policyNames.push(normalizedGroupId);
              }
            }
          }

          flattened.push({
            Source: source,
            Type: "Personal Information",
            Category: "Person",
            Property: property,
            Value: value,
            Protected: isProtected ? "Yes" : "No",
            Policies: policyNames.join(", ") || "None",
          });
        });
      }

      // Handle entity data
      Object.entries(sourceData).forEach(([entityType, entityArray]) => {
        if (entityType === "Person" || !Array.isArray(entityArray)) return;

        entityArray.forEach((entity, index) => {
          // Check if entity is protected by iterating through policyStatus
          let isProtected = false;
          const policyNames = [];

          for (const groupId in policyStatus) {
            const sourceMap = policyStatus[groupId];
            if (
              sourceMap &&
              sourceMap[source] &&
              sourceMap[source][entity.entityId]
            ) {
              const hasActions = Object.keys(
                sourceMap[source][entity.entityId]
              ).some((key) => !key.startsWith("__"));
              if (hasActions) {
                isProtected = true;
                const normalizedGroupId = groupId.includes("#")
                  ? groupId.split("#").pop()
                  : groupId;
                policyNames.push(normalizedGroupId);
              }
            }
          }

          Object.entries(entity.properties).forEach(([property, value]) => {
            flattened.push({
              Source: source,
              Type: "Transactional Data",
              Category: `${entityType} #${index + 1}`,
              Property: property,
              Value: value,
              Protected: isProtected ? "Yes" : "No",
              Policies: policyNames.join(", ") || "None",
            });
          });
        });
      });
    });

    return flattened;
  };

  const exportToJSON = () => {
    setExporting(true);
    handleClose();

    try {
      const flattenedData = flattenData();

      // Organize data by source for better structure
      const dataBySource = {};
      Object.entries(data).forEach(([source, sourceData]) => {
        const sourceInfo = {
          personalInformation: {},
          transactionalData: [],
        };

        // Add Person data
        if (sourceData.Person) {
          Object.entries(sourceData.Person).forEach(([property, value]) => {
            // Check if property is protected by iterating through policyStatus
            let isProtected = false;
            const policyNames = [];

            for (const groupId in policyStatus) {
              const sourceMap = policyStatus[groupId];
              if (
                sourceMap &&
                sourceMap[source] &&
                sourceMap[source][property]
              ) {
                // Check if it has actual actions (not just metadata)
                const hasActions = Object.keys(
                  sourceMap[source][property]
                ).some((key) => !key.startsWith("__"));
                if (hasActions) {
                  isProtected = true;
                  const normalizedGroupId = groupId.includes("#")
                    ? groupId.split("#").pop()
                    : groupId;
                  policyNames.push(normalizedGroupId);
                }
              }
            }

            sourceInfo.personalInformation[property] = {
              value: value,
              protected: isProtected,
              policies: policyNames,
            };
          });
        }

        // Add entity data
        Object.entries(sourceData).forEach(([entityType, entityArray]) => {
          if (entityType === "Person" || !Array.isArray(entityArray)) return;

          entityArray.forEach((entity, index) => {
            // Check if entity is protected by iterating through policyStatus
            let isProtected = false;
            const policyNames = [];

            for (const groupId in policyStatus) {
              const sourceMap = policyStatus[groupId];
              if (
                sourceMap &&
                sourceMap[source] &&
                sourceMap[source][entity.entityId]
              ) {
                // Check if it has actual actions (not just metadata)
                const hasActions = Object.keys(
                  sourceMap[source][entity.entityId]
                ).some((key) => !key.startsWith("__"));
                if (hasActions) {
                  isProtected = true;
                  const normalizedGroupId = groupId.includes("#")
                    ? groupId.split("#").pop()
                    : groupId;
                  policyNames.push(normalizedGroupId);
                }
              }
            }

            sourceInfo.transactionalData.push({
              type: entityType,
              recordNumber: index + 1,
              properties: entity.properties,
              protected: isProtected,
              policies: policyNames,
            });
          });
        });

        dataBySource[source] = sourceInfo;
      });

      // Create comprehensive export data
      const exportData = {
        exportInfo: {
          exportDate: new Date().toISOString(),
          exportedBy: user?.email || "Unknown",
          format: "OntoSov Personal Data Export v1.0",
        },
        subject: {
          name: user?.name || "Unknown",
          email: user?.email || "Unknown",
          taxId: user?.taxid || "Unknown",
        },
        summary: {
          totalDataSources: Object.keys(data).length,
          totalDataItems: flattenedData.length,
          protectedItems: flattenedData.filter(
            (item) => item.Protected === "Yes"
          ).length,
          unprotectedItems: flattenedData.filter(
            (item) => item.Protected === "No"
          ).length,
          protectionPercentage:
            flattenedData.length > 0
              ? Math.round(
                  (flattenedData.filter((item) => item.Protected === "Yes")
                    .length /
                    flattenedData.length) *
                    100
                )
              : 0,
        },
        dataSources: dataBySource,
        policyGroups: policyGroups
          ? policyGroups.map((group) => {
              // Get permissions (actions that are allowed)
              const permissions = [];
              const prohibitions = [];

              if (group.permissions) {
                Object.entries(group.permissions).forEach(
                  ([action, isAllowed]) => {
                    if (isAllowed) {
                      permissions.push(
                        action.charAt(0).toUpperCase() + action.slice(1)
                      );
                    }
                  }
                );
              }

              // Get prohibitions (actions explicitly denied)
              if (group.prohibitions) {
                Object.entries(group.prohibitions).forEach(
                  ([action, isDenied]) => {
                    if (isDenied) {
                      prohibitions.push(
                        action.charAt(0).toUpperCase() + action.slice(1)
                      );
                    }
                  }
                );
              }

              return {
                id: group.id,
                name: group.name,
                description: group.description || "No description",
                permissions: permissions,
                prohibitions: prohibitions,
                constraints: {
                  purpose: group.constraints?.purpose || "None specified",
                  expiration: group.constraints?.expiration || "No expiration",
                  requiresNotification:
                    group.constraints?.requiresNotification || false,
                },
                consequences: group.consequences
                  ? {
                      notificationType:
                        group.consequences.notificationType || "None",
                      compensationAmount: group.consequences.compensationAmount
                        ? `€${group.consequences.compensationAmount}`
                        : "None",
                    }
                  : {
                      notificationType: "None",
                      compensationAmount: "None",
                    },
                aiRestrictions: group.aiRestrictions
                  ? {
                      allowTraining:
                        group.aiRestrictions.allowAiTraining !== false,
                      algorithm:
                        group.aiRestrictions.aiAlgorithm ||
                        "No specific algorithm restriction",
                    }
                  : {
                      allowTraining: true,
                      algorithm: "No restrictions",
                    },
              };
            })
          : [],
      };

      const jsonString = JSON.stringify(exportData, null, 2);
      const blob = new Blob([jsonString], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `my-data-export-${
        new Date().toISOString().split("T")[0]
      }.json`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error("Error exporting JSON:", error);
      alert("Failed to export data. Please try again.");
    } finally {
      setExporting(false);
    }
  };

  const exportToCSV = () => {
    setExporting(true);
    handleClose();

    try {
      const flattenedData = flattenData();

      if (flattenedData.length === 0) {
        alert("No data to export");
        setExporting(false);
        return;
      }

      // Create CSV header
      const headers = Object.keys(flattenedData[0]);
      const csvRows = [headers.join(",")];

      // Add data rows
      flattenedData.forEach((row) => {
        const values = headers.map((header) => {
          const value = row[header]?.toString() || "";
          // Escape quotes and wrap in quotes if contains comma, quote, or newline
          if (
            value.includes(",") ||
            value.includes('"') ||
            value.includes("\n")
          ) {
            return `"${value.replace(/"/g, '""')}"`;
          }
          return value;
        });
        csvRows.push(values.join(","));
      });

      const csvString = csvRows.join("\n");
      const blob = new Blob([csvString], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `my-data-export-${
        new Date().toISOString().split("T")[0]
      }.csv`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error("Error exporting CSV:", error);
      alert("Failed to export data. Please try again.");
    } finally {
      setExporting(false);
    }
  };

  return (
    <>
      <Button
        variant="outlined"
        startIcon={
          exporting ? <CircularProgress size={20} /> : <DownloadIcon />
        }
        onClick={handleClick}
        disabled={exporting || Object.keys(data).length === 0}
      >
        {exporting ? "Exporting..." : "Export My Data"}
      </Button>
      <Menu
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "right",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: "right",
        }}
      >
        <MenuItem onClick={exportToJSON}>
          <ListItemIcon>
            <JsonIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText
            primary="Export as JSON"
            secondary="Complete data with metadata"
          />
        </MenuItem>
        <MenuItem onClick={exportToCSV}>
          <ListItemIcon>
            <CsvIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText
            primary="Export as CSV"
            secondary="Tabular format for spreadsheets"
          />
        </MenuItem>
      </Menu>
    </>
  );
};

export default DataExport;
