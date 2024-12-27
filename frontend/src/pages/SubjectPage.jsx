import React, { useState, useEffect, useContext } from 'react';
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
  Chip,
  CircularProgress,
  Alert
} from '@mui/material';
import { ExpandMore, Lock } from '@mui/icons-material';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import StorageIcon from '@mui/icons-material/Storage';

const SubjectPage = () => {
  const { user } = useContext(AuthContext);
  const [data, setData] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  console.log(user);
  console.log("here");

  useEffect(() => {
    const fetchData = async () => {
      if (!user?.taxid) return;
      
      try {
        const response = await fetch(
          `http://localhost:8080/api/ontop/person/${user.taxid}/controller/8`
        );
        
        if (!response.ok) {
          throw new Error('Failed to fetch data');
        }
        
        const result = await response.json();
        setData(result);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [user]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">Error loading data: {error}</Alert>
      </Box>
    );
  }

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
        
        {Object.entries(data).map(([source, properties]) => (
          <Accordion key={source} defaultExpanded sx={{ mb: 2 }}>
            <AccordionSummary expandIcon={<ExpandMore />}>
              <Stack direction="row" spacing={2} alignItems="center">
                <AccountBalanceIcon color="primary" />
                <Typography variant="h6">{source}</Typography>
                <Chip 
                  label="Data Source"
                  size="small"
                  color="primary"
                  variant="outlined"
                />
              </Stack>
            </AccordionSummary>
            <AccordionDetails>
              <Card>
                <CardContent>
                  <TableContainer component={Paper}>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell width="200px">Property</TableCell>
                          <TableCell>Value</TableCell>
                          <TableCell align="right" width="100px">Access Policy</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {Object.entries(properties).map(([property, value]) => (
                          <TableRow key={property}>
                            <TableCell>{property}</TableCell>
                            <TableCell>{value}</TableCell>
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
                </CardContent>
              </Card>
            </AccordionDetails>
          </Accordion>
        ))}
      </Box>
    </Box>
  );
};

export default SubjectPage;