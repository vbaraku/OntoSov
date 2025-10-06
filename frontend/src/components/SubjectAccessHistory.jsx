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
  Warning as WarningIcon,
  Shield as ShieldIcon,
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

const SubjectAccessHistory = ({ subjectId, controllers }) => {
  const [accessLogs, setAccessLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [filterDecision, setFilterDecision] = useState("all");
  const [filterAction, setFilterAction] = useState("all");
  const [selectedLog, setSelectedLog] = useState(null);
  const [detailsOpen, setDetailsOpen] = useState(false);

  useEffect(() => {
    if (subjectId) {
      fetchAccessLogs();
    }
  }, [subjectId]);

  const fetchAccessLogs = async () => {
    try {
      const response = await fetch(
        `http://localhost:8080/api/controller/subject/${subjectId}/access-log`
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

  const getControllerName = (controllerId) => {
    const controller = controllers?.find((c) => c.id === controllerId);
    return controller?.name || `Controller ${controllerId}`;
  };

  const filteredLogs = accessLogs.filter((log) => {
    const controllerName = getControllerName(log.controllerId);
    const matchesSearch =
      controllerName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      log.purpose?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      log.dataDescription?.toLowerCase().includes(searchTerm.toLowerCase());

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

  // Calculate statistics
  const totalRequests = accessLogs.length;
  const permitted = accessLogs.filter((log) => log.decision === "PERMIT").length;
  const denied = accessLogs.filter((log) => log.decision === "DENY").length;
  const deniedRecent = accessLogs.filter(
    (log) =>
      log.decision === "DENY" &&
      new Date(log.requestTime) > new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)
  ).length;

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
      <Typography variant="h5" gutterBottom>
        Who Accessed My Data
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Transparency log of all access attempts to your data. Both permitted and
        denied requests are recorded on the blockchain.
      </Typography>

      {/* Alert for recent denials */}
      {deniedRecent > 0 && (
        <Alert severity="warning" icon={<WarningIcon />} sx={{ mb: 3 }}>
          <Typography variant="body2">
            <strong>Notice:</strong> {deniedRecent} unauthorized access attempt
            {deniedRecent > 1 ? "s" : ""} blocked in the last 7 days. Your policies
            are protecting your data.
          </Typography>
        </Alert>
      )}

      {/* Statistics Cards */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Stack direction="row" alignItems="center" spacing={1}>
                <ShieldIcon color="primary" />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    Total Requests
                  </Typography>
                  <Typography variant="h4">{totalRequests}</Typography>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Stack direction="row" alignItems="center" spacing={1}>
                <CheckCircleIcon color="success" />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    Permitted
                  </Typography>
                  <Typography variant="h4" color="success.main">
                    {permitted}
                  </Typography>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Stack direction="row" alignItems="center" spacing={1}>
                <CancelIcon color="error" />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    Denied
                  </Typography>
                  <Typography variant="h4" color="error.main">
                    {denied}
                  </Typography>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Stack direction="row" alignItems="center" spacing={1}>
                <WarningIcon color="warning" />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    Recent Blocks
                  </Typography>
                  <Typography variant="h4" color="warning.main">
                    {deniedRecent}
                  </Typography>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Info Alert */}
      <Alert severity="info" sx={{ mb: 3 }}>
        <Typography variant="body2">
          <strong>Your data sovereignty in action:</strong> Every access attempt is
          logged on the blockchain, creating an immutable audit trail. Controllers
          cannot access your data without your permission, and you can verify all
          requests independently.
        </Typography>
      </Alert>

      {/* Filters */}
      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
          <TextField
            placeholder="Search controller, purpose, or data..."
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
            <MenuItem value="PERMIT">Permitted</MenuItem>
            <MenuItem value="DENY">Denied</MenuItem>
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
              <StyledTableCell>Controller</StyledTableCell>
              <StyledTableCell>Action</StyledTableCell>
              <StyledTableCell>Purpose</StyledTableCell>
              <StyledTableCell>Data Requested</StyledTableCell>
              <StyledTableCell>Decision</StyledTableCell>
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
                  <TableCell>
                    <Chip
                      label={getControllerName(log.controllerId)}
                      size="small"
                      color="default"
                      variant="outlined"
                    />
                  </TableCell>
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
                    <Tooltip title={log.dataDescription || ""}>
                      <Typography variant="body2" noWrap sx={{ maxWidth: 150 }}>
                        {log.dataDescription || "N/A"}
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
                    Controller
                  </Typography>
                  <Typography variant="body1">
                    {getControllerName(selectedLog.controllerId)}
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Action Requested
                  </Typography>
                  <Typography variant="body1">{selectedLog.action}</Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Policy Applied
                  </Typography>
                  <Typography variant="body1">
                    {selectedLog.policyGroupId
                      ? `Version ${selectedLog.policyVersion}`
                      : "No policy matched"}
                  </Typography>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Purpose Stated
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
                    {selectedLog.dataDescription || "N/A"}
                  </Typography>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Decision Reason
                  </Typography>
                  <Alert
                    severity={
                      selectedLog.decision === "PERMIT" ? "success" : "error"
                    }
                  >
                    {selectedLog.reason}
                  </Alert>
                </Grid>
                {selectedLog.decision === "DENY" && (
                  <Grid item xs={12}>
                    <Alert severity="info">
                      <Typography variant="body2">
                        <strong>Your policies protected your data.</strong> This
                        access attempt was blocked because it didn't meet your
                        requirements. You can review your policies to ensure they
                        match your preferences.
                      </Typography>
                    </Alert>
                  </Grid>
                )}
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
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ mt: 1, display: "block" }}
                    >
                      This access attempt is permanently recorded on the blockchain
                      for verification and audit purposes.
                    </Typography>
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

export default SubjectAccessHistory;