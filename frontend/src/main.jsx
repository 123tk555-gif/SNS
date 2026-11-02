import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import App from './App.jsx'
import { AuthProvider } from './auth.jsx'
import { NoticeProvider } from './notice.jsx'
import { RealtimeProvider } from './realtime.jsx'
import './styles.css'
import { applyTheme, loadTheme } from './theme.js'

// 設定画面で選んだ表示モード(ライト・ダーク)を、画面を描く前に反映する
applyTheme(loadTheme())

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
