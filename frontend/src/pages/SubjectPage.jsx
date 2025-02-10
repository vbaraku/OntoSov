import React, { useState, useEffect, useContext } from 'react';
import { AuthContext } from '../components/AuthContext';
import PolicyCreator from '../components/PolicyCreator';
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
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  Button
} from '@mui/material';
import { ExpandMore, Lock, Security } from '@mui/icons-material';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';

const SubjectPage = () => {
  const { user } = useContext(AuthContext);
  const [data, setData] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [policyDialogOpen, setPolicyDialogOpen] = useState(false);
  const [controllers, setControllers] = useState([]);
  const [policies, setPolicies] = useState([]);

  useEffect(() => {
    fetchData();
    fetchPolicies();
  }, [user]);

  const fetchData = async () => {
    if (!user?.taxid) return;
    
    try {
      const availableControllers = await fetch('http://localhost:8080/api/controllers')
        .then(res => res.json());
      setControllers(availableControllers);
      
      const controllerData = await Promise.all(
        availableControllers.map(controller => 
          fetch(`http://localhost:8080/api/ontop/person/${user.taxid}/controller/${controller.id}`)
            .then(res => res.json())
            .catch(err => null)
        )
      );

      const combinedData = controllerData.reduce((acc, data, index) => {
        if (data) {
          Object.entries(data).forEach(([source, properties]) => {
            acc[`${availableControllers[index].name} - ${source}`] = properties;
          });
        }
        return acc;
      }, {});

      setData(combinedData);
      setLoading(false);
    } catch (err) {
      setError(err.message);
      setLoading(false);
    }
  };

  const fetchPolicies = async () => {
    // if (!user?.id) return;
    // try {
    //   const response = await fetch(`http://localhost:8080/api/policies/${user.id}`);
    //   const policies = await response.json();
    //   setPolicies(policies);
    // } catch (error) {
    //   console.error('Error fetching policies:', error);
    // }
  };

  const handleCreatePolicy = async (policy) => {
    try {
      const response = await fetch(`http://localhost:8080/api/policies`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          subjectId: user.id,
          ...policy
        }),
      });

      if (response.ok) {
        await fetchPolicies();
        setPolicyDialogOpen(false);
      }
    } catch (error) {
      console.error('Error creating policy:', error);
    }
  };

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
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
          <Typography variant="h4">My Personal Data Overview</Typography>
          <Button
            variant="contained"
            startIcon={<Security />}
            onClick={() => setPolicyDialogOpen(true)}
          >
            Create New Policy
          </Button>
        </Stack>

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
                          <TableCell align="right" width="100px">Policies</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {Object.entries(properties).map(([property, value]) => (
                          <TableRow key={property}>
                            <TableCell>{property}</TableCell>
                            <TableCell>{value}</TableCell>
                            <TableCell align="right">
                              <Tooltip title="View Active Policies">
                                <IconButton size="small">
                                  <Lock color={
                                    policies.some(p => 
                                      p.target[source]?.[property]
                                    ) ? "primary" : "inherit"
                                  } />
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

        <Dialog
          open={policyDialogOpen}
          onClose={() => setPolicyDialogOpen(false)}
          maxWidth="md"
          fullWidth
        >
          <DialogTitle>Create New Policy</DialogTitle>
          <DialogContent>
            <PolicyCreator 
              data={data}
              onPolicyCreate={handleCreatePolicy}
            />
          </DialogContent>
        </Dialog>
      </Box>
    </Box>
  );
};

export default SubjectPage;