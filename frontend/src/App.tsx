import { Navigate, Route, Routes } from 'react-router-dom';
import { HomePage } from './pages/HomePage';
import { FarmerDemoPage } from './pages/FarmerDemoPage';
import { WorkerDemoPage } from './pages/WorkerDemoPage';
import { MatchingPage } from './pages/MatchingPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/farmer-demo" element={<FarmerDemoPage />} />
      <Route path="/worker-demo" element={<WorkerDemoPage />} />
      <Route path="/matching/:id" element={<MatchingPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
