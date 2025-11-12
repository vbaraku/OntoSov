import React, { useState, useEffect } from "react";
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
  Chip,
  TextField,
  InputAdornment,
  MenuItem,
  Grid,
  Card,
  CardContent,
  CircularProgress,
  Alert,
  IconButton,
  Tooltip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Stack,
  tableCellClasses,
  styled,
} from "@mui/material";
import {
  Search as SearchIcon,
  CheckCircle as CheckCircleIcon,
  Cancel as CancelIcon,
  ContentCopy as CopyIcon,
  Visibility as VisibilityIcon,
  FilterList as FilterListIcon,
} from "@mui/icons-material";

const StyledTableCell = styled(TableCell)(({ theme }) => ({
  [`&.${tableCellClasses.head}`]: {
    backgroundColor: theme.palette.primary.light,
    color: theme.palette.primary.contrastText,
    fontWeight: 600,
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
    cursor: "pointer",
  },
}));

const AccessHistory = ({ controllerId }) => {
  const [accessLogs, setAccessLogs] = useState([]);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [filterDecision, setFilterDecision] = useState("all");
  const [filterAction, setFilterAction] = useState("all");
  const [selectedLog, setSelectedLog] = useState(null);
  const [detailsOpen, setDetailsOpen] = useState(false);

  useEffect(() => {
    fetchAccessLogs();
    fetchStats();
  }, [controllerId]);

  const fetchAccessLogs = async () => {
    try {
      const response = await fetch(
        `http://localhost:8080/api/controller/${controllerId}/access-log`
      );
      if (!response.ok) throw new Error("Failed to fetch access logs");
      const data = await response.json();
      setAccessLogs(data);
      setLoading(false);
    } catch (err) {
      console.error("Error fetching access logs:", err);
      setError("Failed to load access history");
      setLoading(false);
    }
  };

  const fetchStats = async () => {
    try {
      const response = await fetch(
        `http://localhost:8080/api/controller/${controllerId}/stats`
      );
      if (!response.ok) throw new Error("Failed to fetch stats");
      const data = await response.json();
      setStats(data);
    } catch (err) {
      console.error("Error fetching stats:", err);
    }
  };

  const formatDataRequested = (log) => {
    // Priority: 1) Property-level access, 2) Entity-level access, 3) Description, 4) N/A
    if (log.dataProperty && log.tableName) {
      return `${log.tableName}.${log.dataProperty}`;
    } else if (log.recordId && log.tableName) {
      return `${log.tableName} (record ID: ${log.recordId})`;
    } else if (log.tableName) {
      return log.tableName;
    } else if (log.dataDescription) {
      return log.dataDescription;
    }
    return "N/A";
  };

  const filteredLogs = accessLogs.filter((log) => {
    const dataRequested = formatDataRequested(log);
    const matchesSearch =
      log.purpose?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      dataRequested.toLowerCase().includes(searchTerm.toLowerCase()) ||
      log.subjectId?.toString().includes(searchTerm);

    const matchesDecision =
      filterDecision === "all" || log.decision === filterDecision;

    const matchesAction = filterAction === "all" || log.action === filterAction;

    return matchesSearch && matchesDecision && matchesAction;
  });

  const handleRowClick = (log) => {
    setSelectedLog(log);
    setDetailsOpen(true);
  };

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
  };

  if (loading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return <Alert severity="error">{error}</Alert>;
  }

  return (
    <Box>
      <Typography variant="h5" color="text.primary" gutterBottom>
        Access History
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        View all your access requests and compliance statistics
      </Typography>

      {/* Statistics Cards */}
      {stats && (
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid item xs={12} sm={6} md={3}>
            <Card>
              <CardContent>
                <Typography color="text.secondary" gutterBottom>
                  Total Requests
                </Typography>
                <Typography variant="h4">{stats.totalRequests}</Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Card>
              <CardContent>
                <Typography color="text.secondary" gutterBottom>
                  Permitted
                </Typography>
                <Typography variant="h4" color="success.main">
                  {stats.permitted}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Card>
              <CardContent>
                <Typography color="text.secondary" gutterBottom>
                  Denied
                </Typography>
                <Typography variant="h4" color="error.main">
                  {stats.denied}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Card>
              <CardContent>
                <Typography color="text.secondary" gutterBottom>
                  Compliance Rate
                </Typography>
                <Typography variant="h4" color="primary.main">
                  {stats.complianceRate}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}

      {/* Filters */}
      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
          <TextField
            placeholder="Search purpose, data, or subject..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            size="small"
            sx={{ flexGrow: 1 }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              ),
            }}
          />
          <TextField
            select
            label="Decision"
            value={filterDecision}
            onChange={(e) => setFilterDecision(e.target.value)}
            size="small"
            sx={{ minWidth: 120 }}
          >
            <MenuItem value="all">All</MenuItem>
            <MenuItem value="PERMIT">Permit</MenuItem>
            <MenuItem value="DENY">Deny</MenuItem>
          </TextField>
          <TextField
            select
            label="Action"
            value={filterAction}
            onChange={(e) => setFilterAction(e.target.value)}
            size="small"
            sx={{ minWidth: 120 }}
          >
            <MenuItem value="all">All</MenuItem>
            <MenuItem value="read">Read</MenuItem>
            <MenuItem value="use">Use</MenuItem>
            <MenuItem value="share">Share</MenuItem>
            <MenuItem value="aggregate">Aggregate</MenuItem>
            <MenuItem value="modify">Modify</MenuItem>
          </TextField>
        </Stack>
      </Paper>

      {/* Access Logs Table */}
      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <StyledTableCell>Timestamp</StyledTableCell>
              <StyledTableCell>Subject ID</StyledTableCell>
              <StyledTableCell>Action</StyledTableCell>
              <StyledTableCell>Purpose</StyledTableCell>
              <StyledTableCell>Decision</StyledTableCell>
              <StyledTableCell>Policy</StyledTableCell>
              <StyledTableCell>Blockchain TX</StyledTableCell>
              <StyledTableCell align="center">Details</StyledTableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {filteredLogs.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} align="center">
                  <Typography variant="body2" color="text.secondary" sx={{ py: 3 }}>
                    No access logs found
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              filteredLogs.map((log) => (
                <StyledTableRow key={log.id} onClick={() => handleRowClick(log)}>
                  <TableCell>
                    {new Date(log.requestTime).toLocaleString()}
                  </TableCell>
                  <TableCell>{log.subjectId}</TableCell>
                  <TableCell>
                    <Chip
                      label={log.action}
                      size="small"
                      variant="outlined"
                      color="primary"
                    />
                  </TableCell>
                  <TableCell>
                    <Tooltip title={log.purpose || ""}>
                      <Typography variant="body2" noWrap sx={{ maxWidth: 200 }}>
                        {log.purpose || "N/A"}
                      </Typography>
                    </Tooltip>
                  </TableCell>
                  <TableCell>
                    <Chip
                      icon={
                        log.decision === "PERMIT" ? (
                          <CheckCircleIcon />
                        ) : (
                          <CancelIcon />
                        )
                      }
                      label={log.decision}
                      size="small"
                      color={log.decision === "PERMIT" ? "success" : "error"}
                    />
                  </TableCell>
                  <TableCell>
                    {log.policyGroupId ? (
                      <Tooltip title={log.policyGroupId}>
                        <Typography variant="body2" noWrap sx={{ maxWidth: 150 }}>
                          v{log.policyVersion}
                        </Typography>
                      </Tooltip>
                    ) : (
                      "N/A"
                    )}
                  </TableCell>
                  <TableCell>
                    {log.blockchainTxHash ? (
                      <Tooltip title={log.blockchainTxHash}>
                        <IconButton
                          size="small"
                          onClick={(e) => {
                            e.stopPropagation();
                            copyToClipboard(log.blockchainTxHash);
                          }}
                        >
                          <CopyIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    ) : (
                      "N/A"
                    )}
                  </TableCell>
                  <TableCell align="center">
                    <IconButton size="small">
                      <VisibilityIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </StyledTableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Details Dialog */}
      <Dialog
        open={detailsOpen}
        onClose={() => setDetailsOpen(false)}
        maxWidth="md"
        fullWidth
      >
        {selectedLog && (
          <>
            <DialogTitle>
              <Stack direction="row" alignItems="center" spacing={2}>
                <Typography variant="h6">Access Request Details</Typography>
                <Chip
                  icon={
                    selectedLog.decision === "PERMIT" ? (
                      <CheckCircleIcon />
                    ) : (
                      <CancelIcon />
                    )
                  }
                  label={selectedLog.decision}
                  color={selectedLog.decision === "PERMIT" ? "success" : "error"}
                />
              </Stack>
            </DialogTitle>
            <DialogContent dividers>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Timestamp
                  </Typography>
                  <Typography variant="body1">
                    {new Date(selectedLog.requestTime).toLocaleString()}
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Subject ID
                  </Typography>
                  <Typography variant="body1">{selectedLog.subjectId}</Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Action
                  </Typography>
                  <Typography variant="body1">{selectedLog.action}</Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Policy Version
                  </Typography>
                  <Typography variant="body1">
                    {selectedLog.policyVersion || "N/A"}
                  </Typography>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Purpose
                  </Typography>
                  <Typography variant="body1">
                    {selectedLog.purpose || "N/A"}
                  </Typography>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Data Requested
                  </Typography>
                  <Typography variant="body1">
                    {formatDataRequested(selectedLog)}
                  </Typography>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Reason
                  </Typography>
                  <Alert
                    severity={
                      selectedLog.decision === "PERMIT" ? "success" : "error"
                    }
                  >
                    {selectedLog.reason}
                  </Alert>
                </Grid>
                {selectedLog.policyGroupId && (
                  <Grid item xs={12}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Policy Group ID
                    </Typography>
                    <Typography
                      variant="body2"
                      sx={{ fontFamily: "monospace", wordBreak: "break-all" }}
                    >
                      {selectedLog.policyGroupId}
                    </Typography>
                  </Grid>
                )}
                {selectedLog.blockchainTxHash && (
                  <Grid item xs={12}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Blockchain Transaction Hash
                    </Typography>
                    <Paper variant="outlined" sx={{ p: 1, bgcolor: "grey.50" }}>
                      <Stack direction="row" alignItems="center" spacing={1}>
                        <Typography
                          variant="body2"
                          sx={{
                            fontFamily: "monospace",
                            wordBreak: "break-all",
                            flex: 1,
                          }}
                        >
                          {selectedLog.blockchainTxHash}
                        </Typography>
                        <IconButton
                          size="small"
                          onClick={() =>
                            copyToClipboard(selectedLog.blockchainTxHash)
                          }
                        >
                          <CopyIcon fontSize="small" />
                        </IconButton>
                      </Stack>
                    </Paper>
                  </Grid>
                )}
              </Grid>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setDetailsOpen(false)}>Close</Button>
            </DialogActions>
          </>
        )}
      </Dialog>
    </Box>
  );
};

export default AccessHistory;