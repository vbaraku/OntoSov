import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import SubjectPage from './pages/SubjectPage';
import ControllerPage from './pages/ControllerPage';

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/subject" element={<SubjectPage />} />
        <Route path="/controller" element={<ControllerPage />} />
      </Routes>
    </Router>
  );
}

export default App;