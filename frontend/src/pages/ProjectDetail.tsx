import {useEffect, useMemo, useState} from 'react'
import {Link, useParams} from 'react-router-dom'
import {boardApi} from '../features/board/api'
import {peopleApi} from '../features/people/api'
import type {Person} from '../features/people/types'
import {projectsApi} from '../features/projects/api'
import type {ProjectDetail as Detail} from '../features/projects/types'
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
import {StatusBadge} from '../components/cards'
import {useAuth} from '../auth-context'
import {initials} from '../util'

function BoardReview({detail, onReviewed}: { detail: Detail; onReviewed: () => void }) {
    const {token} = useAuth()
    const [note, setNote] = useState('')
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState<string | null>(null)

    async function review(decision: 'approve' | 'reject') {
        if (!token) return
        setBusy(true)
        setError(null)
        try {
            await boardApi.reviewProject(token, detail.project.id, decision, note || undefined)
            onReviewed()
        } catch (err) {
            setError((err as Error).message)
            setBusy(false)
        }
    }

    return (
        <div className="card form board-review">
            <h3>Board review</h3>
            <p className="muted">This design doc is awaiting a decision.</p>
            <label>
                Note to the team <span className="muted">(optional)</span>
                <input
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    placeholder="Feedback, conditions, next steps…"
                    maxLength={1000}
                />
            </label>
            {error && <p className="form-error">{error}</p>}
            <div className="review-actions">
                <button className="btn btn-primary" disabled={busy} onClick={() => review('approve')}>
                    Approve
                </button>
                <button className="btn btn-danger" disabled={busy} onClick={() => review('reject')}>
                    Reject
                </button>
            </div>
        </div>
    )
}

function TeamSection({
                         detail,
                         people,
                         onUpdated,
                     }: {
    detail: Detail
    people: Person[]
    onUpdated: (d: Detail) => void
}) {
    const {token} = useAuth()
    const [editing, setEditing] = useState(false)
    const [memberIds, setMemberIds] = useState<number[]>([])
    const [leadId, setLeadId] = useState(detail.teamLeadId)
    const [error, setError] = useState<string | null>(null)
    const [busy, setBusy] = useState(false)

    function startEditing() {
        setMemberIds(detail.members.map((m) => m.id))
        setLeadId(detail.teamLeadId)
        setError(null)
        setEditing(true)
    }

    async function save() {
        if (!token) return
        setBusy(true)
        setError(null)
        try {
            const updated = await projectsApi.updateMembers(token, detail.project.id, {memberIds, teamLeadId: leadId})
            if (updated) onUpdated(updated)
            setEditing(false)
        } catch (err) {
            setError((err as Error).message)
        } finally {
            setBusy(false)
        }
    }

    return (
        <div className="doc-section">
            <div className="doc-section-head">
                <h3>Team</h3>
                {detail.canManageTeam && !editing && (
                    <button className="btn btn-ghost" onClick={startEditing}>
                        Edit team
                    </button>
                )}
            </div>
            {!detail.canManageTeam && detail.canEdit && (
                <p className="muted mono">only the team lead can modify the team</p>
            )}
            {editing ? (
                <div className="form">
                    <TeamPicker
                        people={people}
                        memberIds={memberIds}
                        leadId={leadId}
                        onChange={(ids, lead) => {
                            setMemberIds(ids)
                            setLeadId(lead)
                        }}
                    />
                    {error && <p className="form-error">{error}</p>}
                    <div className="step-actions">
                        <button className="btn btn-ghost" disabled={busy} onClick={() => setEditing(false)}>
                            Cancel
                        </button>
                        <button className="btn btn-primary" disabled={busy} onClick={save}>
                            {busy ? 'Saving…' : 'Save team'}
                        </button>
                    </div>
                </div>
            ) : (
                <ul className="member-list">
                    {detail.members.map((m) => (
                        <li key={m.id} className="member-row">
                            {m.avatarUrl ? (
                                <img className="avatar avatar-sm" src={m.avatarUrl} alt=""
                                     referrerPolicy="no-referrer"/>
                            ) : (
                                <div className="avatar avatar-sm avatar-initials">{initials(m.name)}</div>
                            )}
                            <span className="member-name">{m.name}</span>
                            {m.id === detail.teamLeadId && <span className="badge badge-board">team lead</span>}
                        </li>
                    ))}
                </ul>
            )}
        </div>
    )
}

