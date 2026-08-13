import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
    useDeleteProject,
    usePendingProjects,
    useReviewProject,
} from '../../features/board/queries'
import { useProjects } from '../../features/projects/queries'
import type { PendingProject, Project } from '../../features/projects/types'
import { ReviewNote, StatusBadge } from '../../features/projects/StatusBadge'
import { Avatar } from '../../components/Avatar'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { FormError } from '../../components/FormError'
import { SectionHead } from '../../components/SectionHead'

const row = 'border-b border-line px-1 py-[1.4rem] hover:bg-bg-raised'

/** True when any of the fields contains the already-lowercased query. */
function matches(query: string, ...fields: (string | null | undefined)[]) {
    return fields.some((f) => f?.toLowerCase().includes(query))
}

/**
 * Deletes a project for good, behind an inline confirmation. Rejecting leaves a team able to
 * resubmit; this doesn't, so the second click spells out what goes with it.
 */
function DeleteProject({ project }: { project: Project }) {
    const deleteProject = useDeleteProject()
    const [confirming, setConfirming] = useState(false)

    if (!confirming) {
        return (
            <Button variant="ghost" onClick={() => setConfirming(true)}>
                Delete
            </Button>
        )
    }

    const busy = deleteProject.isPending

    return (
        <div className="flex flex-wrap items-center gap-[0.6rem]">
            <span className="font-mono text-[0.8rem] text-text-soft">
                Permanently delete “{project.title}” with its team, tasks, and design docs?
            </span>
            <Button variant="ghost" disabled={busy} onClick={() => setConfirming(false)}>
                Cancel
            </Button>
            <Button
                variant="danger"
                disabled={busy}
                onClick={() => deleteProject.mutate(project.id)}
            >
                {busy ? 'Deleting…' : 'Delete forever'}
            </Button>
            <FormError error={deleteProject.error?.message} />
        </div>
    )
}

function PendingProjectCard({ pending }: { pending: PendingProject }) {
    const reviewProject = useReviewProject()
    const [rejecting, setRejecting] = useState(false)
    const [note, setNote] = useState('')

    const { project, members, teamLeadID } = pending
    const lead = members.find((m) => m.id === teamLeadID)

    function approve() {
        reviewProject.mutate({ id: project.id, decision: 'approve' })
    }

    function reject() {
        reviewProject.mutate({ id: project.id, decision: 'reject', note: note.trim() || undefined })
    }

    const busy = reviewProject.isPending

    const rejected = project.status === 'REJECTED'

    return (
        <article className={row}>
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex flex-wrap items-center gap-3">
                    <h3 className="m-0">{project.title}</h3>
                    <StatusBadge status={project.status} />
                </div>
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
                <Link to={`/projects/${project.id}/doc`}>view full design doc →</Link>
                {project.repoUrl && (
                    <>
                        {' · '}
                        <a href={project.repoUrl} target="_blank" rel="noreferrer">
                            repository ↗
                        </a>
                    </>
                )}
            </p>
            <FormError error={reviewProject.error?.message} className="mt-2" />
            {rejected ? (
                <div className="mt-[0.6rem] flex flex-col items-start gap-[0.5rem]">
                    {project.reviewNote && <ReviewNote note={project.reviewNote} className="m-0" />}
                    <p className="m-0 font-mono text-[0.8rem] text-text-soft">
                        Waiting for the team to make changes and resubmit.
                    </p>
                    <DeleteProject project={project} />
                </div>
            ) : rejecting ? (
                <>
                    <label className="mt-[0.6rem]">
                        Feedback to submitter <span className="text-text-soft">(optional)</span>
                        <textarea
                            value={note}
                            onChange={(e) => setNote(e.target.value)}
                            placeholder="What should the team change before resubmitting?"
                            rows={3}
                            maxLength={1000}
                        />
                    </label>
                    <div className="mt-[0.9rem] flex gap-[0.6rem]">
                        <Button variant="ghost" disabled={busy} onClick={() => setRejecting(false)}>
                            Cancel
                        </Button>
                        <Button variant="danger" disabled={busy} onClick={reject}>
                            {busy ? 'Sending…' : 'Confirm rejection'}
                        </Button>
                    </div>
                </>
            ) : (
                <div className="mt-[0.9rem] flex flex-wrap items-center gap-[0.6rem]">
                    <Button disabled={busy} onClick={approve}>
                        Approve
                    </Button>
                    <Button variant="danger" disabled={busy} onClick={() => setRejecting(true)}>
                        Reject
                    </Button>
                    <DeleteProject project={project} />
                </div>
            )}
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
                    led by {project.teamLeadName} ·{' '}
                    {active ? 'shown on the home page' : 'listed under past projects'}
                </p>
            </div>
            <div className="flex flex-wrap items-center gap-[0.6rem]">
                <Button variant="ghost" disabled={reviewProject.isPending} onClick={toggle}>
                    {active ? 'Mark inactive' : 'Mark active'}
                </Button>
                <DeleteProject project={project} />
            </div>
        </article>
    )
}

export default function BoardProjects() {
    const { data: pending = [], error } = usePendingProjects()
    const { data: approved = [] } = useProjects()
    const [query, setQuery] = useState('')

    const q = query.trim().toLowerCase()

    const shownPending = useMemo(() => {
        if (!q) return pending
        return pending.filter(({ project, members }) =>
            matches(q, project.title, project.description, ...members.map((m) => m.name)),
        )
    }, [pending, q])

    const shownApproved = useMemo(() => {
        if (!q) return approved
        return approved.filter((p) => matches(q, p.title, p.description, p.teamLeadName))
    }, [approved, q])

    return (
        <>
            <SectionHead title="Projects awaiting approval">
                Review design docs awaiting a decision.
            </SectionHead>

            {pending.length + approved.length > 0 && (
                <input
                    type="search"
                    className="mb-6 w-full max-w-[440px] px-4 py-[0.6rem]"
                    placeholder="Search by title, description, or member…"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    aria-label="Search projects"
                />
            )}

            <FormError error={error?.message} />
            {shownPending.length === 0 ? (
                <p className="text-text-soft">
                    {q && pending.length > 0
                        ? 'No pending projects match your search.'
                        : 'Nothing awaiting approval..'}
                </p>
            ) : (
                <div className="border-t border-line">
                    {shownPending.map((p) => (
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
                    {shownApproved.length === 0 ? (
                        <p className="text-text-soft">No approved projects match your search.</p>
                    ) : (
                        <div className="border-t border-line">
                            {shownApproved.map((p) => (
                                <ApprovedProjectRow key={p.id} project={p} />
                            ))}
                        </div>
                    )}
                </>
            )}
        </>
    )
}
