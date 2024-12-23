import React from 'react';
import { AuthContext } from '../components/AuthContext';
import {
  Box,
  Typography,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Card,
  CardContent,
  IconButton,
  Tooltip,
  Stack,
  Chip
} from '@mui/material';
import { ExpandMore, Lock } from '@mui/icons-material';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import StorageIcon from '@mui/icons-material/Storage';

const SubjectPage = () => {
  const mockData = {
    'Amazon Corp': {
      databases: {
        'Customer Database': {
          'Customer Profiles': [
            { field: 'Name', value: 'John Doe' },
            { field: 'Email', value: 'john@email.com' },
            { field: 'Phone', value: '+1234567890' }
          ],
          'Purchase History': [
            { field: 'Order ID', value: '#12345' },
            { field: 'Date', value: '2024-01-15' },
            { field: 'Total', value: '$299.99' }
          ]
        }
      }
    },
    'General Hospital': {
      databases: {
        'Patient Records': {
          'Patient Information': [
            { field: 'Patient ID', value: 'P789' },
            { field: 'Blood Type', value: 'O+' },
            { field: 'Allergies', value: 'Penicillin' }
          ],
          'Medical Records': [
            { field: 'Visit Date', value: '2024-02-01' },
            { field: 'Diagnosis', value: 'Regular Checkup' },
            { field: 'Medication', value: 'None' }
          ]
        }
      }
    }
  };

  return (
    <Box sx={{
      width: '100vw',
      minHeight: '100vh',
      backgroundImage: 'radial-gradient(ellipse at 50% 50%, hsl(210, 100%, 97%), hsl(0, 0%, 100%))',
      margin: 0,
      padding: 0
    }}>
      <Box sx={{ maxWidth: '1200px', margin: '0 auto', p: 3 }}>
        <Typography variant="h4" gutterBottom>
          My Personal Data Overview
        </Typography>
        
        {Object.entries(mockData).map(([controller, data]) => (
          <Accordion key={controller} defaultExpanded sx={{ mb: 2 }}>
            <AccordionSummary expandIcon={<ExpandMore />}>
              <Stack direction="row" spacing={2} alignItems="center">
                <AccountBalanceIcon color="primary" />
                <Typography variant="h6">{controller}</Typography>
                <Chip 
                  label="Controller"
                  size="small"
                  color="primary"
                  variant="outlined"
                />
              </Stack>
            </AccordionSummary>
            <AccordionDetails>
              {Object.entries(data.databases).map(([dbName, tables]) => (
                <Card key={dbName} sx={{ mb: 2 }}>
                  <CardContent>
                    <Stack direction="row" spacing={2} alignItems="center" mb={2}>
                      <StorageIcon color="primary" />
                      <Typography variant="h6">{dbName}</Typography>
                      <Chip 
                        label="Database"
                        size="small"
                        color="secondary"
                        variant="outlined"
                      />
                    </Stack>
                    
                    {Object.entries(tables).map(([tableName, fields]) => (
                      <Box key={tableName} sx={{ mb: 2 }}>
                        <Typography variant="subtitle1" gutterBottom>
                          {tableName}
                        </Typography>
                        <TableContainer component={Paper}>
                          <Table size="small">
                            <TableHead>
                              <TableRow>
                                <TableCell width="200px">Field</TableCell>
                                <TableCell>Value</TableCell>
                                <TableCell align="right" width="100px">Access Policy</TableCell>
                              </TableRow>
                            </TableHead>
                            <TableBody>
                              {fields.map((field, index) => (
                                <TableRow key={index}>
                                  <TableCell>{field.field}</TableCell>
                                  <TableCell>{field.value}</TableCell>
                                  <TableCell align="right">
                                    <Tooltip title="Configure Access Policy">
                                      <IconButton size="small">
                                        <Lock />
                                      </IconButton>
                                    </Tooltip>
                                  </TableCell>
                                </TableRow>
                              ))}
                            </TableBody>
                          </Table>
                        </TableContainer>
                      </Box>
                    ))}
                  </CardContent>
                </Card>
              ))}
            </AccordionDetails>
          </Accordion>
        ))}
      </Box>
    </Box>
  );
};

export default SubjectPage;