import React, { useState } from "react";
import {
  Box,
  Stepper,
  Step,
  StepLabel,
  Typography,
  Paper,
  Container,
  Button,
} from "@mui/material";
import DatabaseConnectionForm from "./DatabaseConnectionForm";
import SchemaMapper from "./SchemaMapper";

const steps = ["Connect Database", "Map Schema"];

const DatabaseMappingWizard = () => {
  const [activeStep, setActiveStep] = useState(0);
  const [dbConfig, setDbConfig] = useState(null);
  const [tables, setTables] = useState([]);

  const handleNext = () => {
    setActiveStep((prevStep) => prevStep + 1);
  };

  const handleBack = () => {
    setActiveStep((prevStep) => prevStep - 1);
  };

  const handleDatabaseConnect = async (config) => {
    try {
      const response = await fetch(
        "http://localhost:8080/api/database/tables",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(config),
        }
      );

      if (!response.ok) throw new Error("Failed to fetch tables");

      const tablesData = await response.json();
      setDbConfig(config);
      setTables(tablesData);
      handleNext();
    } catch (error) {
      console.error("Error fetching tables:", error);
      // Handle error appropriately
    }
  };

  const renderStepContent = (step) => {
    switch (step) {
      case 0:
        return <DatabaseConnectionForm onSubmit={handleDatabaseConnect} />;
      case 1:
        return <SchemaMapper tables={tables} />;
      default:
        return null;
    }
  };

  return (
    <Container
      maxWidth="lg"
      sx={{
        minHeight: "100vh",
        background:
          "radial-gradient(ellipse at 50% 50%, hsl(210, 100%, 97%), hsl(0, 0%, 100%))",
        py: 4,
      }}
    >
      <Paper elevation={3} sx={{ p: 4, mt: 4 }}>
        <Typography variant="h4" align="center" gutterBottom>
          Database Configuration and Mapping
        </Typography>

        <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
          {steps.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>

        <Box>
          {renderStepContent(activeStep)}

          <Box sx={{ display: "flex", justifyContent: "flex-end", mt: 2 }}>
            {activeStep !== 0 && (
              <Button onClick={handleBack} sx={{ mr: 1 }}>
                Back
              </Button>
            )}
          </Box>
        </Box>
      </Paper>
    </Container>
  );
};

export default DatabaseMappingWizard;
