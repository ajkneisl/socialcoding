import { useState } from 'react'
import { Link } from 'react-router-dom'
import { usePendingProjects, useReviewProject } from '../../features/board/queries'
import { useProjects } from '../../features/projects/queries'
import type { PendingProject, Project } from '../../features/projects/types'
import { Avatar } from '../../components/Avatar'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { FormError } from '../../components/FormError'
import { SectionHead } from '../../components/SectionHead'

const row = 'border-b border-line px-1 py-[1.4rem] hover:bg-bg-raised'

function PendingProjectCard({ pending }: { pending: PendingProject }) {
    const reviewProject = useReviewProject()
    const [note, setNote] = useState('')

    const { project, members, teamLeadID } = pending
    const lead = members.find((m) => m.id === teamLeadID)

    function review(decision: 'approve' | 'reject') {
        reviewProject.mutate({ id: project.id, decision, note: note || undefined })
    }

    const busy = reviewProject.isPending

    return (
        <article className={row}>
            <div className="flex flex-wrap items-center justify-between gap-3">
                <h3 className="m-0">{project.title}</h3>
                <span className="font-mono text-[0.8rem] text-text-soft">
                    {new Date(project.submittedAt).toLocaleDateString()}
                    {lead && <> · led by {lead.name}</>}
                </span>
            </div>
            <p className="mb-2 mt-[0.35rem] max-w-[75ch] text-text-soft">{project.description}</p>
            <ul className="my-3 flex flex-wrap gap-x-4 gap-y-2">
                {members.map((m) => (
                    <li key={m.id} className="flex items-center gap-[0.5rem]">
                        <Avatar name={m.name} avatarUrl={m.avatarUrl} size="sm" />
                        <span className="text-[0.9rem] font-medium">{m.name}</span>
                        {m.id === teamLeadID && <Badge variant="board">team lead</Badge>}
                    </li>
                ))}
            </ul>
            <p className="mb-2 mt-[0.35rem] font-mono text-[0.8rem]">
                <Link to={`/projects/${project.id}`}>view full design doc →</Link>
                {project.repoUrl && (
                    <>
                        {' · '}
                        <a href={project.repoUrl} target="_blank" rel="noreferrer">
                            repository ↗
                        </a>
                    </>
                )}
            </p>
            <label className="mt-[0.6rem]">
                Note to submitter <span className="text-text-soft">(optional)</span>
                <input
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    placeholder="Feedback, conditions, next steps…"
                    maxLength={1000}
                />
            </label>
            <FormError error={reviewProject.error?.message} className="mt-2" />
            <div className="mt-[0.9rem] flex gap-[0.6rem]">
                <Button disabled={busy} onClick={() => review('approve')}>
                    Approve
                </Button>
                <Button variant="danger" disabled={busy} onClick={() => review('reject')}>
                    Reject
                </Button>
            </div>
        </article>
    )
}

function ApprovedProjectRow({ project }: { project: Project }) {
    const reviewProject = useReviewProject()
    const active = project.active

    function toggle() {
        reviewProject.mutate({
            id: project.id,
            decision: active ? 'deactivate' : 'activate',
        })
    }

    return (
        <article className={`${row} flex flex-wrap items-center justify-between gap-4`}>
            <div>
                <h3 className="m-0 text-base">{project.title}</h3>
                <p className="mb-0 mt-[0.1rem] font-mono text-[0.8rem] text-text-soft">
                    led by {project.ownerName} ·{' '}
                    {active ? 'shown on the home page' : 'listed under past projects'}
                </p>
            </div>
            <Button variant="ghost" disabled={reviewProject.isPending} onClick={toggle}>
                {active ? 'Mark inactive' : 'Mark active'}
            </Button>
        </article>
    )
}

export default function BoardProjects() {
    const { data: pending = [], error } = usePendingProjects()
    const { data: approved = [] } = useProjects()

    return (
        <>
            <SectionHead title="Pending projects">Review project's design docs.</SectionHead>
            <FormError error={error?.message} />
            {pending.length === 0 ? (
                <p className="text-text-soft">Queue is empty. Nice work.</p>
            ) : (
                <div className="border-t border-line">
                    {pending.map((p) => (
                        <PendingProjectCard key={p.project.id} pending={p} />
                    ))}
                </div>
            )}

            {approved.length > 0 && (
                <>
                    <SectionHead title="Approved projects" className="mt-14">
                        Active projects appear on the home page; inactive ones move to “past
                        projects.”
                    </SectionHead>
                    <div className="border-t border-line">
                        {approved.map((p) => (
                            <ApprovedProjectRow key={p.id} project={p} />
                        ))}
                    </div>
                </>
            )}
        </>
    )
}
