import React, { useState } from "react";
import { Box, Stepper, Step, StepLabel, Button } from "@mui/material";
import DatabaseConnectionForm from "./DatabaseConnectionForm";
import SchemaMapper from "./SchemaMapper";

const steps = ["Connect Database", "Map Schema"];

const DatabaseMappingWizard = ({ onComplete }) => {
  const [activeStep, setActiveStep] = useState(0);
  const [dbConfig, setDbConfig] = useState(null);
  const [tables, setTables] = useState([]);

  const handleDatabaseConnect = async (config) => {
    try {
      const saveResponse = await fetch(
        "http://localhost:8080/api/database/save-config",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(config),
        }
      );

      if (!saveResponse.ok) throw new Error("Failed to save database config");

      const tablesResponse = await fetch(
        "http://localhost:8080/api/database/tables",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(config),
        }
      );

      if (!tablesResponse.ok) throw new Error("Failed to fetch tables");

      const tablesData = await tablesResponse.json();
      setDbConfig(config);
      setTables(tablesData);
      setActiveStep(1);
    } catch (error) {
      console.error("Error:", error);
    }
  };

  const handleMappingComplete = () => {
    onComplete();
  };

  const handleBack = () => {
    setActiveStep(0);
  };

  const renderStepContent = (step) => {
    switch (step) {
      case 0:
        return <DatabaseConnectionForm onSubmit={handleDatabaseConnect} />;
      case 1:
        return (
          <SchemaMapper
            tables={tables}
            dbConfig={dbConfig}
            onComplete={handleMappingComplete}
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
        <Button onClick={onComplete} color="inherit" sx={{ ml: "auto" }}>
          Cancel
        </Button>
      </Box>
    </Box>
  );
};

export default DatabaseMappingWizard;
