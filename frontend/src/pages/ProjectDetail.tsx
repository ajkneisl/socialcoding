import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useReviewProject } from '../features/board/queries'
import { usePeople } from '../features/people/queries'
import type { Person } from '../features/people/types'
import {
    useProjectDetail,
    useUpdateProjectDesign,
    useUpdateProjectMembers,
    useUpdateProjectTasks,
} from '../features/projects/queries'
import type { ProjectDetail as Detail } from '../features/projects/types'
import { ReviewNote, StatusBadge } from '../features/projects/StatusBadge'
import {
    DESIGN_SECTIONS,
    DeliverablesEditor,
    DesignDocQuestions,
    ProjectTimeline,
    TeamPicker,
    tasksToEditable,
    type DesignDoc,
    type EditableTask,
} from '../features/design'
import { useAuth } from '../auth-context'
import { Avatar } from '../components/Avatar'
import { Badge } from '../components/Badge'
import { Button } from '../components/Button'
import { Eyebrow } from '../components/Eyebrow'
import { FormActions } from '../components/FormActions'
import { FormError } from '../components/FormError'
import { NoticeCard } from '../components/NoticeCard'
import { PageMessage } from '../components/PageMessage'
import { card, page } from '../components/styles'

const sectionHead = 'mb-3 flex items-center justify-between gap-4'

function BoardReview({ detail }: { detail: Detail }) {
    const reviewProject = useReviewProject()
    const [note, setNote] = useState('')

    function review(decision: 'approve' | 'reject') {
        reviewProject.mutate({ id: detail.project.id, decision, note: note || undefined })
    }

    const busy = reviewProject.isPending
    const error = reviewProject.error?.message ?? null

    return (
        <div className={`${card} mb-10 flex flex-col gap-[0.9rem]`}>
            <h3 className="m-0">Board review</h3>
            <p className="m-0 text-text-soft">This design doc is awaiting a decision.</p>
            <label>
                Note to the team <span className="text-text-soft">(optional)</span>
                <input
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    placeholder="Feedback, conditions, next steps…"
                    maxLength={1000}
                />
            </label>
            <FormError error={error} />
            <div className="flex gap-[0.6rem]">
                <Button disabled={busy} onClick={() => review('approve')}>
                    Approve
                </Button>
                <Button variant="danger" disabled={busy} onClick={() => review('reject')}>
                    Reject
                </Button>
            </div>
        </div>
    )
}

function TeamSection({ detail, people }: { detail: Detail; people: Person[] }) {
    const updateMembers = useUpdateProjectMembers(detail.project.id)
    const [editing, setEditing] = useState(false)
    const [memberIds, setMemberIds] = useState<string[]>([])
    const [leadId, setLeadId] = useState(detail.teamLeadId)

    function startEditing() {
        setMemberIds(detail.members.map((m) => m.id))
        setLeadId(detail.teamLeadId)
        updateMembers.reset()
        setEditing(true)
    }

    async function save() {
        try {
            await updateMembers.mutateAsync({ memberIds, teamLeadId: leadId })
            setEditing(false)
        } catch {
            // error surfaced via updateMembers.error below
        }
    }

    const busy = updateMembers.isPending
    const error = updateMembers.error?.message ?? null

    return (
        <div className="mb-10">
            <div className={sectionHead}>
                <h3 className="m-0">Team</h3>
                {detail.canManageTeam && !editing && (
                    <Button variant="ghost" onClick={startEditing}>
                        Edit team
                    </Button>
                )}
            </div>
            {!detail.canManageTeam && detail.canEdit && (
                <p className="font-mono text-[0.8rem] text-text-soft">
                    only the team lead can modify the team
                </p>
            )}
            {editing ? (
                <div className="flex flex-col gap-[0.9rem]">
                    <TeamPicker
                        people={people}
                        memberIds={memberIds}
                        leadId={leadId}
                        onChange={(ids, lead) => {
                            setMemberIds(ids)
                            setLeadId(lead)
                        }}
                    />
                    <FormError error={error} />
                    <FormActions>
                        <Button variant="ghost" disabled={busy} onClick={() => setEditing(false)}>
                            Cancel
                        </Button>
                        <Button disabled={busy} onClick={save}>
                            {busy ? 'Saving…' : 'Save team'}
                        </Button>
                    </FormActions>
                </div>
            ) : (
                <ul className="flex flex-col gap-[0.45rem]">
                    {detail.members.map((m) => (
                        <li key={m.id} className="flex items-center gap-[0.65rem]">
                            <Avatar name={m.name} avatarUrl={m.avatarUrl} size="sm" />
                            <span className="font-medium">{m.name}</span>
                            {m.id === detail.teamLeadId && <Badge variant="board">team lead</Badge>}
                        </li>
                    ))}
                </ul>
            )}
        </div>
    )
}

