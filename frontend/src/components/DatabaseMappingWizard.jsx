import React, { useState, useEffect } from "react";
import { Box, Stepper, Step, StepLabel, Button } from "@mui/material";
import DatabaseConnectionForm from "./DatabaseConnectionForm";
import SchemaMapper from "./SchemaMapper";

const steps = ["Connect Database", "Map Schema"];

const DatabaseMappingWizard = ({ onComplete, controllerId, database = null }) => {
  const [activeStep, setActiveStep] = useState(database ? 1 : 0);
  const [dbConfig, setDbConfig] = useState(database);
  const [tables, setTables] = useState([]);

  const fetchTables = async (config) => {
    try {
      console.log("Fetching tables for config:", config);
      const tablesResponse = await fetch(
        "http://localhost:8080/api/database/tables",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(config),
        }
      );

      if (!tablesResponse.ok) {
        throw new Error(`Failed to fetch tables: ${await tablesResponse.text()}`);
      }

      const tablesData = await tablesResponse.json();
      setTables(tablesData);
    } catch (error) {
      console.error("Error fetching tables:", error);
    }
  };

  const handleDatabaseConnect = async (config) => {
    try {
      console.log("Saving database config:", config);
      const saveResponse = await fetch(
        `http://localhost:8080/api/database/save-config?controllerId=${controllerId}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(config),
        }
      );

      if (!saveResponse.ok) throw new Error("Failed to save database config");

      // Update the dbConfig state with the saved configuration
      setDbConfig(config);
      
      // Fetch tables
      await fetchTables(config);
      
      // Move to next step
      setActiveStep(1);
    } catch (error) {
      console.error("Error saving database:", error);
    }
  };

  const handleMappingComplete = () => {
    console.log("Mapping complete");
    onComplete?.(); // Call onComplete only if it exists
  };

  const handleBack = () => {
    setActiveStep(0);
  };

  const renderStepContent = (step) => {
    switch (step) {
      case 0:
        return (
          <DatabaseConnectionForm 
            onSubmit={handleDatabaseConnect} // Only save when form is submitted
            controllerId={controllerId}
            initialData={database}
            isEditMode={!!database}
          />
        );
      case 1:
        return (
          <SchemaMapper
            tables={tables}
            dbConfig={dbConfig}
            onComplete={handleMappingComplete}
            controllerId={controllerId}
            isEditMode={!!database}
          />
        );
      default:
        return null;
    }
  };

  return (
    <Box
      sx={{
        minHeight: "600px",
        width: "800px",
        margin: "0 auto",
        p: 3,
        display: "flex",
        flexDirection: "column",
      }}
    >
      <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
        {steps.map((label) => (
          <Step key={label}>
            <StepLabel>{label}</StepLabel>
          </Step>
        ))}
      </Stepper>

      <Box sx={{ flex: 1 }}>{renderStepContent(activeStep)}</Box>

      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          mt: "auto",
          pt: 2,
        }}
      >
        {activeStep === 1 && <Button onClick={handleBack}>Back</Button>}
        <Button onClick={handleMappingComplete} color="inherit" sx={{ ml: "auto" }}>
          Cancel
        </Button>
      </Box>
    </Box>
  );
};

export default DatabaseMappingWizard;
