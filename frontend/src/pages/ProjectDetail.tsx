import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useReviewProject } from '../features/board/queries'
import { usePeople } from '../features/people/queries'
import type { Person } from '../features/people/types'
import {
    useProjectDetail,
    useResubmitProject,
    useUpdateProjectDesign,
    useUpdateProjectMembers,
    useUpdateProjectTasks,
    useUpdateSemesterDoc,
} from '../features/projects/queries'
import type { ProjectDetail as Detail } from '../features/projects/types'
import { ReviewNote, StatusBadge } from '../features/projects/StatusBadge'
import {
    DESIGN_SECTIONS,
    RETURNING_SECTIONS,
    DeliverablesEditor,
    DesignDocAnswers,
    DesignDocQuestions,
    ProjectTimeline,
    TeamPicker,
    emptyDesignDoc,
    emptyReturningDoc,
    tasksToEditable,
    type DesignDoc,
    type DesignDocEntry,
    type EditableTask,
    type ReturningDoc,
} from '../features/design'
import { useAuth } from '../auth-context'
import { Avatar } from '../components/Avatar'
import { Badge } from '../components/Badge'
import { Button } from '../components/Button'
import { Eyebrow } from '../components/Eyebrow'
import { FormActions } from '../components/FormActions'
import { FormError } from '../components/FormError'
import { ImageUpload } from '../components/ImageUpload'
import { NoticeCard } from '../components/NoticeCard'
import { PageMessage } from '../components/PageMessage'
import { card, page } from '../components/styles'

const sectionHead = 'mb-3 flex items-center justify-between gap-4'

function BoardReview({ detail }: { detail: Detail }) {
    const reviewProject = useReviewProject()
    const [rejecting, setRejecting] = useState(false)
    const [note, setNote] = useState('')

    function approve() {
        reviewProject.mutate({ id: detail.project.id, decision: 'approve' })
    }

    function reject() {
        reviewProject.mutate({
            id: detail.project.id,
            decision: 'reject',
            note: note.trim() || undefined,
        })
    }

    const busy = reviewProject.isPending
    const error = reviewProject.error?.message ?? null

    return (
        <div className={`${card} mb-8 flex flex-col gap-[0.9rem]`}>
            <h3 className="m-0">Board review</h3>
            <p className="m-0 text-text-soft">This design doc is awaiting a decision.</p>
            <FormError error={error} />
            {rejecting ? (
                <>
                    <label>
                        Feedback to the team <span className="text-text-soft">(optional)</span>
                        <textarea
                            value={note}
                            onChange={(e) => setNote(e.target.value)}
                            placeholder="What should the team change before resubmitting?"
                            rows={3}
                            maxLength={1000}
                        />
                    </label>
                    <div className="flex gap-[0.6rem]">
                        <Button variant="ghost" disabled={busy} onClick={() => setRejecting(false)}>
                            Cancel
                        </Button>
                        <Button variant="danger" disabled={busy} onClick={reject}>
                            {busy ? 'Sending…' : 'Confirm rejection'}
                        </Button>
                    </div>
                </>
            ) : (
                <div className="flex gap-[0.6rem]">
                    <Button disabled={busy} onClick={approve}>
                        Approve
                    </Button>
                    <Button variant="danger" disabled={busy} onClick={() => setRejecting(true)}>
                        Reject
                    </Button>
                </div>
            )}
        </div>
    )
}

function RejectedNotice({ detail }: { detail: Detail }) {
    const resubmit = useResubmitProject(detail.project.id)

    return (
        <div className="mt-3 flex flex-col items-start gap-[0.6rem]">
            {detail.project.reviewNote && <ReviewNote note={detail.project.reviewNote} className="m-0" />}
            {detail.canManageTeam && (
                <>
                    <p className="m-0 text-text-soft">
                        Make any changes above, then send it back to the board.
                    </p>
                    <Button disabled={resubmit.isPending} onClick={() => resubmit.mutate()}>
                        {resubmit.isPending ? 'Resubmitting…' : 'Resubmit for review'}
                    </Button>
                    <FormError error={resubmit.error?.message ?? null} />
                </>
            )}
        </div>
    )
}