function DesignDocSections({ detail }: { detail: Detail }) {
    const updateDesign = useUpdateProjectDesign(detail.project.id)
    const [editing, setEditing] = useState(false)
    const [title, setTitle] = useState('')
    const [description, setDescription] = useState('')
    const [repoUrl, setRepoUrl] = useState('')
    const [doc, setDoc] = useState<DesignDoc>(detail.designDoc)

    function startEditing() {
        setTitle(detail.project.title)
        setDescription(detail.project.description)
        setRepoUrl(detail.project.repoUrl ?? '')
        setDoc(detail.designDoc)
        updateDesign.reset()
        setEditing(true)
    }

    async function save() {
        try {
            await updateDesign.mutateAsync({
                title: title.trim(),
                description: description.trim(),
                repoUrl: repoUrl.trim() || undefined,
                designDoc: doc,
            })
            setEditing(false)
        } catch {
            // error surfaced via updateDesign.error below
        }
    }

    const busy = updateDesign.isPending
    const error = updateDesign.error?.message ?? null

    return (
        <div className="mb-10">
            <div className={sectionHead}>
                <h3 className="m-0">Design doc</h3>
                {detail.canEdit && !editing && (
                    <Button variant="ghost" onClick={startEditing}>
                        Edit answers
                    </Button>
                )}
            </div>

            {editing ? (
                <div className="flex flex-col gap-[0.9rem]">
                    <label>
                        Project name
                        <input
                            value={title}
                            onChange={(e) => setTitle(e.target.value)}
                            required
                            maxLength={200}
                        />
                    </label>
                    <label>
                        Project description
                        <textarea
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            required
                            rows={3}
                        />
                    </label>
                    <label>
                        GitHub link <span className="text-text-soft">(optional)</span>
                        <input
                            type="url"
                            value={repoUrl}
                            onChange={(e) => setRepoUrl(e.target.value)}
                            placeholder="https://github.com/…"
                        />
                    </label>
                    {DESIGN_SECTIONS.map((section) => (
                        <div key={section.id}>
                            <h4 className="mb-[0.15rem] mt-3">{section.title}</h4>
                            <DesignDocQuestions doc={doc} onChange={setDoc} section={section} />
                        </div>
                    ))}
                    <FormError error={error} />
                    <FormActions>
                        <Button variant="ghost" disabled={busy} onClick={() => setEditing(false)}>
                            Cancel
                        </Button>
                        <Button disabled={busy} onClick={save}>
                            {busy ? 'Saving…' : 'Save answers'}
                        </Button>
                    </FormActions>
                </div>
            ) : (
                DESIGN_SECTIONS.map((section) => (
                    <div key={section.id} className="mb-7">
                        <h4 className="mb-3 text-[0.85rem] uppercase tracking-[0.1em] text-gold">
                            {section.title}
                        </h4>
                        {section.questions.map((q) => (
                            <div key={q.field} className="mb-[0.9rem]">
                                <p className="mb-[0.15rem] font-semibold">{q.label}</p>
                                <p className="m-0 max-w-[75ch] whitespace-pre-wrap text-text-soft">
                                    {detail.designDoc[q.field] || 'Not answered yet.'}
                                </p>
                            </div>
                        ))}
                    </div>
                ))
            )}
        </div>
    )
}

