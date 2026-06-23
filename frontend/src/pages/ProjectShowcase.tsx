import { Link, useParams } from 'react-router-dom'
import { Avatar } from '../components/Avatar'
import { Badge } from '../components/Badge'
import { Eyebrow } from '../components/Eyebrow'
import { FormError } from '../components/FormError'
import { PageMessage } from '../components/PageMessage'
import { card, page } from '../components/styles'
import { LikeButton } from '../features/projects/LikeButton'
import { ActiveBadge } from '../features/projects/StatusBadge'
import { useProjectShowcase } from '../features/projects/queries'

const externalLink =
    'inline-flex items-center gap-[0.4rem] font-semibold text-text-soft hover:text-gold hover:no-underline'

export default function ProjectShowcase() {
    const { id } = useParams()
    const { data: showcase, error, isLoading } = useProjectShowcase(id ?? '')

    if (isLoading) {
        return <PageMessage>Loading…</PageMessage>
    }

    if (error || !showcase) {
        return (
            <section className={page}>
                <FormError error={error?.message ?? 'Project not found.'} />
                <p className="mt-3 font-mono text-[0.85rem] text-text-faint">
                    <Link to="/projects">← Back to projects</Link>
                </p>
            </section>
        )
    }

    const { project, teamLeadID, members } = showcase

    return (
        <section className={page}>
            <p className="mb-5 font-mono text-[0.85rem] text-text-faint">
                <Link to="/projects">← Back to projects</Link>
            </p>

            <div className="mb-8 max-w-[720px]">
                <Eyebrow>Project</Eyebrow>
                <div className="flex flex-wrap items-center gap-3">
                    <h2 className="m-0">{project.title}</h2>
                    <ActiveBadge active={project.active} />
                </div>
                <p className="my-3 text-text-soft">{project.description}</p>
                <div className="flex flex-wrap items-center gap-4 font-mono text-[0.8rem] text-text-faint">
                    <LikeButton project={project} />
                    {project.siteUrl && (
                        <a
                            href={project.siteUrl}
                            target="_blank"
                            rel="noreferrer"
                            className={externalLink}
                        >
                            {project.siteUrl.replace(/^https?:\/\//, '').replace(/\/$/, '')} ↗
                        </a>
                    )}
                    {project.repoUrl && (
                        <a
                            href={project.repoUrl}
                            target="_blank"
                            rel="noreferrer"
                            className={externalLink}
                        >
                            <svg
                                viewBox="0 0 16 16"
                                width="15"
                                height="15"
                                fill="currentColor"
                                aria-hidden="true"
                            >
                                <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.42 7.42 0 0 1 2-.27c.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z" />
                            </svg>
                            GitHub
                        </a>
                    )}
                </div>
            </div>

            <div className={`${card} max-w-[720px]`}>
                <h3 className="mb-4 mt-0">Team</h3>
                <ul className="flex flex-col gap-[0.55rem]">
                    {members.map((m) => (
                        <li key={m.id} className="flex items-center gap-[0.65rem]">
                            <Avatar name={m.name} avatarUrl={m.avatarUrl} size="sm" />
                            <span className="font-medium">{m.name}</span>
                            {m.id === teamLeadID && <Badge variant="board">team lead</Badge>}
                        </li>
                    ))}
                </ul>
            </div>
        </section>
    )
}
