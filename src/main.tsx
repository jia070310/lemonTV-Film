import React, { useState } from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import { AppStartupSplash } from '@/components/AppStartupSplash'
import './index.css'

function Root() {
  const [started, setStarted] = useState(false)
  if (!started) {
    return <AppStartupSplash onComplete={() => setStarted(true)} />
  }
  return <App />
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Root />
  </React.StrictMode>,
)