function DeliverablesSection({ detail }: { detail: Detail }) {
    const updateTasks = useUpdateProjectTasks(detail.project.id)
    const [editing, setEditing] = useState(false)
    const [tasks, setTasks] = useState<EditableTask[]>([])

    function startEditing() {
        setTasks(tasksToEditable(detail.tasks))
        updateTasks.reset()
        setEditing(true)
    }

    async function save() {
        try {
            await updateTasks.mutateAsync(
                tasks
                    .filter((t) => t.name.trim() !== '')
                    .map((t) => ({ ...t, name: t.name.trim() })),
            )
            setEditing(false)
        } catch {
            // error surfaced via updateTasks.error below
        }
    }

    const busy = updateTasks.isPending
    const error = updateTasks.error?.message ?? null

    return (
        <div className="mb-10">
            <div className={sectionHead}>
                <h3 className="m-0">Timeline &amp; deliverables</h3>
                {detail.canEdit && !editing && (
                    <Button variant="ghost" onClick={startEditing}>
                        Edit deliverables
                    </Button>
                )}
            </div>
            {editing ? (
                <div className="flex flex-col gap-[0.9rem]">
                    <p className="m-0 text-text-soft">
                        The MVP and final presentations are required milestones — their dates can
                        change, but they can't be removed.
                    </p>
                    <DeliverablesEditor tasks={tasks} team={detail.members} onChange={setTasks} />
                    <FormError error={error} />
                    <FormActions>
                        <Button variant="ghost" disabled={busy} onClick={() => setEditing(false)}>
                            Cancel
                        </Button>
                        <Button disabled={busy} onClick={save}>
                            {busy ? 'Saving…' : 'Save deliverables'}
                        </Button>
                    </FormActions>
                </div>
            ) : (
                <ProjectTimeline tasks={detail.tasks} members={detail.members} />
            )}
        </div>
    )
}

export default function ProjectDetail() {
    const { id } = useParams()
    const { user, token, loading } = useAuth()
    const projectId = useMemo(() => Number(id), [id])
    const { data: detail, error } = useProjectDetail(projectId)
    const { data: people = [] } = usePeople()

    if (loading) {
        return <PageMessage>Loading…</PageMessage>
    }

    if (!user || !token) {
        return (
            <NoticeCard eyebrow="Project" title="Sign in first">
                <p className="text-text-soft">
                    <Link to="/account">Sign in</Link> to view project design docs.
                </p>
            </NoticeCard>
        )
    }

    if (error) {
        return (
            <section className={page}>
                <FormError error={error.message} />
            </section>
        )
    }

    if (!detail) {
        return <PageMessage>Loading…</PageMessage>
    }

    const lead = detail.members.find((m) => m.id === detail.teamLeadId)

    return (
        <section className={page}>
            <div className="mb-7 max-w-[680px]">
                <Eyebrow>Design doc</Eyebrow>
                <div className="flex flex-wrap items-center gap-3">
                    <h2 className="m-0">{detail.project.title}</h2>
                    <StatusBadge status={detail.project.status} />
                </div>
                <p className="my-2 text-text-soft">{detail.project.description}</p>
                <p className="my-2 font-mono text-[0.8rem] text-text-soft">
                    {lead && <>led by {lead.name} · </>}
                    submitted {new Date(detail.project.submittedAt).toLocaleDateString()}
                    {detail.project.repoUrl && (
                        <>
                            {' · '}
                            <a href={detail.project.repoUrl} target="_blank" rel="noreferrer">
                                GitHub ↗
                            </a>
                        </>
                    )}
                </p>
                {detail.project.status === 'REJECTED' && detail.project.reviewNote && (
                    <ReviewNote note={detail.project.reviewNote} />
                )}
            </div>

            {user.role === 'BOARD' && detail.project.status === 'PENDING' && (
                <BoardReview detail={detail} />
            )}

            <DeliverablesSection detail={detail} />
            <TeamSection detail={detail} people={people} />
            <DesignDocSections detail={detail} />
        </section>
    )
}
