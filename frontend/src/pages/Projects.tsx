import {useEffect, useMemo, useState} from 'react'
import {Link} from 'react-router-dom'
import {projectsApi} from '../features/projects/api'
import type {Project} from '../features/projects/types'
import {ProjectCard} from '../components/cards'

export default function Projects() {
    const [projects, setProjects] = useState<Project[]>([])
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        projectsApi.list().then(setProjects).catch((e: Error) => setError(e.message))
    }, [])

    const active = useMemo(() => projects.filter((p) => p.active), [projects])
    const past = useMemo(() => projects.filter((p) => !p.active), [projects])

    return (
        <section className="section container page">
            <div className="section-head">
                <p className="eyebrow mono">Projects</p>
                <h2>Active projects</h2>
                <p className="muted">
                    Member-led and board-approved. Want yours here?{' '}
                    <Link to="/projects/new">Start a design doc.</Link>
                </p>
            </div>

            {error && <p className="form-error">{error}</p>}

            <div className="grid grid-projects">
                {active.map((p) => (
                    <ProjectCard key={p.id} project={p}/>
                ))}
            </div>

            {past.length > 0 && (
                <>
                    <div className="section-head past-head">
                        <h2>Past projects</h2>
                        <p className="muted">Projects from past semesters.</p>
                    </div>
                    <div className="grid grid-projects">
                        {past.map((p) => (
                            <ProjectCard key={p.id} project={p}/>
                        ))}
                    </div>
                </>
            )}
        </section>
    )
}
