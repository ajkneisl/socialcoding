import { useMemo } from 'react'
import { useProjects } from '../features/projects/queries'
import { ProjectCard } from '../features/projects/ProjectCard'
import { FormError } from '../components/FormError'
import { SectionHead } from '../components/SectionHead'
import { LinkButton } from '../components/Button'
import { page } from '../components/styles'

const projectGrid = 'grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-[1.1rem]'

export default function Projects() {
    const { data: projects = [], error } = useProjects()

    const active = useMemo(() => projects.filter((p) => p.active), [projects])
    const past = useMemo(() => projects.filter((p) => !p.active), [projects])

    return (
        <section className={page}>
            <div className="flex flex-row items-center justify-between">
                <SectionHead eyebrow="Projects" title="Active projects">
                    Start/Manage your project(s) here.
                </SectionHead>

                <LinkButton
                    to="/projects/new"
                    className="mb-7 shadow-[0_0_0_0_rgba(255,211,2,0)] transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_6px_20px_-4px_rgba(255,211,2,0.45)]"
                >
                    Start a design doc
                </LinkButton>
            </div>

            <FormError error={error?.message} />

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
