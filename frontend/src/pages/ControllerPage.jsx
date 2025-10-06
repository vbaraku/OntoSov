import React, { useState, useContext, useEffect } from "react";
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Grid,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  Tabs,
  Tab,
  Container,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import StorageIcon from "@mui/icons-material/Storage";
import PolicyIcon from "@mui/icons-material/Policy";
import HistoryIcon from "@mui/icons-material/History";
import DatabaseIcon from "@mui/icons-material/Storage";
import DatabaseConnectionForm from "../components/DatabaseConnectionForm";
import DatabaseMappingWizard from "../components/DatabaseMappingWizard";
import PolicyChecker from "../components/PolicyChecker";
import AccessHistory from "../components/AccessHistory";
import { AuthContext } from "../components/AuthContext";

function TabPanel({ children, value, index, ...other }) {
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`controller-tabpanel-${index}`}
      aria-labelledby={`controller-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ py: 3 }}>{children}</Box>}
    </div>
  );
}

const ControllerPage = () => {
  const { user } = useContext(AuthContext);
  const [currentTab, setCurrentTab] = useState(0);
  const [openNewDB, setOpenNewDB] = useState(false);
  const [openMapping, setOpenMapping] = useState(false);
  const [selectedDB, setSelectedDB] = useState(null);
  const [databases, setDatabases] = useState([]);
  const [refreshKey, setRefreshKey] = useState(0);

  const fetchDatabases = async () => {
    if (!user) return;

    console.log("Fetching databases...");
    try {
      const response = await fetch(
        `http://localhost:8080/api/database/controller/${user.id}/databases`
      );
      if (response.ok) {
        const data = await response.json();
        console.log("Received databases:", data);
        setDatabases(data);
      } else {
        console.error("Failed to fetch databases:", await response.text());
      }
    } catch (error) {
      console.error("Error fetching databases:", error);
    }
  };

  useEffect(() => {
    fetchDatabases();
  }, [user, refreshKey]);

  const handleEditMapping = (db) => {
    setSelectedDB(db);
    setOpenMapping(true);
  };

  const handleDeleteDatabase = async (dbId) => {
    try {
      const response = await fetch(
        `http://localhost:8080/api/database/${dbId}`,
        {
          method: "DELETE",
        }
      );
      if (response.ok) {
        setRefreshKey((prev) => prev + 1);
      }
    } catch (error) {
      console.error("Error deleting database:", error);
    }
  };

  const handleTabChange = (event, newValue) => {
    setCurrentTab(newValue);
  };

  return (
    <Container maxWidth="xl" sx={{ mt: 4, mb: 4 }}>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" gutterBottom>
          Controller Dashboard
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Manage your databases, request access to subject data, and view your
          compliance history
        </Typography>
      </Box>

      <Box sx={{ borderBottom: 1, borderColor: "divider" }}>
        <Tabs
          value={currentTab}
          onChange={handleTabChange}
          aria-label="controller tabs"
        >
          <Tab
            icon={<DatabaseIcon />}
            label="Database Management"
            iconPosition="start"
          />
          <Tab
            icon={<PolicyIcon />}
            label="Policy Checker"
            iconPosition="start"
          />
          <Tab
            icon={<HistoryIcon />}
            label="Access History"
            iconPosition="start"
          />
        </Tabs>
      </Box>

      {/* Tab 1: Database Management */}
      <TabPanel value={currentTab} index={0}>
        <Box>
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              mb: 3,
            }}
          >
            <Typography variant="h5">Your Databases</Typography>
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => setOpenNewDB(true)}
            >
              Add Database
            </Button>
          </Box>

          <Grid container spacing={3}>
            {databases.map((db) => (
              <Grid item xs={12} sm={6} md={4} key={db.id}>
                <Card>
                  <CardContent>
                    <Box
                      sx={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "flex-start",
                        mb: 2,
                      }}
                    >
                      <StorageIcon
                        sx={{ fontSize: 40, color: "primary.main" }}
                      />
                      <Box>
                        <IconButton
                          size="small"
                          onClick={() => handleEditMapping(db)}
                        >
                          <EditIcon />
                        </IconButton>
                        <IconButton
                          size="small"
                          color="error"
                          onClick={() => handleDeleteDatabase(db.id)}
                        >
                          <DeleteIcon />
                        </IconButton>
                      </Box>
                    </Box>
                    <Typography variant="h6" gutterBottom>
                      {db.databaseName}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {db.dbType} • {db.host}:{db.port}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            ))}

            {databases.length === 0 && (
              <Grid item xs={12}>
                <Card sx={{ textAlign: "center", py: 4 }}>
                  <CardContent>
                    <StorageIcon
                      sx={{ fontSize: 60, color: "text.secondary", mb: 2 }}
                    />
                    <Typography variant="h6" gutterBottom>
                      No databases yet
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                      Add your first database to start managing your data
                    </Typography>
                    <Button
                      variant="contained"
                      startIcon={<AddIcon />}
                      onClick={() => setOpenNewDB(true)}
                    >
                      Add Database
                    </Button>
                  </CardContent>
                </Card>
              </Grid>
            )}
          </Grid>
        </Box>
      </TabPanel>

      {/* Tab 2: Policy Checker */}
      <TabPanel value={currentTab} index={1}>
        <PolicyChecker controllerId={user?.id} />
      </TabPanel>

      {/* Tab 3: Access History */}
      <TabPanel value={currentTab} index={2}>
        <AccessHistory controllerId={user?.id} />
      </TabPanel>

      {/* Dialog for adding new database */}
      <Dialog
        open={openNewDB}
        onClose={() => setOpenNewDB(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Add New Database</DialogTitle>
        <DialogContent>
          <DatabaseMappingWizard
            controllerId={user?.id}
            onComplete={() => {
              setOpenNewDB(false);
              setRefreshKey((prev) => prev + 1);
            }}
          />
        </DialogContent>
      </Dialog>

      {/* Dialog for editing database mapping */}
      <Dialog
        open={openMapping}
        onClose={() => setOpenMapping(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Edit Database Mapping</DialogTitle>
        <DialogContent>
          <DatabaseMappingWizard
            controllerId={user?.id}
            database={selectedDB}
            onComplete={() => {
              setOpenMapping(false);
              setRefreshKey((prev) => prev + 1);
            }}
          />
        </DialogContent>
      </Dialog>
    </Container>
  );
};

export default ControllerPage;