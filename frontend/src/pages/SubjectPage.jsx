import React, { useState, useEffect, useContext } from "react";
import { AuthContext } from "../components/AuthContext";
import PolicyGroupsManager from "../components/PolicyGroupsManager";
import PolicyDetailsDialog from "../components/PolicyDetailsDialog";
import {
  Box,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Card,
  CardContent,
  CardHeader,
  IconButton,
  Tooltip,
  Grid,
  Stack,
  Chip,
  CircularProgress,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  Button,
  TextField,
  InputAdornment,
  LinearProgress,
  Tabs,
  Tab,
  tableCellClasses,
  styled,
  Divider,
} from "@mui/material";
import {
  Lock,
  LockOpen,
  Security,
  Search,
  Visibility,
  Storage,
  BarChart,
  MoreVert,
  DataUsage,
  FilterList,
} from "@mui/icons-material";
import AddModeratorIcon from "@mui/icons-material/AddModerator";

// Styled components for enhanced visuals
const StyledTableCell = styled(TableCell)(({ theme }) => ({
  [`&.${tableCellClasses.head}`]: {
    backgroundColor: theme.palette.primary.light,
    color: theme.palette.primary.contrastText,
    fontWeight: 500,
  },
  [`&.${tableCellClasses.body}`]: {
    fontSize: 14,
  },
}));

const StyledTableRow = styled(TableRow)(({ theme }) => ({
  "&:nth-of-type(odd)": {
    backgroundColor: theme.palette.action.hover,
  },
  "&:hover": {
    backgroundColor: theme.palette.action.selected,
  },
}));

