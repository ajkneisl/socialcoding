import {Route, Routes} from 'react-router-dom'
import Layout from './components/Layout'
import Account from './pages/Account'
import Board from './pages/Board'
import CreateProject from './pages/CreateProject'
import Home from './pages/Home'
import People from './pages/People'
import ProjectDetail from './pages/ProjectDetail'
import Projects from './pages/Projects'

export default function App() {
    return (
        <Routes>
            <Route element={<Layout/>}>
                <Route index element={<Home/>}/>
                <Route path="projects" element={<Projects/>}/>
                <Route path="projects/new" element={<CreateProject/>}/>
                <Route path="projects/:id" element={<ProjectDetail/>}/>
                <Route path="people" element={<People/>}/>
                <Route path="account" element={<Account/>}/>
                <Route path="board" element={<Board/>}/>
            </Route>
        </Routes>
    )
}
