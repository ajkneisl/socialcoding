import { Link } from 'react-router-dom'
import { Avatar } from '../../components/Avatar'
import { card } from '../../components/styles'
import { LikeButton } from './LikeButton'
import { ReviewNote, StatusBadge } from './StatusBadge'
import type { Project } from './types'

const repoLink =
    'relative z-10 inline-flex items-center gap-[0.4rem] font-semibold text-text-soft hover:text-gold hover:no-underline'

export function ProjectCard({
    project,
    showStatus,
    linkToDoc,
}: {
    project: Project
    showStatus?: boolean
    linkToDoc?: boolean
}) {
    // The whole card is a link; the title carries it via a stretched overlay so interactive
    // children (hearts, repo links) still work above it.
    const to = linkToDoc ? `/projects/${project.id}/doc` : `/projects/${project.id}`

    return (
        <article
            className={`${card} relative flex flex-col transition-[border-color,transform] duration-150 hover:-translate-y-0.5 hover:border-text-faint`}
        >
            <div className="flex items-start justify-between gap-3">
                <h3 className="m-0 min-w-0">
                    <Link to={to} className="after:absolute after:inset-0 after:content-['']">
                        {project.title}
                    </Link>
                </h3>
                <div className="flex shrink-0 items-center gap-2">
                    {showStatus && <StatusBadge status={project.status} />}
                    {project.imageUrl && (
                        <img
                            src={project.imageUrl}
                            alt=""
                            className="h-12 w-12 shrink-0 rounded-md border border-line bg-bg-raised object-contain p-1"
                        />
                    )}
                </div>
            </div>
            <p className="mb-3 mt-[0.45rem] text-text-soft">{project.description}</p>
            <div className="mt-auto flex items-center justify-between gap-3 pt-2 font-mono text-[0.8rem] text-text-faint">
                {!showStatus && (
                    <span className="relative z-10">
                        <LikeButton project={project} />
                    </span>
                )}
                <span className="flex min-w-0 items-center gap-[0.45rem]">
                    <span className="truncate">led by {project.teamLeadName}</span>
                    <Avatar
                        name={project.teamLeadName}
                        avatarUrl={project.teamLeadAvatarUrl}
                        size="sm"
                    />
                </span>
                {project.siteUrl && (
                    <a href={project.siteUrl} target="_blank" rel="noreferrer" className={repoLink}>
                        {project.siteUrl.replace(/^https?:\/\//, '').replace(/\/$/, '')} ↗
                    </a>
                )}
            </div>
            {showStatus && project.status === 'REJECTED' && project.reviewNote && (
                <ReviewNote note={project.reviewNote} className="mt-3" />
            )}
        </article>
    )
}
