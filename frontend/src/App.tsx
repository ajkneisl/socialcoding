import { Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import Account from './pages/Account'
import BoardLayout from './pages/board/BoardLayout'
import BoardEvents from './pages/board/Events'
import BoardOverview from './pages/board/Overview'
import BoardProjects from './pages/board/Projects'
import CreateProject from './pages/CreateProject'
import EventDetail from './pages/EventDetail'
import Events from './pages/Events'
import Home from './pages/Home'
import People from './pages/People'
import ProjectDetail from './pages/ProjectDetail'
import Projects from './pages/Projects'

export default function App() {
    return (
        <Routes>
            <Route element={<Layout />}>
                <Route index element={<Home />} />
                <Route path="projects" element={<Projects />} />
                <Route path="projects/new" element={<CreateProject />} />
                <Route path="projects/:id" element={<ProjectDetail />} />
                <Route path="events" element={<Events />} />
                <Route path="events/:id" element={<EventDetail />} />
                <Route path="people" element={<People />} />
                <Route path="account" element={<Account />} />
                <Route path="board" element={<BoardLayout />}>
                    <Route index element={<BoardOverview />} />
                    <Route path="projects" element={<BoardProjects />} />
                    <Route path="events" element={<BoardEvents />} />
                </Route>
            </Route>
        </Routes>
    )
}
