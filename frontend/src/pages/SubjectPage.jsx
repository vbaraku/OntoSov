import React, { useState, useEffect, useContext } from 'react';
import { AuthContext } from '../components/AuthContext';
import PolicyGroupsManager from '../components/PolicyGroupsManager';
import PolicyDetailsDialog from '../components/PolicyDetailsDialog'; // Import the new component
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
  Button,
} from '@mui/material';
import { 
  ExpandMore, 
  Lock, 
  LockOpen,
  Security,
} from '@mui/icons-material';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';

const SubjectPage = () => {
  const { user } = useContext(AuthContext);
  const [data, setData] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [policyDialogOpen, setPolicyDialogOpen] = useState(false);
  const [controllers, setControllers] = useState([]);
  const [policyStatus, setPolicyStatus] = useState({});
  const [policyGroups, setPolicyGroups] = useState([]);
  const [policyDetailsOpen, setPolicyDetailsOpen] = useState(false);
  const [selectedPolicyDetails, setSelectedPolicyDetails] = useState(null);

  useEffect(() => {
    fetchData();
    if (user?.id) {
      fetchPolicyStatus();
      fetchPolicyGroups();
    }
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

  const fetchPolicyGroups = async () => {
    try {
      const response = await fetch(`http://localhost:8080/api/policy-groups/${user.id}`);
      if (response.ok) {
        const data = await response.json();
        setPolicyGroups(data);
      }
    } catch (err) {
      console.error("Error fetching policy groups:", err);
    }
  };

  const fetchPolicyStatus = async () => {
    try {
      const response = await fetch(`http://localhost:8080/api/policy-groups/status/${user.id}`);
      if (response.ok) {
        const data = await response.json();
        setPolicyStatus(data.assignedPolicies || {});
      }
    } catch (err) {
      console.error("Error fetching policy status:", err);
    }
  };

  const isPropertyProtected = (source, property) => {
    // Check if this property is protected by any policy
    for (const groupId in policyStatus) {
      const sourceMap = policyStatus[groupId];
      if (sourceMap && sourceMap[source] && sourceMap[source][property]) {
        return true;
      }
    }
    return false;
  };

  const getPolicyInfo = (source, property) => {
    // Get all policies that protect this property
    const policies = [];
    
    for (const groupId in policyStatus) {
      const sourceMap = policyStatus[groupId];
      if (sourceMap && sourceMap[source] && sourceMap[source][property]) {
        // Normalize the groupId by removing the prefix if present
        const normalizedGroupId = groupId.includes('#') 
          ? groupId.split('#')[1] 
          : groupId;
        
        // Find the policy group using the normalized ID
        const policyGroup = policyGroups.find(pg => pg.id === normalizedGroupId);
        
        if (policyGroup) {
          policies.push({
            groupId: normalizedGroupId,
            groupName: policyGroup.name,
            actions: sourceMap[source][property],
            constraints: policyGroup.constraints
          });
        } else {
          policies.push({
            groupId: normalizedGroupId,
            groupName: 'Unknown Policy',
            actions: sourceMap[source][property]
          });
        }
      }
    }
    
    return policies;
  };

  const handleShowPolicyDetails = (source, property) => {
    const policies = getPolicyInfo(source, property);
    if (policies.length > 0) {
      setSelectedPolicyDetails({
        source,
        property,
        policies
      });
      setPolicyDetailsOpen(true);
    }
  };

  const handlePolicyDialogClose = () => {
    setPolicyDialogOpen(false);
    // Refresh policy status after dialog is closed
    fetchPolicyStatus();
    fetchPolicyGroups();
  };

  // Helper to get the first policy group name for a property (for display in table)
  const getPrimaryPolicyName = (source, property) => {
    const policies = getPolicyInfo(source, property);
    return policies.length > 0 ? policies[0].groupName : '';
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
      width: '100%',
      minHeight: '100vh',
      backgroundImage: 'radial-gradient(ellipse at 50% 50%, hsl(210, 100%, 97%), hsl(0, 0%, 100%))',
      margin: 0,
      padding: 0
    }}>
      <Box sx={{ maxWidth: '1200px', margin: '0 auto', p: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
          <Typography variant="h4">My Personal Data Overview</Typography>
          <Button
            variant="contained"
            startIcon={<Security />}
            onClick={() => setPolicyDialogOpen(true)}
          >
            Manage Policy Groups
          </Button>
        </Box>

        {Object.entries(data).length === 0 ? (
          <Alert severity="info">
            No personal data found. This could be because your tax ID is not recognized by any controllers,
            or there might be an issue connecting to the data sources.
          </Alert>
        ) : (
          Object.entries(data).map(([source, properties]) => (
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
                            <TableCell align="right" width="120px">Policy</TableCell>
                            <TableCell align="right" width="100px">Status</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {Object.entries(properties).map(([property, value]) => {
                            const isProtected = isPropertyProtected(source, property);
                            const policyName = isProtected ? getPrimaryPolicyName(source, property) : '';
                            
                            return (
                              <TableRow key={property}>
                                <TableCell>{property}</TableCell>
                                <TableCell>{value}</TableCell>
                                <TableCell align="right">
                                  {isProtected && (
                                    <Typography variant="body2" color="primary">
                                      {policyName}
                                    </Typography>
                                  )}
                                </TableCell>
                                <TableCell align="right">
                                  <Tooltip title={
                                    isProtected 
                                      ? "Protected by policy - Click for details" 
                                      : "No policy applied"
                                  }>
                                    <IconButton 
                                      size="small"
                                      onClick={() => isProtected && handleShowPolicyDetails(source, property)}
                                    >
                                      {isProtected ? (
                                        <Lock color="primary" />
                                      ) : (
                                        <LockOpen color="action" />
                                      )}
                                    </IconButton>
                                  </Tooltip>
                                </TableCell>
                              </TableRow>
                            );
                          })}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </CardContent>
                </Card>
              </AccordionDetails>
            </Accordion>
          ))
        )}

        {/* Policy management dialog */}
        <Dialog
          open={policyDialogOpen}
          onClose={handlePolicyDialogClose}
          maxWidth="lg"
          fullWidth
        >
          <DialogTitle>Manage Data Policies</DialogTitle>
          <DialogContent>
            <PolicyGroupsManager
              data={data}
              userId={user?.id}
            />
          </DialogContent>
        </Dialog>

        <PolicyDetailsDialog 
          open={policyDetailsOpen}
          onClose={() => setPolicyDetailsOpen(false)}
          details={selectedPolicyDetails}
        />
      </Box>
    </Box>
  );
};

export default SubjectPage;