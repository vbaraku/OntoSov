import React, { useState } from 'react';
import {
  Box,
  Stepper,
  Step,
  StepLabel,
  Typography,
  Paper,
  Container,
  Button
} from '@mui/material';
import DatabaseConnectionForm from './DatabaseConnectionForm';
import SchemaMapper from './SchemaMapper';

const steps = ['Connect Database', 'Map Schema'];

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

  const handleDatabaseConnect = (config) => {
    setDbConfig(config);
    // In a real application, you would fetch tables here
    // For now, we'll use dummy data
    setTables([
      {
        name: 'users',
        columns: ['id', 'name', 'email', 'phone']
      },
      {
        name: 'products',
        columns: ['id', 'name', 'price', 'category']
      }
    ]);
    handleNext();
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
    <Container maxWidth="lg">
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
          
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 2 }}>
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