function DesignDocSections({detail, onUpdated}: { detail: Detail; onUpdated: (d: Detail) => void }) {
    const {token} = useAuth()
    const [editing, setEditing] = useState(false)
    const [title, setTitle] = useState('')
    const [description, setDescription] = useState('')
    const [repoUrl, setRepoUrl] = useState('')
    const [doc, setDoc] = useState<DesignDoc>(detail.designDoc)
    const [error, setError] = useState<string | null>(null)
    const [busy, setBusy] = useState(false)

    function startEditing() {
        setTitle(detail.project.title)
        setDescription(detail.project.description)
        setRepoUrl(detail.project.repoUrl ?? '')
        setDoc(detail.designDoc)
        setError(null)
        setEditing(true)
    }

    async function save() {
        if (!token) return
        setBusy(true)
        setError(null)
        try {
            const updated = await projectsApi.updateDesign(token, detail.project.id, {
                title: title.trim(),
                description: description.trim(),
                repoUrl: repoUrl.trim() || undefined,
                designDoc: doc,
            })
            onUpdated(updated)
            setEditing(false)
        } catch (err) {
            setError((err as Error).message)
        } finally {
            setBusy(false)
        }
    }

    return (
        <div className="doc-section">
            <div className="doc-section-head">
                <h3>Design doc</h3>
                {detail.canEdit && !editing && (
                    <button className="btn btn-ghost" onClick={startEditing}>
                        Edit answers
                    </button>
                )}
            </div>

            {editing ? (
                <div className="form">
                    <label>
                        Project name
                        <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={200}/>
                    </label>
                    <label>
                        Project description
                        <textarea value={description} onChange={(e) => setDescription(e.target.value)} required
                                  rows={3}/>
                    </label>
                    <label>
                        GitHub link <span className="muted">(optional)</span>
                        <input type="url" value={repoUrl} onChange={(e) => setRepoUrl(e.target.value)}
                               placeholder="https://github.com/…"/>
                    </label>
                    {DESIGN_SECTIONS.map((section) => (
                        <div key={section.id}>
                            <h4>{section.title}</h4>
                            <DesignDocQuestions doc={doc} onChange={setDoc} section={section}/>
                        </div>
                    ))}
                    {error && <p className="form-error">{error}</p>}
                    <div className="step-actions">
                        <button className="btn btn-ghost" disabled={busy} onClick={() => setEditing(false)}>
                            Cancel
                        </button>
                        <button className="btn btn-primary" disabled={busy} onClick={save}>
                            {busy ? 'Saving…' : 'Save answers'}
                        </button>
                    </div>
                </div>
            ) : (
                DESIGN_SECTIONS.map((section) => (
                    <div key={section.id} className="qa-section">
                        <h4>{section.title}</h4>
                        {section.questions.map((q) => (
                            <div key={q.field} className="qa">
                                <p className="qa-q">{q.label}</p>
                                <p className={detail.designDoc[q.field] ? 'qa-a' : 'qa-a muted'}>
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

function DeliverablesSection({detail, onUpdated}: { detail: Detail; onUpdated: (d: Detail) => void }) {
    const {token} = useAuth()
    const [editing, setEditing] = useState(false)
    const [tasks, setTasks] = useState<EditableTask[]>([])
    const [error, setError] = useState<string | null>(null)
    const [busy, setBusy] = useState(false)

    function startEditing() {
        setTasks(tasksToEditable(detail.tasks))
        setError(null)
        setEditing(true)
    }

    async function save() {
        if (!token) return
        setBusy(true)
        setError(null)
        try {
            const updated = await projectsApi.updateTasks(
                token,
                detail.project.id,
                tasks.filter((t) => t.name.trim() !== '').map((t) => ({...t, name: t.name.trim()})),
            )
            onUpdated(updated)
            setEditing(false)
        } catch (err) {
            setError((err as Error).message)
        } finally {
            setBusy(false)
        }
    }

    return (
        <div className="doc-section">
            <div className="doc-section-head">
                <h3>Timeline &amp; deliverables</h3>
                {detail.canEdit && !editing && (
                    <button className="btn btn-ghost" onClick={startEditing}>
                        Edit deliverables
                    </button>
                )}
            </div>
            {editing ? (
                <div className="form">
                    <p className="muted">
                        The MVP and final presentations are required milestones — their dates can change, but
                        they can't be removed.
                    </p>
                    <DeliverablesEditor tasks={tasks} team={detail.members} onChange={setTasks}/>
                    {error && <p className="form-error">{error}</p>}
                    <div className="step-actions">
                        <button className="btn btn-ghost" disabled={busy} onClick={() => setEditing(false)}>
                            Cancel
                        </button>
                        <button className="btn btn-primary" disabled={busy} onClick={save}>
                            {busy ? 'Saving…' : 'Save deliverables'}
                        </button>
                    </div>
                </div>
            ) : (
                <ProjectTimeline tasks={detail.tasks} members={detail.members}/>
            )}
        </div>
    )
}

export default function ProjectDetail() {
    const {id} = useParams()
    const {user, token, loading} = useAuth()
    const [detail, setDetail] = useState<Detail | null>(null)
    const [people, setPeople] = useState<Person[]>([])
    const [error, setError] = useState<string | null>(null)

    const projectId = useMemo(() => Number(id), [id])

    useEffect(() => {
        if (!token || Number.isNaN(projectId)) return
        projectsApi
            .detail(token, projectId)
            .then(setDetail)
            .catch((e: Error) => setError(e.message))
    }, [token, projectId])

    useEffect(() => {
        peopleApi.list().then(setPeople).catch(() => setPeople([]))
    }, [])

    function reload() {
        if (token) projectsApi.detail(token, projectId).then(setDetail).catch(() => undefined)
    }

    if (loading) {
        return (
            <section className="section container page">
                <p className="muted">Loading…</p>
            </section>
        )
    }

    if (!user || !token) {
        return (
            <section className="section container page narrow">
                <div className="card signin-card">
                    <p className="eyebrow mono">Project</p>
                    <h2>Sign in first</h2>
                    <p className="muted">
                        <Link to="/account">Sign in</Link> to view project design docs.
                    </p>
                </div>
            </section>
        )
    }

    if (error) {
        return (
            <section className="section container page">
                <p className="form-error">{error}</p>
            </section>
        )
    }

    if (!detail) {
        return (
            <section className="section container page">
                <p className="muted">Loading…</p>
            </section>
        )
    }

    const lead = detail.members.find((m) => m.id === detail.teamLeadId)

    return (
        <section className="section container page">
            <div className="section-head">
                <p className="eyebrow mono">Design doc</p>
                <div className="doc-title">
                    <h2>{detail.project.title}</h2>
                    <StatusBadge status={detail.project.status}/>
                </div>
                <p className="muted">{detail.project.description}</p>
                <p className="mono muted">
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
                    <p className="review-note">Board feedback: {detail.project.reviewNote}</p>
                )}
            </div>

            {user.role === 'BOARD' && detail.project.status === 'PENDING' && (
                <BoardReview detail={detail} onReviewed={reload}/>
            )}

            <DeliverablesSection detail={detail} onUpdated={setDetail}/>
            <TeamSection detail={detail} people={people} onUpdated={setDetail}/>
            <DesignDocSections detail={detail} onUpdated={setDetail}/>
        </section>
    )
}
