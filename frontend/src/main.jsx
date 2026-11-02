import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import App from './App.jsx'
import { AuthProvider } from './auth.jsx'
import { NoticeProvider } from './notice.jsx'
import { RealtimeProvider } from './realtime.jsx'
import './styles.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <NoticeProvider>
        <AuthProvider>
          <RealtimeProvider>
            <App />
          </RealtimeProvider>
        </AuthProvider>
      </NoticeProvider>
    </BrowserRouter>
  </StrictMode>,
)
