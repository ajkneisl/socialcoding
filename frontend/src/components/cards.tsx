import { Link } from 'react-router-dom'
import type { Person } from '../features/people/types'
import type { Project, ProjectStatus } from '../features/projects/types'
import { initials } from '../util'

export function StatusBadge({ status }: { status: ProjectStatus }) {
  const label = {
    PENDING: 'Awaiting board review',
    APPROVED: 'Approved',
    REJECTED: 'Not approved',
  }[status]
  return <span className={`badge badge-${status.toLowerCase()}`}>{label}</span>
}

export function ActiveBadge({ active }: { active: boolean }) {
  return (
    <span className={`badge ${active ? 'badge-approved' : 'badge-inactive'}`}>
      {active ? 'Active' : 'Inactive'}
    </span>
  )
}

export function ProjectCard({
  project,
  showStatus,
  linkToDoc,
}: {
  project: Project
  showStatus?: boolean
  linkToDoc?: boolean
}) {
  return (
    <article className="card project-card">
      <div className="card-top">
        <h3>
          {linkToDoc ? <Link to={`/projects/${project.id}`}>{project.title}</Link> : project.title}
        </h3>
        {showStatus ? <StatusBadge status={project.status} /> : (
          <ActiveBadge active={project.active} />
        )}
      </div>
      <p>{project.description}</p>
      <div className="card-meta mono">
        <span>led by {project.ownerName}</span>
        {project.siteUrl && (
          <a href={project.siteUrl} target="_blank" rel="noreferrer" className="repo-link">
            {project.siteUrl.replace(/^https?:\/\//, '').replace(/\/$/, '')} ↗
          </a>
        )}
        {project.repoUrl && (
          <a href={project.repoUrl} target="_blank" rel="noreferrer" className="repo-link">
            <svg viewBox="0 0 16 16" width="15" height="15" fill="currentColor" aria-hidden="true">
              <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.42 7.42 0 0 1 2-.27c.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z" />
            </svg>
            GitHub
          </a>
        )}
      </div>
      {showStatus && project.status === 'REJECTED' && project.reviewNote && (
        <p className="review-note">Board feedback: {project.reviewNote}</p>
      )}
    </article>
  )
}

export function PersonCard({ person }: { person: Person }) {
  return (
    <article className="card person-card">
      {person.avatarUrl ? (
        <img className="avatar" src={person.avatarUrl} alt="" referrerPolicy="no-referrer" />
      ) : (
        <div className="avatar avatar-initials" aria-hidden="true">
          {initials(person.name)}
        </div>
      )}
      <div className="person-info">
        <h3>{person.name}</h3>
        {person.role === 'BOARD' && (
          <p className="person-title">{person.title ?? 'Board'}</p>
        )}
        <p className="mono muted">
          {[
            person.joinedTerm && `joined ${person.joinedTerm}`,
            person.gradYear && `class of ${person.gradYear}`,
          ]
            .filter(Boolean)
            .join(' · ') || 'member'}
        </p>
        {person.company && <p className="mono person-company">{person.company}</p>}
        <div className="person-links mono">
          {person.github && (
            <a href={`https://github.com/${person.github}`} target="_blank" rel="noreferrer">
              github
            </a>
          )}
          {person.linkedin && (
            <a href={person.linkedin} target="_blank" rel="noreferrer">
              linkedin
            </a>
          )}
          {person.website && (
            <a href={person.website} target="_blank" rel="noreferrer">
              portfolio
            </a>
          )}
        </div>
      </div>
    </article>
  )
}
