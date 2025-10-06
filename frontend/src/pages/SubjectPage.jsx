import React, { useState, useEffect, useContext } from "react";
import { AuthContext } from "../components/AuthContext";
import PolicyGroupsManager from "../components/PolicyGroupsManager";
import PolicyDetailsDialog from "../components/PolicyDetailsDialog";
import SubjectAccessHistory from "../components/SubjectAccessHistory";
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
  IconButton,
  Tooltip,
  Grid,
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
} from "@mui/material";
import {
  Lock,
  LockOpen,
  Security,
  Search,
  DataUsage as DataUsageIcon,
  History as HistoryIcon,
  Business as BusinessIcon,
  Person as PersonIcon,
  Receipt as ReceiptIcon,
} from "@mui/icons-material";
import AddModeratorIcon from "@mui/icons-material/AddModerator";

// Styled components
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

// TabPanel component
function TabPanel({ children, value, index, ...other }) {
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`subject-tabpanel-${index}`}
      aria-labelledby={`subject-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ py: 3 }}>{children}</Box>}
    </div>
  );
}

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
  const [currentTab, setCurrentTab] = useState(0);

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

  const isPropertyProtected = (source, property) => {
    for (const groupId in policyStatus) {
      const sourceMap = policyStatus[groupId];
      if (sourceMap && sourceMap[source] && sourceMap[source][property]) {
        return true;
      }
    }
    return false;
  };

  const isEntityProtected = (source, entityType, entityId) => {
    for (const groupId in policyStatus) {
      const sourceMap = policyStatus[groupId];
      if (sourceMap && sourceMap[source] && sourceMap[source][entityId]) {
        return true;
      }
    }
    return false;
  };

  const handleApplyPolicyToEntity = (source, entityType, entityId) => {
    setSelectedDataForPolicy({
      source,
      entityType,
      entityId,
      type: "entity",
    });
    setPolicyDialogOpen(true);
  };

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

  const handleTabChange = (event, newValue) => {
    setCurrentTab(newValue);
  };

  const handleFilterTabChange = (source, tab) => {
    setFilterTabs((prev) => ({
      ...prev,
      [source]: tab,
    }));
  };

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

  const handleShowEntityPolicyDetails = (source, entityType, entityId) => {
    const policies = getEntityPolicyInfo(source, entityId);
    if (policies.length > 0) {
      setSelectedPolicyDetails({
        source,
        entityType,
        entityId,
        type: "entity",
        policies,
      });
      setPolicyDetailsOpen(true);
    }
  };

  const getEntityPolicyInfo = (source, entityId) => {
    const policies = [];
    for (const groupId in policyStatus) {
      const sourceMap = policyStatus[groupId];
      if (sourceMap && sourceMap[source] && sourceMap[source][entityId]) {
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
            actions: sourceMap[source][entityId],
            constraints: policyGroup.constraints,
            consequences: policyGroup.consequences,
            aiRestrictions: policyGroup.aiRestrictions,
          });
        } else {
          policies.push({
            groupId: normalizedGroupId,
            groupName: "Unknown Policy",
            actions: sourceMap[source][entityId],
          });
        }
      }
    }
    return policies;
  };

  const calculateProtectionStats = () => {
    let totalFields = 0;
    let protectedFields = 0;

    Object.entries(data).forEach(([source, sourceData]) => {
      Object.entries(sourceData).forEach(([entityType, entityData]) => {
        if (entityType === "Person") {
          const personProperties = Object.keys(entityData);
          totalFields += personProperties.length;
          protectedFields += personProperties.filter((property) =>
            isPropertyProtected(source, property)
          ).length;
        } else {
          if (Array.isArray(entityData)) {
            totalFields += entityData.length;
            protectedFields += entityData.filter((entity) =>
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
        totalFields > 0 ? Math.round((protectedFields / totalFields) * 100) : 0,
    };
  };

  const renderSourceDataAsTable = (source, sourceData, searchTerm, filterTab) => {
    const rows = [];

    // 1. Person data first (property-level privacy)
    if (sourceData.Person) {
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
        const matchesSearch =
          property.toLowerCase().includes(searchTerm.toLowerCase()) ||
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
                <Typography variant="body2" color="primary">
                  {policyDisplay}
                </Typography>
              )}
            </TableCell>
            <TableCell align="center">
              <Tooltip
                title={
                  isProtected
                    ? "Protected - Click for details"
                    : "Click to apply policy"
                }
              >
                <IconButton
                  size="small"
                  onClick={() => {
                    if (isProtected) {
                      handleShowPolicyDetails(source, property);
                    } else {
                      setPolicyDialogOpen(true);
                      setSelectedDataForPolicy({
                        source: source,
                        property: property,
                      });
                    }
                  }}
                >
                  {isProtected ? (
                    <Lock fontSize="small" color="primary" />
                  ) : (
                    <LockOpen fontSize="small" color="action" />
                  )}
                </IconButton>
              </Tooltip>
            </TableCell>
          </StyledTableRow>
        );
      });
    }

    // 2. Transactional data (entity-level privacy)
    const transactionalEntityTypes = Object.keys(sourceData).filter(
      (entityType) =>
        entityType !== "Person" && Array.isArray(sourceData[entityType])
    );

    if (transactionalEntityTypes.length > 0) {
      rows.push(
        <StyledTableRow key="transactional-header" sx={{ backgroundColor: "secondary.50" }}>
          <StyledTableCell colSpan={4}>
            <Typography variant="subtitle2" fontWeight="bold" color="secondary.main">
              Transactional Data
            </Typography>
          </StyledTableCell>
        </StyledTableRow>
      );

      transactionalEntityTypes.forEach((entityType) => {
        const entityData = sourceData[entityType];

        entityData.forEach((entity, index) => {
          const matchesSearch = Object.entries(entity.properties).some(
            ([property, value]) =>
              property.toLowerCase().includes(searchTerm.toLowerCase()) ||
              value.toString().toLowerCase().includes(searchTerm.toLowerCase())
          );
          if (!matchesSearch) return;

          const isProtected = isEntityProtected(
            source,
            entityType,
            entity.entityId
          );
          if (filterTab === 1 && !isProtected) return;
          if (filterTab === 2 && isProtected) return;

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
                {isProtected &&
                  (() => {
                    const entityPolicies = getEntityPolicyInfo(
                      source,
                      entity.entityId
                    );
                    let policyDisplay = "";

                    if (entityPolicies.length === 1) {
                      policyDisplay = entityPolicies[0].groupName;
                    } else if (entityPolicies.length > 1) {
                      policyDisplay = `${entityPolicies[0].groupName} (+${
                        entityPolicies.length - 1
                      })`;
                    } else {
                      policyDisplay = "Protected";
                    }

                    return (
                      <Typography variant="body2" color="primary">
                        {policyDisplay}
                      </Typography>
                    );
                  })()}
              </TableCell>
              <TableCell align="center">
                <Tooltip
                  title={
                    isProtected
                      ? "Protected entity - Click for details"
                      : "Click to apply policy"
                  }
                >
                  <IconButton
                    size="small"
                    onClick={() => {
                      if (isProtected) {
                        handleShowEntityPolicyDetails(
                          source,
                          entityType,
                          entity.entityId
                        );
                      } else {
                        handleApplyPolicyToEntity(
                          source,
                          entityType,
                          entity.entityId
                        );
                      }
                    }}
                  >
                    {isProtected ? (
                      <Lock fontSize="small" color="primary" />
                    ) : (
                      <LockOpen fontSize="small" color="action" />
                    )}
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
                startIcon={<AddModeratorIcon />}
                onClick={() => setPolicyDialogOpen(true)}
                size="large"
                sx={{ boxShadow: 2 }}
              >
                Create Policy
              </Button>
            </Grid>
          </Grid>
        </Box>

        {/* Tabs */}
        <Box sx={{ borderBottom: 1, borderColor: "divider", mb: 2 }}>
          <Tabs
            value={currentTab}
            onChange={handleTabChange}
            aria-label="subject tabs"
          >
            <Tab
              icon={<DataUsageIcon />}
              label="My Data"
              iconPosition="start"
            />
            <Tab
              icon={<HistoryIcon />}
              label="Access History"
              iconPosition="start"
            />
          </Tabs>
        </Box>

        {/* Tab 1: My Data */}
        <TabPanel value={currentTab} index={0}>
          {/* Protection Summary - Horizontal layout */}
          <Card sx={{ mb: 3, boxShadow: 2 }}>
            <CardContent sx={{ py: 2 }}>
              <Box sx={{ display: "flex", alignItems: "center", gap: 3 }}>
                {/* Left side - Title */}
                <Box sx={{ minWidth: 200 }}>
                  <Typography variant="h6">
                    Protection Summary
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Overall data protection status
                  </Typography>
                </Box>
                
                {/* Vertical divider */}
                <Box
                  sx={{
                    width: 2,
                    height: 50,
                    bgcolor: "grey.300",
                    borderRadius: 1,
                  }}
                />
                
                {/* Right side - Progress indicator */}
                <Box sx={{ display: "flex", alignItems: "center", gap: 2, flex: 1 }}>
                  <Box
                    sx={{
                      width: 50,
                      height: 50,
                      borderRadius: "50%",
                      border: 3,
                      borderColor: protectionPercentage >= 75 ? "success.main" : protectionPercentage >= 50 ? "warning.main" : "error.main",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                    }}
                  >
                    <Typography variant="body1" fontWeight="bold">
                      {protectionPercentage}%
                    </Typography>
                  </Box>
                  <Box sx={{ flex: 1 }}>
                    <Typography variant="body2" sx={{ mb: 0.5 }}>
                      {protectedFields} of {totalFields} items protected
                    </Typography>
                    <LinearProgress
                      variant="determinate"
                      value={protectionPercentage}
                      color={
                        protectionPercentage >= 75
                          ? "success"
                          : protectionPercentage >= 50
                          ? "warning"
                          : "error"
                      }
                      sx={{ height: 6, borderRadius: 5 }}
                    />
                  </Box>
                </Box>
              </Box>
            </CardContent>
          </Card>

          {/* Data Display */}
          {Object.keys(data).length === 0 && (
            <Alert severity="info">
              No data available. Connect databases to see your personal information.
            </Alert>
          )}

          {Object.keys(data).length > 0 && (
            <Grid container spacing={2}>
              {Object.entries(data).map(([source, sourceData]) => {
                const searchTerm = searchTerms[source] || "";
                const filterTab = filterTabs[source] || 0;

                // Calculate per-source stats
                let sourceTotal = 0;
                let sourceProtected = 0;
                
                Object.entries(sourceData).forEach(([entityType, entityData]) => {
                  if (entityType === "Person") {
                    const personProperties = Object.keys(entityData);
                    sourceTotal += personProperties.length;
                    sourceProtected += personProperties.filter((property) =>
                      isPropertyProtected(source, property)
                    ).length;
                  } else if (Array.isArray(entityData)) {
                    sourceTotal += entityData.length;
                    sourceProtected += entityData.filter((entity) =>
                      isEntityProtected(source, entityType, entity.entityId)
                    ).length;
                  }
                });

                const sourceProtectionPercent = sourceTotal > 0 
                  ? Math.round((sourceProtected / sourceTotal) * 100) 
                  : 0;

                return (
                  <Grid item xs={12} key={source}>
                    <Card sx={{ boxShadow: 2 }}>
                      <CardContent>
                        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 1 }}>
                          <BusinessIcon color="action" sx={{ fontSize: 28 }} />
                          <Typography variant="h6" fontWeight="600">
                            {formatSourceName(source)}
                          </Typography>
                        </Box>
                        
                        {/* Inline stats */}
                        <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 2 }}>
                          <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                            <DataUsageIcon fontSize="small" color="action" />
                            <Typography variant="body2" color="text.secondary">
                              {sourceTotal} data items
                            </Typography>
                          </Box>
                          <Typography variant="body2" color="text.secondary">|</Typography>
                          <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                            <Lock fontSize="small" color="primary" />
                            <Typography variant="body2" color="text.secondary">
                              {sourceProtected} protected
                            </Typography>
                          </Box>
                          <Typography variant="body2" color="text.secondary">Protection:</Typography>
                          <Box sx={{ flex: 1, maxWidth: 200 }}>
                            <LinearProgress
                              variant="determinate"
                              value={sourceProtectionPercent}
                              color={
                                sourceProtectionPercent >= 75
                                  ? "success"
                                  : sourceProtectionPercent >= 50
                                  ? "warning"
                                  : "error"
                              }
                              sx={{ height: 6, borderRadius: 5 }}
                            />
                          </Box>
                          <Typography variant="body2" color="text.secondary">
                            {sourceProtectionPercent}%
                          </Typography>
                        </Box>

                        {/* Search and Filter tabs */}
                        <Box
                          sx={{
                            display: "flex",
                            justifyContent: "space-between",
                            alignItems: "center",
                            mb: 2,
                          }}
                        >
                          <TextField
                            size="small"
                            placeholder="Search data..."
                            value={searchTerm}
                            onChange={(e) =>
                              handleSearchChange(source, e.target.value)
                            }
                            InputProps={{
                              startAdornment: (
                                <InputAdornment position="start">
                                  <Search fontSize="small" />
                                </InputAdornment>
                              ),
                            }}
                            sx={{ width: 300 }}
                          />
                          
                          <Tabs
                            value={filterTab}
                            onChange={(e, newValue) =>
                              handleFilterTabChange(source, newValue)
                            }
                          >
                            <Tab label="ALL" />
                            <Tab label="PROTECTED" />
                            <Tab label="UNPROTECTED" />
                          </Tabs>
                        </Box>

                        <TableContainer>
                          <Table size="small">
                            <TableHead>
                              <TableRow>
                                <StyledTableCell>Property</StyledTableCell>
                                <StyledTableCell>Value</StyledTableCell>
                                <StyledTableCell align="right">
                                  Policy
                                </StyledTableCell>
                                <StyledTableCell align="center">
                                  Status
                                </StyledTableCell>
                              </TableRow>
                            </TableHead>
                            <TableBody>
                              {renderSourceDataAsTable(
                                source,
                                sourceData,
                                searchTerm,
                                filterTab
                              )}
                            </TableBody>
                          </Table>
                        </TableContainer>
                      </CardContent>
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
                selectedDataForPolicy={selectedDataForPolicy}
                initialSelectedData={
                  selectedDataForPolicy?.type === "entity"
                    ? {
                        entities: {
                          [selectedDataForPolicy.source]: [
                            selectedDataForPolicy.entityId,
                          ],
                        },
                      }
                    : selectedDataForPolicy?.type === "property"
                    ? {
                        properties: {
                          [selectedDataForPolicy.source]: [
                            selectedDataForPolicy.property,
                          ],
                        },
                      }
                    : selectedDataForPolicy
                    ? {
                        properties: {
                          [selectedDataForPolicy.source]: [
                            selectedDataForPolicy.property,
                          ],
                        },
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
        </TabPanel>

        {/* Tab 2: Access History */}
        <TabPanel value={currentTab} index={1}>
          <SubjectAccessHistory
            subjectId={user?.id}
            controllers={controllers}
          />
        </TabPanel>
      </Box>
    </Box>
  );
};

export default SubjectPage;