const ProgressBar = ({ value, color = "primary" }) => (
  <Box
    sx={{
      position: "relative",
      display: "inline-flex",
      alignItems: "center",
      width: 100,
    }}
  >
    <LinearProgress
      variant="determinate"
      value={value}
      color={color}
      sx={{ width: "100%", height: 8, borderRadius: 5 }}
    />
    <Typography
      variant="caption"
      sx={{ position: "absolute", right: -35, color: "text.secondary" }}
    >
      {Math.round(value)}%
    </Typography>
  </Box>
);

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
  const [searchTerms, setSearchTerms] = useState({});
  const [filterTabs, setFilterTabs] = useState({});
  const [selectedDataForPolicy, setSelectedDataForPolicy] = useState(null);

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
      const availableControllers = await fetch(
        "http://localhost:8080/api/controllers"
      ).then((res) => res.json());
      setControllers(availableControllers);

      const controllerData = await Promise.all(
        availableControllers.map((controller) =>
          fetch(
            `http://localhost:8080/api/ontop/person/${user.taxid}/controller/${controller.id}`
          )
            .then((res) => res.json())
            .catch((err) => null)
        )
      );

      const combinedData = controllerData.reduce((acc, data, index) => {
        if (data) {
          Object.entries(data).forEach(([source, sourceData]) => {
            acc[`${availableControllers[index].name} - ${source}`] = sourceData;
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
      const response = await fetch(
        `http://localhost:8080/api/policy-groups/${user.id}`
      );
      if (response.ok) {
        const data = await response.json();
        setPolicyGroups(data);
      }
    } catch (err) {
      console.error("Error fetching policies:", err);
    }
  };

  const fetchPolicyStatus = async () => {
    try {
      const response = await fetch(
        `http://localhost:8080/api/policy-groups/status/${user.id}`
      );
      if (response.ok) {
        const data = await response.json();
        setPolicyStatus(data.assignedPolicies || {});
      }
    } catch (err) {
      console.error("Error fetching policy status:", err);
    }
  };

  // Helper to check if property is protected
  const isPropertyProtected = (source, property) => {
    for (const groupId in policyStatus) {
      const sourceMap = policyStatus[groupId];
      if (sourceMap && sourceMap[source] && sourceMap[source][property]) {
        return true;
      }
    }
    return false;
  };

  // Helper functions for entity-level privacy
  const isEntityProtected = (source, entityType, entityId) => {
    // TODO: Implement entity-level protection checking
    return false; // Placeholder for now
  };

  const handleApplyPolicyToEntity = (source, entityType, entityId) => {
    console.log(`Apply policy to entity: ${entityType} ${entityId} in ${source}`);
    setPolicyDialogOpen(true);
    setSelectedDataForPolicy({ source, entityType, entityId, isEntity: true });
  };

  // Helper to get policy info
  const getPolicyInfo = (source, property) => {
    const policies = [];
    for (const groupId in policyStatus) {
      const sourceMap = policyStatus[groupId];
      if (sourceMap && sourceMap[source] && sourceMap[source][property]) {
        const normalizedGroupId = groupId.includes("#")
          ? groupId.split("#")[1]
          : groupId;

        const policyGroup = policyGroups.find(
          (pg) => pg.id === normalizedGroupId
        );

        if (policyGroup) {
          policies.push({
            groupId: normalizedGroupId,
            groupName: policyGroup.name,
            actions: sourceMap[source][property],
            constraints: policyGroup.constraints,
            consequences: policyGroup.consequences,
            aiRestrictions: policyGroup.aiRestrictions,
          });
        } else {
          policies.push({
            groupId: normalizedGroupId,
            groupName: "Unknown Policy",
            actions: sourceMap[source][property],
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
        policies,
      });
      setPolicyDetailsOpen(true);
    }
  };

  const handlePolicyDialogClose = () => {
    setPolicyDialogOpen(false);
    fetchPolicyStatus();
    fetchPolicyGroups();
  };

  const handleSearchChange = (source, term) => {
    setSearchTerms((prev) => ({
      ...prev,
      [source]: term,
    }));
  };

  const handleTabChange = (source, tab) => {
    setFilterTabs((prev) => ({
      ...prev,
      [source]: tab,
    }));
  };

  // Helper to format source name
  const formatSourceName = (source) => {
    const parts = source.split(" - ");
    if (parts.length === 2) {
      return (
        <Box>
          <Typography component="span" fontWeight="bold">
            {parts[0]}
          </Typography>
          <Typography component="span" color="text.secondary">
            {" · "}
            {parts[1]}
          </Typography>
        </Box>
      );
    }
    return source;
  };

  // Calculate overall protection stats
  const calculateProtectionStats = () => {
    let totalFields = 0;
    let protectedFields = 0;

    Object.entries(data).forEach(([source, sourceData]) => {
      Object.entries(sourceData).forEach(([entityType, entityData]) => {
        if (entityType === "Person") {
          const personProperties = Object.keys(entityData);
          totalFields += personProperties.length;
          protectedFields += personProperties.filter(property => 
            isPropertyProtected(source, property)
          ).length;
        } else {
          if (Array.isArray(entityData)) {
            totalFields += entityData.length;
            protectedFields += entityData.filter(entity => 
              isEntityProtected(source, entityType, entity.entityId)
            ).length;
          }
        }
      });
    });

    return {
      totalFields,
      protectedFields,
      protectionPercentage:
        totalFields > 0 ? (protectedFields / totalFields) * 100 : 0,
    };
  };

  // Render all data in a single table with proper separation
  const renderSourceDataAsTable = (source, sourceData, searchTerm, filterTab) => {
    const rows = [];
    
    // 1. Person data first (property-level privacy)
    if (sourceData.Person) {
      // Add section header
      rows.push(
        <StyledTableRow key="person-header" sx={{ backgroundColor: "primary.50" }}>
          <StyledTableCell colSpan={4}>
            <Typography variant="subtitle2" fontWeight="bold" color="primary.main">
              Personal Information
            </Typography>
          </StyledTableCell>
        </StyledTableRow>
      );

      Object.entries(sourceData.Person).forEach(([property, value]) => {
        const matchesSearch = property.toLowerCase().includes(searchTerm.toLowerCase()) ||
                             value.toString().toLowerCase().includes(searchTerm.toLowerCase());
        if (!matchesSearch) return;
        
        const isProtected = isPropertyProtected(source, property);
        if (filterTab === 1 && !isProtected) return;
        if (filterTab === 2 && isProtected) return;
        
        const policies = getPolicyInfo(source, property);
        let policyDisplay = "";
        if (policies.length === 1) {
          policyDisplay = policies[0].groupName;
        } else if (policies.length > 1) {
          policyDisplay = `${policies[0].groupName} (+${policies.length - 1})`;
        }
        
        rows.push(
          <StyledTableRow key={`Person-${property}`}>
            <TableCell>{property}</TableCell>
            <TableCell>{value.toString()}</TableCell>
            <TableCell align="right">
              {isProtected && (
                <Typography variant="body2" color="primary">{policyDisplay}</Typography>
              )}
            </TableCell>
            <TableCell align="center">
              <Tooltip title={isProtected ? "Protected - Click for details" : "Click to apply policy"}>
                <IconButton
                  size="small"
                  onClick={() => {
                    if (isProtected) {
                      handleShowPolicyDetails(source, property);
                    } else {
                      setPolicyDialogOpen(true);
                      setSelectedDataForPolicy({ source: source, property: property });
                    }
                  }}
                >
                  {isProtected ? <Lock fontSize="small" color="primary" /> : <LockOpen fontSize="small" color="action" />}
                </IconButton>
              </Tooltip>
            </TableCell>
          </StyledTableRow>
        );
      });
    }
    
    // 2. Transactional data (entity-level privacy)
    const transactionalEntityTypes = Object.keys(sourceData).filter(entityType => 
      entityType !== "Person" && Array.isArray(sourceData[entityType])
    );
    
    if (transactionalEntityTypes.length > 0) {
      // Add section header
      rows.push(
        <StyledTableRow key="transactional-header" sx={{ backgroundColor: "secondary.50" }}>
          <StyledTableCell colSpan={4}>
            <Typography variant="subtitle2" fontWeight="bold" color="secondary.main">
              Transactional Data
            </Typography>
          </StyledTableCell>
        </StyledTableRow>
      );

      transactionalEntityTypes.forEach(entityType => {
        const entityData = sourceData[entityType];
        
        entityData.forEach((entity, index) => {
          const matchesSearch = Object.entries(entity.properties).some(([property, value]) => 
            property.toLowerCase().includes(searchTerm.toLowerCase()) ||
            value.toString().toLowerCase().includes(searchTerm.toLowerCase())
          );
          if (!matchesSearch) return;
          
          const isProtected = isEntityProtected(source, entityType, entity.entityId);
          if (filterTab === 1 && !isProtected) return;
          if (filterTab === 2 && isProtected) return;
          
          // Create a single row with all properties
          const propertyText = Object.entries(entity.properties)
            .map(([prop, val]) => `${prop}: ${val}`)
            .join(" • ");
          
          rows.push(
            <StyledTableRow key={entity.entityId}>
              <TableCell>
                <Chip 
                  label={`${entityType} #${index + 1}`} 
                  size="small" 
                  color={isProtected ? "primary" : "default"} 
                  variant={isProtected ? "filled" : "outlined"}
                />
              </TableCell>
              <TableCell>{propertyText}</TableCell>
              <TableCell align="right">
                {isProtected && <Typography variant="body2" color="primary">Protected</Typography>}
              </TableCell>
              <TableCell align="center">
                <Tooltip title={isProtected ? "Protected entity" : "Click to apply policy"}>
                  <IconButton
                    size="small"
                    onClick={() => handleApplyPolicyToEntity(source, entityType, entity.entityId)}
                  >
                    {isProtected ? <Lock fontSize="small" color="primary" /> : <LockOpen fontSize="small" color="action" />}
                  </IconButton>
                </Tooltip>
              </TableCell>
            </StyledTableRow>
          );
        });
      });
    }
    
    if (rows.length === 0) {
      return (
        <TableRow>
          <TableCell colSpan={4} align="center">
            <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
              No matching data found
            </Typography>
          </TableCell>
        </TableRow>
      );
    }
    
    return rows;
  };

  if (loading) {
    return (
      <Box
        sx={{
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          height: "100vh",
        }}
      >
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

  const { totalFields, protectedFields, protectionPercentage } =
    calculateProtectionStats();

  return (
    <Box
      sx={{
        width: "100%",
        minHeight: "100vh",
        backgroundImage:
          "radial-gradient(ellipse at 50% 50%, hsl(210, 100%, 97%), hsl(0, 0%, 100%))",
        margin: 0,
        padding: 0,
      }}
    >
      <Box sx={{ maxWidth: "1200px", margin: "0 auto", p: 3 }}>
        <Box sx={{ mb: 4, mt: 2 }}>
          <Grid container spacing={2} alignItems="center">
            <Grid item>
              <Box
                sx={{
                  width: 64,
                  height: 64,
                  borderRadius: "12px",
                  bgcolor: "primary.main",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  boxShadow: "0 4px 12px rgba(0,0,0,0.08)",
                }}
              >
                <Security sx={{ fontSize: 36, color: "white" }} />
              </Box>
            </Grid>
            <Grid item xs>
              <Box>
                <Typography
                  variant="h4"
                  fontWeight="bold"
                  sx={{
                    background: "linear-gradient(90deg, #1565c0, #1565c0)",
                    backgroundClip: "text",
                    WebkitBackgroundClip: "text",
                    WebkitTextFillColor: "transparent",
                    textFillColor: "transparent",
                  }}
                >
                  Personal Data Dashboard
                </Typography>
                <Typography variant="subtitle1" color="text.secondary">
                  Manage privacy policies for your personal information
                </Typography>
              </Box>
            </Grid>
            <Grid item>
              <Button
                variant="contained"
                color="primary"
                startIcon={<AddModeratorIcon />}
                onClick={() => setPolicyDialogOpen(true)}
                sx={{
                  px: 3,
                  py: 1,
                  borderRadius: 2,
                  boxShadow: "0 4px 12px rgba(0,0,0,0.1)",
                }}
              >
                Manage Policies
              </Button>
            </Grid>
          </Grid>
        </Box>

        {/* Protection Summary */}
        <Card variant="outlined" sx={{ mb: 3 }}>
          <CardContent>
            <Grid container spacing={2} alignItems="center">
              <Grid item xs={12} md={6}>
                <Typography variant="h6">Protection Summary</Typography>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  Overall data protection status
                </Typography>
                <Box sx={{ display: "flex", alignItems: "center", mt: 1 }}>
                  <DataUsage color="primary" sx={{ fontSize: 40, mr: 2 }} />
                  <Box>
                    <Typography variant="body2">
                      {protectedFields} of {totalFields} items protected
                    </Typography>
                    <ProgressBar
                      value={protectionPercentage}
                      color={
                        protectionPercentage > 70
                          ? "success"
                          : protectionPercentage > 30
                          ? "warning"
                          : "error"
                      }
                    />
                  </Box>
                </Box>
              </Grid>
            </Grid>
          </CardContent>
        </Card>

        {Object.entries(data).length === 0 ? (
          <Alert severity="info">
            No personal data found. This could be because your tax ID is not
            recognized by any controllers, or there might be an issue connecting
            to the data sources.
          </Alert>
        ) : (
          <Grid container spacing={3}>
            {Object.entries(data).map(([source, sourceData]) => {
              // Calculate stats for this source
              let totalItems = 0;
              let protectedItems = 0;
              
              Object.entries(sourceData).forEach(([entityType, entityData]) => {
                if (entityType === "Person") {
                  const personProperties = Object.keys(entityData);
                  totalItems += personProperties.length;
                  protectedItems += personProperties.filter(property => 
                    isPropertyProtected(source, property)
                  ).length;
                } else {
                  if (Array.isArray(entityData)) {
                    totalItems += entityData.length;
                    protectedItems += entityData.filter(entity => 
                      isEntityProtected(source, entityType, entity.entityId)
                    ).length;
                  }
                }
              });

              const protectionPercentage = totalItems > 0 ? (protectedItems / totalItems) * 100 : 0;
              const searchTerm = searchTerms[source] || "";
              const filterTab = filterTabs[source] || 0;

              return (
                <Grid item xs={12} key={source}>
                  <Card sx={{ height: "100%", display: "flex", flexDirection: "column" }}>
                    <CardHeader title={formatSourceName(source)} sx={{ pb: 0 }} />
                    
                    <CardContent sx={{ pt: 1, pb: 1 }}>
                      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }}>
                        <Box sx={{ display: "flex", alignItems: "center" }}>
                          <Storage color="primary" sx={{ mr: 1 }} />
                          <Typography variant="body2" color="text.secondary">
                            {totalItems} data items
                          </Typography>
                        </Box>
                        <Box sx={{ display: "flex", alignItems: "center" }}>
                          <Lock color="primary" sx={{ mr: 1 }} />
                          <Typography variant="body2" color="text.secondary">
                            {protectedItems} protected
                          </Typography>
                        </Box>
                        <Box sx={{ display: "flex", alignItems: "center", ml: "auto" }}>
                          <Typography variant="body2" color="text.secondary" sx={{ mr: 1 }}>
                            Protection:
                          </Typography>
                          <ProgressBar
                            value={protectionPercentage}
                            color={
                              protectionPercentage > 70 ? "success" :
                              protectionPercentage > 30 ? "warning" : "error"
                            }
                          />
                        </Box>
                      </Stack>
                    </CardContent>
                    
                    <Divider />
                    <Box sx={{ display: "flex", p: 2, alignItems: "center" }}>
                      <TextField
                        placeholder="Search data..."
                        variant="outlined"
                        size="small"
                        value={searchTerm}
                        onChange={(e) => handleSearchChange(source, e.target.value)}
                        InputProps={{
                          startAdornment: (
                            <InputAdornment position="start">
                              <Search />
                            </InputAdornment>
                          ),
                        }}
                        sx={{ mr: 2 }}
                      />
                      <Tabs
                        value={filterTab}
                        onChange={(e, newValue) => handleTabChange(source, newValue)}
                        sx={{ ml: "auto" }}
                        indicatorColor="primary"
                        textColor="primary"
                        size="small"
                      >
                        <Tab label="All" />
                        <Tab label="Protected" />
                        <Tab label="Unprotected" />
                      </Tabs>
                    </Box>
                    <Divider />

                    <TableContainer component={Box} sx={{ flexGrow: 1, overflowY: "auto", maxHeight: "600px" }}>
                      <Table size="small" stickyHeader>
                        <TableHead>
                          <TableRow>
                            <StyledTableCell width="200px">Property</StyledTableCell>
                            <StyledTableCell>Value</StyledTableCell>
                            <StyledTableCell align="right" width="120px">Policy</StyledTableCell>
                            <StyledTableCell align="center" width="70px">Status</StyledTableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {renderSourceDataAsTable(source, sourceData, searchTerm, filterTab)}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </Card>
                </Grid>
              );
            })}
          </Grid>
        )}

        {/* Policy management dialog */}
        <Dialog
          open={policyDialogOpen}
          onClose={() => {
            handlePolicyDialogClose();
            setSelectedDataForPolicy(null);
          }}
          maxWidth="lg"
          fullWidth
        >
          <DialogTitle>Manage Data Policies</DialogTitle>
          <DialogContent>
            <PolicyGroupsManager
              data={data}
              userId={user?.id}
              initialSelectedData={
                selectedDataForPolicy
                  ? {
                      [selectedDataForPolicy.source]: [
                        selectedDataForPolicy.property,
                      ],
                    }
                  : undefined
              }
            />
          </DialogContent>
        </Dialog>

        {/* Policy details dialog */}
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