import React, { useEffect, useState } from 'react';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import Pairing from './pages/Pairing';
import FileTransfer from './pages/FileTransfer';
import SMS from './pages/SMS';
import Notifications from './pages/Notifications';
import NoteMaker from './pages/NoteMaker';
import ScreenMirror from './pages/ScreenMirror';
import Settings from './pages/Settings';
import { ConnectionProvider } from './contexts/ConnectionContext';
import { ThemeProvider } from './contexts/ThemeContext';

function App() {
  return (
    <ThemeProvider>
      <ConnectionProvider>
        <Router>
          <Routes>
            <Route path="/" element={<Layout />}>
              <Route index element={<Navigate to="/dashboard" replace />} />
              <Route path="dashboard" element={<Dashboard />} />
              <Route path="pairing" element={<Pairing />} />
              <Route path="files" element={<FileTransfer />} />
              <Route path="sms" element={<SMS />} />
              <Route path="notifications" element={<Notifications />} />
              <Route path="notes" element={<NoteMaker />} />
              <Route path="screen-mirror" element={<ScreenMirror />} />
              <Route path="settings" element={<Settings />} />
            </Route>
          </Routes>
        </Router>
      </ConnectionProvider>
    </ThemeProvider>
  );
}

export default App;