function TeamSection({ detail, people }: { detail: Detail; people: Person[] }) {
    const updateMembers = useUpdateProjectMembers(detail.project.id)
    const [editing, setEditing] = useState(false)
    const [memberIds, setMemberIds] = useState<string[]>([])
    const [leadId, setLeadId] = useState(detail.teamLeadId)

    // Seed with accepted members *and* outstanding invites, so re-saving the team keeps pending
    // invitees instead of silently dropping them.
    function startEditing() {
        setMemberIds([...detail.members, ...detail.pendingMembers].map((m) => m.id))
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
        <div className={card}>
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
                    <p className="m-0 text-[0.85rem] text-text-soft">
                        Search people to invite them. New teammates get a pending invite they accept
                        from their account; promote anyone to hand off the team lead role.
                    </p>
                    <TeamPicker
                        people={people}
                        memberIds={memberIds}
                        leadId={leadId}
                        pendingIds={detail.pendingMembers.map((m) => m.id)}
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
                <>
                    <ul className="flex flex-col gap-[0.45rem]">
                        {detail.members.map((m) => (
                            <li key={m.id} className="flex items-center gap-[0.65rem]">
                                <Avatar name={m.name} avatarUrl={m.avatarUrl} size="sm" />
                                <span className="font-medium">{m.name}</span>
                                {m.id === detail.teamLeadId && (
                                    <Badge variant="board">team lead</Badge>
                                )}
                            </li>
                        ))}
                    </ul>
                    {detail.pendingMembers.length > 0 && (
                        <ul className="mt-[0.45rem] flex flex-col gap-[0.45rem]">
                            {detail.pendingMembers.map((m) => (
                                <li
                                    key={m.id}
                                    className="flex items-center gap-[0.65rem] opacity-60"
                                >
                                    <Avatar name={m.name} avatarUrl={m.avatarUrl} size="sm" />
                                    <span className="font-medium">{m.name}</span>
                                    <Badge variant="pending">invite pending</Badge>
                                </li>
                            ))}
                        </ul>
                    )}
                </>
            )}
        </div>
    )
}

/** Edits the project's own fields. The answers on a design doc are edited separately, per semester. */
function ProjectDetailsForm({ detail, onDone }: { detail: Detail; onDone: () => void }) {
    const updateDesign = useUpdateProjectDesign(detail.project.id)
    const [title, setTitle] = useState(detail.project.title)
    const [description, setDescription] = useState(detail.project.description)
    const [repoUrl, setRepoUrl] = useState(detail.project.repoUrl ?? '')
    const [imageUrl, setImageUrl] = useState(detail.project.imageUrl ?? '')
    const [invalid, setInvalid] = useState(false)

    async function save() {
        // `required` stops empty fields, but not whitespace-only ones — which the server rejects.
        if (title.trim() === '' || description.trim() === '') {
            setInvalid(true)
            return
        }
        setInvalid(false)
        try {
            // No designDoc: this form doesn't touch the answers, whichever semester they're from.
            await updateDesign.mutateAsync({
                title: title.trim(),
                description: description.trim(),
                repoUrl: repoUrl.trim() || undefined,
                imageUrl: imageUrl || undefined,
            })
            onDone()
        } catch {
            // error surfaced via updateDesign.error below
        }
    }

    const busy = updateDesign.isPending
    const error = invalid
        ? 'Project name and description are required.'
        : (updateDesign.error?.message ?? null)

    return (
        <form
            className="flex flex-col gap-[0.9rem]"
            onSubmit={(e) => {
                e.preventDefault()
                save()
            }}
        >
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
            <label>
                Cover image <span className="text-text-soft">(optional)</span>
                <ImageUpload value={imageUrl} onChange={setImageUrl} />
            </label>
            {detail.project.status === 'APPROVED' && (
                <p className="m-0 text-text-soft">
                    These show on the public project page, so saving a change here sends the
                    project back to the board for approval.
                </p>
            )}
            <FormError error={error} />
            <FormActions>
                <Button variant="ghost" disabled={busy} onClick={onDone}>
                    Cancel
                </Button>
                <Button type="submit" disabled={busy}>
                    {busy ? 'Saving…' : 'Save details'}
                </Button>
            </FormActions>
        </form>
    )
}

