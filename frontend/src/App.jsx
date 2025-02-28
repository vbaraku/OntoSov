import {
  BrowserRouter as Router,
  Routes,
  Route,
  Navigate,
} from "react-router-dom";
import SubjectPage from "./pages/SubjectPage";
import ControllerPage from "./pages/ControllerPage";
import { AuthProvider } from "./components/AuthContext";
import SignIn from "./components/SignIn";
import SignUp from "./components/SignUp";
import ProtectedRoute from "./components/ProtectedRoute";
import DatabaseMappingWizard from "./components/DatabaseMappingWizard";
import { LocalizationProvider } from "@mui/x-date-pickers";
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFnsV3';

function App() {
  return (
    <LocalizationProvider dateAdapter={AdapterDateFns}>
      <AuthProvider>
        <Router>
          <Routes>
            <Route path="/signin" element={<SignIn />} />
            <Route path="/signup" element={<SignUp />} />
            <Route element={<ProtectedRoute />}>
              <Route path="/subject" element={<SubjectPage />} />
              <Route path="/controller" element={<ControllerPage />} />
              <Route
                path="/database-mapping"
                element={<DatabaseMappingWizard />}
              />
            </Route>
          </Routes>
        </Router>
      </AuthProvider>
    </LocalizationProvider>
  );
}

export default App;
