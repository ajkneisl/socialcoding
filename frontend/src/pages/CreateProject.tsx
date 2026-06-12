import {useEffect, useMemo, useState} from 'react'
import {useNavigate} from 'react-router-dom'
import {peopleApi} from '../features/people/api'
import type {Person} from '../features/people/types'
import {projectsApi} from '../features/projects/api'
import {
    DESIGN_SECTIONS,
    DeliverablesEditor,
    DesignDocQuestions,
    TeamPicker,
    emptyDesignDoc,
    requiredMilestones,
    type DesignDoc,
    type EditableTask,
} from '../features/design'
import {useAuth} from '../auth-context'

const STEPS = ['Details', 'About your Project', 'Architecture', 'Teamwork', 'Deliverables']

export default function CreateProject() {
    const {user, token, loading} = useAuth()
    const navigate = useNavigate()

    const [step, setStep] = useState(0)
    const [people, setPeople] = useState<Person[]>([])

    const [title, setTitle] = useState('')
    const [description, setDescription] = useState('')
    const [repoUrl, setRepoUrl] = useState('')
    const [memberIds, setMemberIds] = useState<number[]>([])
    const [leadId, setLeadId] = useState<number>(0)
    const [doc, setDoc] = useState<DesignDoc>(emptyDesignDoc())
    const [tasks, setTasks] = useState<EditableTask[]>(requiredMilestones())

    const [error, setError] = useState<string | null>(null)
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        peopleApi.list().then(setPeople).catch(() => setPeople([]))
    }, [])

    // You're always on your own team, and lead by default.
    useEffect(() => {
        if (user && memberIds.length === 0) {
            setMemberIds([user.id])
            setLeadId(user.id)
        }
    }, [user, memberIds.length])

    const team = useMemo(
        () =>
            memberIds
                .map((id) => people.find((p) => p.id === id))
                .filter((p): p is Person => p !== undefined)
                .map((p) => ({id: p.id, name: p.name, avatarUrl: p.avatarUrl})),
        [memberIds, people],
    )

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
                    <p className="eyebrow mono">Design doc</p>
                    <h2>Sign in first</h2>
                    <p className="muted">You need to be signed in to start a project design doc.</p>
                </div>
            </section>
        )
    }

    const detailsValid = title.trim() !== '' && description.trim() !== ''

    function next() {
        if (step === 0 && !detailsValid) {
            setError('Project name and description are required.')
            return
        }
        setError(null)
        setStep((s) => Math.min(s + 1, STEPS.length - 1))
    }

    async function submit() {
        if (!detailsValid) {
            setError('Project name and description are required.')
            setStep(0)
            return
        }
        setSubmitting(true)
        setError(null)
        try {
            const detail = await projectsApi.create(token!, {
                title: title.trim(),
                description: description.trim(),
                repoUrl: repoUrl.trim() || undefined,
                teamLeadId: leadId,
                memberIds,
                designDoc: doc,
                tasks: tasks
                    .filter((t) => t.name.trim() !== '')
                    .map((t) => ({...t, name: t.name.trim()})),
            })
            navigate(`/projects/${detail.project.id}`)
        } catch (err) {
            setError((err as Error).message)
            setSubmitting(false)
        }
    }

    return (
        <section className="section container page">
            <div className="section-head">
                <p className="eyebrow mono">New project</p>
                <h2>Project design doc</h2>
                <p className="muted">
                    Every Social Coding project starts with a design doc. Work through each section — the
                    board reviews it, and once approved your project goes live. Your team can keep editing
                    answers afterward.
                </p>
            </div>

            <nav className="steps" aria-label="Design doc sections">
                {STEPS.map((label, i) => (
                    <button
                        key={label}
                        type="button"
                        className={`step${i === step ? ' step-active' : ''}`}
                        onClick={() => setStep(i)}
                    >
                        <span className="step-num mono">{i + 1}</span> {label}
                    </button>
                ))}
            </nav>

            <form
                className="card form designdoc-form"
                onSubmit={(e) => {
                    e.preventDefault()
                    if (step < STEPS.length - 1) next()
                    else submit()
                }}
            >
                {step === 0 && (
                    <>
                        <h3>Details</h3>
                        <label>
                            Project name
                            <input
                                value={title}
                                onChange={(e) => setTitle(e.target.value)}
                                required
                                maxLength={200}
                                placeholder="e.g. Gopher Study Group Finder"
                            />
                        </label>
                        <label>
                            Project description
                            <textarea
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                required
                                rows={3}
                                placeholder="A short summary of what you're building."
                            />
                        </label>
                        <label>
                            GitHub link <span className="muted">(optional)</span>
                            <input
                                type="url"
                                value={repoUrl}
                                onChange={(e) => setRepoUrl(e.target.value)}
                                placeholder="https://github.com/…"
                            />
                        </label>
                        <div>
                            <h4>Teammates &amp; team lead</h4>
                            <p className="muted">
                                Add other members by name, and pick who leads the project — yourself, or someone
                                on the team.
                            </p>
                        </div>
                        <TeamPicker
                            people={people}
                            memberIds={memberIds}
                            leadId={leadId}
                            lockedIds={[user.id]}
                            onChange={(ids, lead) => {
                                setMemberIds(ids)
                                setLeadId(lead)
                            }}
                        />
                    </>
                )}

                {step >= 1 && step <= 3 && (
                    <>
                        <h3>{DESIGN_SECTIONS[step - 1].title}</h3>
                        <p className="muted">{DESIGN_SECTIONS[step - 1].blurb}</p>
                        <DesignDocQuestions doc={doc} onChange={setDoc} section={DESIGN_SECTIONS[step - 1]}/>
                    </>
                )}

                {step === 4 && (
                    <>
                        <h3>Deliverables</h3>
                        <p className="muted">
                            Break the project into tasks with owners, due dates, and dependencies — they become
                            your project timeline. Every project must include an MVP presentation and a final
                            presentation; the board may adjust the timeline.
                        </p>
                        <DeliverablesEditor tasks={tasks} team={team} onChange={setTasks}/>
                    </>
                )}

                {error && <p className="form-error">{error}</p>}

                <div className="step-actions">
                    {step > 0 && (
                        <button type="button" className="btn btn-ghost" onClick={() => setStep(step - 1)}>
                            Back
                        </button>
                    )}
                    {step < STEPS.length - 1 ? (
                        <button type="submit" className="btn btn-primary">
                            Next: {STEPS[step + 1]}
                        </button>
                    ) : (
                        <button type="submit" className="btn btn-primary" disabled={submitting}>
                            {submitting ? 'Submitting…' : 'Submit for board review'}
                        </button>
                    )}
                </div>
            </form>
        </section>
    )
}