/** Edits the proposal answers, which stay editable for the semester the project started in. */
function ProposalForm({
    detail,
    entry,
    onDone,
}: {
    detail: Detail
    entry: DesignDocEntry
    onDone: () => void
}) {
    const updateDesign = useUpdateProjectDesign(detail.project.id)
    const [doc, setDoc] = useState<DesignDoc>(entry.initial ?? emptyDesignDoc())

    async function save() {
        try {
            await updateDesign.mutateAsync({
                title: detail.project.title,
                description: detail.project.description,
                repoUrl: detail.project.repoUrl ?? undefined,
                imageUrl: detail.project.imageUrl ?? undefined,
                designDoc: doc,
            })
            onDone()
        } catch {
            // error surfaced via updateDesign.error below
        }
    }

    const busy = updateDesign.isPending

    return (
        <div className="flex flex-col gap-[0.9rem]">
            {DESIGN_SECTIONS.map((section) => (
                <div key={section.id} className="flex flex-col gap-[0.9rem]">
                    <div>
                        <h4 className="mb-[0.15rem] mt-3">{section.title}</h4>
                        <p className="m-0 text-text-soft">{section.blurb}</p>
                    </div>
                    <DesignDocQuestions doc={doc} onChange={setDoc} section={section} />
                </div>
            ))}
            <FormError error={updateDesign.error?.message ?? null} />
            <FormActions>
                <Button variant="ghost" disabled={busy} onClick={onDone}>
                    Cancel
                </Button>
                <Button disabled={busy} onClick={save}>
                    {busy ? 'Saving…' : 'Save answers'}
                </Button>
            </FormActions>
        </div>
    )
}

/**
 * Fills in this semester's returning doc. Filing the first one is how a project comes back for
 * another semester, so it goes to the board — later saves just replace the answers.
 */
function SemesterDocForm({
    detail,
    entry,
    onDone,
}: {
    detail: Detail
    entry?: DesignDocEntry
    onDone: () => void
}) {
    const save = useUpdateSemesterDoc(detail.project.id)
    const [doc, setDoc] = useState<ReturningDoc>(entry?.returning ?? emptyReturningDoc())

    async function submit() {
        try {
            await save.mutateAsync(doc)
            onDone()
        } catch {
            // error surfaced via save.error below
        }
    }

    const busy = save.isPending

    return (
        <div className="flex flex-col gap-[0.9rem]">
            {!entry && (
                <p className="m-0 text-text-soft">
                    Filing this sends {detail.project.title} back to the board for{' '}
                    {detail.currentSemester}. You can keep editing your answers while they review.
                </p>
            )}
            {RETURNING_SECTIONS.map((section) => (
                <div key={section.id}>
                    <h4 className="mb-[0.15rem] mt-3">{section.title}</h4>
                    <p className="m-0 text-[0.85rem] text-text-soft">{section.blurb}</p>
                    <DesignDocQuestions doc={doc} onChange={setDoc} section={section} />
                </div>
            ))}
            <FormError error={save.error?.message ?? null} />
            <FormActions>
                <Button variant="ghost" disabled={busy} onClick={onDone}>
                    Cancel
                </Button>
                <Button disabled={busy} onClick={submit}>
                    {busy ? 'Saving…' : entry ? 'Save answers' : `File ${detail.currentSemester} doc`}
                </Button>
            </FormActions>
        </div>
    )
}

/** A doc from a semester that's over: listed, but folded away until someone asks for it. */
function PastDoc({ entry }: { entry: DesignDocEntry }) {
    return (
        <details className={`${card} [&[open]>summary]:mb-5`}>
            <summary className="flex cursor-pointer list-none flex-wrap items-center gap-3 [&::-webkit-details-marker]:hidden">
                <span className="font-semibold">{entry.semester}</span>
                <Badge variant="inactive">
                    {entry.kind === 'INITIAL' ? 'proposal' : 'semester doc'}
                </Badge>
                <span className="ml-auto font-mono text-[0.8rem] text-text-soft">
                    filed {new Date(entry.submittedAt).toLocaleDateString()}
                </span>
            </summary>
            <DesignDocAnswers entry={entry} />
        </details>
    )
}

/**
 * The project's design docs: this semester's expanded and editable, every earlier one kept but
 * collapsed, so the page reads as what the team is doing now with its history underneath.
 */
