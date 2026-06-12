import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { projectsApi } from '../features/projects/api'
import type { Project } from '../features/projects/types'
import { ProjectCard } from '../features/projects/ProjectCard'
import { FormError } from '../components/FormError'
import { SectionHead } from '../components/SectionHead'
import { page } from '../components/styles'

const projectGrid = 'grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-[1.1rem]'

export default function Projects() {
    const [projects, setProjects] = useState<Project[]>([])
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        projectsApi
            .list()
            .then(setProjects)
            .catch((e: Error) => setError(e.message))
    }, [])

    const active = useMemo(() => projects.filter((p) => p.active), [projects])
    const past = useMemo(() => projects.filter((p) => !p.active), [projects])

    return (
        <section className={page}>
            <SectionHead eyebrow="Projects" title="Active projects">
                Member-led and board-approved. Want yours here?{' '}
                <Link to="/projects/new">Start a design doc.</Link>
            </SectionHead>

            <FormError error={error} />

            <div className={projectGrid}>
                {active.map((p) => (
                    <ProjectCard key={p.id} project={p} />
                ))}
            </div>

            {past.length > 0 && (
                <>
                    <SectionHead title="Past projects" className="mt-14">
                        Projects from past semesters.
                    </SectionHead>
                    <div className={projectGrid}>
                        {past.map((p) => (
                            <ProjectCard key={p.id} project={p} />
                        ))}
                    </div>
                </>
            )}
        </section>
    )
}
