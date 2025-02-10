import React, { useState } from 'react';
import {
  Box,
  Stepper,
  Step,
  StepLabel,
  Card,
  Typography,
  FormControl,
  FormControlLabel,
  Checkbox,
  TextField,
  Button,
  Tooltip,
  IconButton
} from '@mui/material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';

const tooltips = {
  read: "Basic access to view the data (e.g., viewing your profile information)",
  use: "Using data for operations (e.g., processing transactions, providing services)",
  share: "Sharing data with authorized third parties (e.g., sharing medical data with specialists)",
  aggregate: "Using data for anonymous statistics and analytics",
  modify: "Making updates or changes to the data",
  notification: "Receive notifications when your data is accessed according to allowed permissions"
};

const steps = ['Select Data', 'Set Permissions', 'Add Constraints'];

const PolicyCreator = ({ data, onPolicyCreate }) => {
  const [activeStep, setActiveStep] = useState(0);
  const [selectedData, setSelectedData] = useState({});
  const [permissions, setPermissions] = useState({
    read: false,
    use: false,
    share: false,
    aggregate: false,
    modify: false
  });
  const [purpose, setPurpose] = useState('');
  const [expiration, setExpiration] = useState(null);
  const [notification, setNotification] = useState(false);

  const handleNext = () => {
    setActiveStep((prev) => prev + 1);
  };

  const handleBack = () => {
    setActiveStep((prev) => prev - 1);
  };

  const handleDataSelect = (source, property) => {
    setSelectedData(prev => ({
      ...prev,
      [source]: {
        ...(prev[source] || {}),
        [property]: !(prev[source]?.[property] || false)
      }
    }));
  };

  const handleSubmit = () => {
    const policy = {
      target: selectedData,
      permissions,
      constraints: {
        purpose,
        expiration: expiration?.toISOString(),
        requiresNotification: notification
      }
    };
    onPolicyCreate(policy);
  };

  const renderPermissionWithTooltip = (permission, label) => (
    <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
      <FormControlLabel
        control={
          <Checkbox
            checked={permissions[permission]}
            onChange={(e) => setPermissions(prev => ({ ...prev, [permission]: e.target.checked }))}
          />
        }
        label={`Allow ${label}`}
      />
      <Tooltip title={tooltips[permission]} arrow>
        <IconButton size="small">
          <HelpOutlineIcon fontSize="small" />
        </IconButton>
      </Tooltip>
    </Box>
  );

  const renderStepContent = (step) => {
    switch (step) {
      case 0:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Select data to protect
            </Typography>
            {Object.entries(data).map(([source, properties]) => (
              <Card key={source} sx={{ p: 2, mb: 2 }}>
                <Typography variant="subtitle1" gutterBottom>
                  {source}
                </Typography>
                {Object.entries(properties).map(([property, value]) => (
                  <FormControlLabel
                    key={property}
                    control={
                      <Checkbox
                        checked={selectedData[source]?.[property] || false}
                        onChange={() => handleDataSelect(source, property)}
                      />
                    }
                    label={`${property}: ${value}`}
                  />
                ))}
              </Card>
            ))}
          </Box>
        );

      case 1:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Set permissions
            </Typography>
            {renderPermissionWithTooltip('read', 'Read')}
            {renderPermissionWithTooltip('use', 'Use')}
            {renderPermissionWithTooltip('share', 'Share')}
            {renderPermissionWithTooltip('aggregate', 'Aggregate')}
            {renderPermissionWithTooltip('modify', 'Modify')}
          </Box>
        );

      case 2:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Add constraints
            </Typography>
            <FormControl fullWidth sx={{ mb: 2 }}>
              <TextField
                label="Purpose"
                value={purpose}
                onChange={(e) => setPurpose(e.target.value)}
                placeholder="e.g., Marketing, Research, Service Provision"
              />
            </FormControl>
            <FormControl fullWidth sx={{ mb: 2 }}>
              <DatePicker
                label="Expiration Date"
                value={expiration}
                onChange={(date) => setExpiration(date)}
                slotProps={{
                  textField: {
                    helperText: 'Leave empty for no expiration'
                  }
                }}
              />
            </FormControl>
            <Box sx={{ display: 'flex', alignItems: 'center' }}>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={notification}
                    onChange={(e) => setNotification(e.target.checked)}
                  />
                }
                label="Notify me when data is accessed"
              />
              <Tooltip title={tooltips.notification} arrow>
                <IconButton size="small">
                  <HelpOutlineIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Box>
          </Box>
        );

      default:
        return null;
    }
  };

  return (
    <Box sx={{ width: '100%', maxWidth: 600, mx: 'auto', p: 3 }}>
      <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
        {steps.map((label) => (
          <Step key={label}>
            <StepLabel>{label}</StepLabel>
          </Step>
        ))}
      </Stepper>

      {renderStepContent(activeStep)}

      <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 4 }}>
        <Button
          onClick={handleBack}
          disabled={activeStep === 0}
        >
          Back
        </Button>
        {activeStep === steps.length - 1 ? (
          <Button
            variant="contained"
            onClick={handleSubmit}
          >
            Create Policy
          </Button>
        ) : (
          <Button
            variant="contained"
            onClick={handleNext}
          >
            Next
          </Button>
        )}
      </Box>
    </Box>
  );
};

export default PolicyCreator;