import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Layout } from './components/Layout'
import { HomePage } from './pages/HomePage'
import { DetailPage } from './pages/DetailPage'
import { FilterPage } from './pages/FilterPage'
import { PlayerPage } from './pages/PlayerPage'
import { SearchPage } from './pages/SearchPage'
import { LibraryPage } from './pages/LibraryPage'
import { SettingsPage } from './pages/SettingsPage'
import { NotFoundPage } from './pages/NotFoundPage'

function App() {
  return (
    <BrowserRouter>
      <div className="h-screen w-screen overflow-hidden bg-background text-foreground">
        <Routes>
          {/* Player page uses full-screen layout */}
          <Route path="/player/:id" element={<PlayerPage />} />
          {/* All other pages use Layout with sidebar */}
          <Route element={<Layout />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/detail/:id" element={<DetailPage />} />
            <Route path="/filter" element={<FilterPage />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/library" element={<LibraryPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </div>
    </BrowserRouter>
  )
}

export default App