function DesignDocsSection({ detail }: { detail: Detail }) {
    const [editing, setEditing] = useState(false)
    const current = detail.designDocs.find((d) => d.semester === detail.currentSemester)
    const past = detail.designDocs.filter((d) => d.semester !== detail.currentSemester)

    return (
        <>
            <div className={card}>
                <div className={sectionHead}>
                    <div>
                        <h3 className="m-0">Design doc</h3>
                        <p className="m-0 mt-1 font-mono text-[0.8rem] text-text-soft">
                            {detail.currentSemester}
                            {current?.kind === 'INITIAL' && ' · project proposal'}
                        </p>
                    </div>
                    {detail.canEdit && !editing && current && (
                        <Button variant="ghost" onClick={() => setEditing(true)}>
                            Edit answers
                        </Button>
                    )}
                </div>

                {editing && current?.kind === 'INITIAL' ? (
                    <ProposalForm
                        detail={detail}
                        entry={current}
                        onDone={() => setEditing(false)}
                    />
                ) : editing ? (
                    <SemesterDocForm
                        detail={detail}
                        entry={current}
                        onDone={() => setEditing(false)}
                    />
                ) : current ? (
                    <DesignDocAnswers entry={current} />
                ) : (
                    <div className="flex flex-col items-start gap-[0.9rem]">
                        <p className="m-0 text-text-soft">
                            This project hasn't filed a design doc for {detail.currentSemester} yet.
                            Returning projects write up what they got done last semester and what
                            they're taking on now.
                        </p>
                        {detail.canEdit && (
                            <Button onClick={() => setEditing(true)}>
                                Start {detail.currentSemester} design doc
                            </Button>
                        )}
                    </div>
                )}
            </div>

            {past.length > 0 && (
                <div className="mt-8">
                    <Eyebrow>Past semesters</Eyebrow>
                    <div className="mt-3 flex flex-col gap-3">
                        {past.map((entry) => (
                            <PastDoc key={entry.id} entry={entry} />
                        ))}
                    </div>
                </div>
            )}
        </>
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
        <div className={card}>
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
                        The MVP and final presentations are required milestones — they can't be
                        removed, and their dates come from the board, set when you filed this
                        semester's design doc.
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
    const projectId = id ?? ''
    const { data: detail, error } = useProjectDetail(projectId)
    const { data: people = [] } = usePeople()
    const [editingDetails, setEditingDetails] = useState(false)

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
            <div className={`${card} mb-8`}>
                {editingDetails ? (
                    <ProjectDetailsForm
                        detail={detail}
                        onDone={() => setEditingDetails(false)}
                    />
                ) : (
                    <>
                        <div className="flex items-start justify-between gap-5">
                            <div className="min-w-0">
                                <Eyebrow>Design doc</Eyebrow>
                                <div className="flex flex-wrap items-center gap-3">
                                    <h2 className="m-0">{detail.project.title}</h2>
                                    <StatusBadge status={detail.project.status} />
                                </div>
                                <p className="my-2 max-w-[68ch] text-text-soft">
                                    {detail.project.description}
                                </p>
                                <p className="my-2 font-mono text-[0.8rem] text-text-soft">
                                    {lead && <>led by {lead.name} · </>}
                                    submitted{' '}
                                    {new Date(detail.project.submittedAt).toLocaleDateString()}
                                    {detail.project.repoUrl && (
                                        <>
                                            {' · '}
                                            <a
                                                href={detail.project.repoUrl}
                                                target="_blank"
                                                rel="noreferrer"
                                            >
                                                GitHub ↗
                                            </a>
                                        </>
                                    )}
                                </p>
                            </div>
                            <div className="flex shrink-0 items-start gap-3">
                                {detail.project.imageUrl && (
                                    <img
                                        src={detail.project.imageUrl}
                                        alt=""
                                        className="h-20 w-20 rounded-xl border border-line bg-bg-raised object-contain p-2"
                                    />
                                )}
                                {detail.canEdit && (
                                    <Button
                                        variant="ghost"
                                        onClick={() => setEditingDetails(true)}
                                    >
                                        Edit details
                                    </Button>
                                )}
                            </div>
                        </div>
                        {detail.project.status === 'REJECTED' && (
                            <RejectedNotice detail={detail} />
                        )}
                    </>
                )}
            </div>

            {user.role === 'BOARD' && detail.project.status === 'PENDING' && (
                <BoardReview detail={detail} />
            )}

            <div className="mb-8 grid gap-8 md:grid-cols-2 md:items-start">
                <DeliverablesSection detail={detail} />
                <TeamSection detail={detail} people={people} />
            </div>
            <DesignDocsSection detail={detail} />
        </section>
    )
}
