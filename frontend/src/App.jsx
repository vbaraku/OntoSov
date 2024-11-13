import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import SubjectPage from './pages/SubjectPage';
import ControllerPage from './pages/ControllerPage';
import { AuthProvider } from './components/AuthContext';
import SignIn from './components/SignIn';
import SignUp from './components/SignUp'
import ProtectedRoute from './components/ProtectedRoute';

function App() {
  return (
    <AuthProvider>
      <Router>
        <Routes>
          <Route path="/signin" element={<SignIn />} />
          <Route path="/signup" element={<SignUp />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/subject" element={<SubjectPage />} />
            <Route path="/controller" element={<ControllerPage />} />
          </Route>
        </Routes>
      </Router>
    </AuthProvider>
  );
}

export default App;