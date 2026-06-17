import {useEffect, useMemo, useState} from 'react'
import {useNavigate} from 'react-router-dom'
import {usePeople} from '../features/people/queries'
import type {Person} from '../features/people/types'
import {useCreateProject} from '../features/projects/queries'
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
import {Button} from '../components/Button'
import {FormActions} from '../components/FormActions'
import {FormError} from '../components/FormError'
import {NoticeCard} from '../components/NoticeCard'
import {PageMessage} from '../components/PageMessage'
import {SectionHead} from '../components/SectionHead'
import {card, page} from '../components/styles'

const STEPS = ['Details', 'About your Project', 'Architecture', 'Teamwork', 'Deliverables']

function stepClass(active: boolean) {
    return `inline-flex cursor-pointer items-center gap-2 rounded-full border py-[0.4rem] pl-2 pr-4 text-[0.88rem] font-medium transition-colors ${
        active
            ? 'border-gold bg-gold/12 text-gold'
            : 'border-line bg-bg-raised text-text-soft hover:border-text-faint hover:text-text'
    }`
}

function stepNumClass(active: boolean) {
    return `inline-flex h-6 w-6 items-center justify-center rounded-full border font-mono text-xs font-bold ${
        active ? 'border-gold bg-gold text-ink' : 'border-line bg-panel'
    }`
}

export default function CreateProject() {
    const {user, token, loading} = useAuth()
    const navigate = useNavigate()
    const {data: people = []} = usePeople()
    const createProject = useCreateProject()

    const [step, setStep] = useState(0)

    const [title, setTitle] = useState('')
    const [description, setDescription] = useState('')
    const [repoUrl, setRepoUrl] = useState('')
    const [memberIds, setMemberIds] = useState<number[]>([])
    const [leadId, setLeadId] = useState<number>(0)
    const [doc, setDoc] = useState<DesignDoc>(emptyDesignDoc())
    const [tasks, setTasks] = useState<EditableTask[]>(requiredMilestones())

    const [error, setError] = useState<string | null>(null)

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
        return <PageMessage>Loading…</PageMessage>
    }

    if (!user || !token) {
        return (
            <NoticeCard eyebrow="Design doc" title="Sign in first">
                <p className="text-text-soft">
                    You need to be signed in to start a project design doc.
                </p>
            </NoticeCard>
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
        setError(null)
        try {
            const detail = await createProject.mutateAsync({
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
        }
    }

    return (
        <section className={page}>
            <SectionHead eyebrow="New project" title="Project design doc">
                Every Social Coding project starts with a design document that details everything relating to your
                project. After submitting, the board will review it and reach out if anything is missing.
            </SectionHead>

            <nav className="mb-6 flex flex-wrap gap-2" aria-label="Design doc sections">
                {STEPS.map((label, i) => (
                    <button
                        key={label}
                        type="button"
                        className={stepClass(i === step)}
                        onClick={() => setStep(i)}
                    >
                        <span className={stepNumClass(i === step)}>{i + 1}</span> {label}
                    </button>
                ))}
            </nav>

            <form
                className={`${card} mb-8 flex flex-col gap-[0.9rem]`}
                onSubmit={(e) => {
                    e.preventDefault()
                    if (step < STEPS.length - 1) next()
                    else submit()
                }}
            >
                {step === 0 && (
                    <>
                        <h3 className="m-0">Details</h3>
                        <label>
                            Project name
                            <input
                                value={title}
                                onChange={(e) => setTitle(e.target.value)}
                                required
                                maxLength={200}
                                placeholder="e.g. Computer Vision Wheelchair"
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
                            GitHub link <span className="text-text-soft">(optional)</span>
                            <input
                                type="url"
                                value={repoUrl}
                                onChange={(e) => setRepoUrl(e.target.value)}
                                placeholder="https://github.com/…"
                            />
                        </label>
                        <div>
                            <h4 className="mb-[0.15rem] mt-3">Team</h4>
                            <p className="m-0 text-text-soft">
                                Add other members by name, and pick a project lead.
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
                        <h3 className="m-0">{DESIGN_SECTIONS[step - 1].title}</h3>
                        <p className="m-0 text-text-soft">{DESIGN_SECTIONS[step - 1].blurb}</p>
                        <DesignDocQuestions
                            doc={doc}
                            onChange={setDoc}
                            section={DESIGN_SECTIONS[step - 1]}
                        />
                    </>
                )}

                {step === 4 && (
                    <>
                        <h3 className="m-0">Deliverables</h3>
                        <p className="m-0 text-text-soft">
                            Break the project into tasks with owners, due dates, and dependencies —
                            they become your project timeline. Every project must include an MVP
                            presentation and a final presentation; the board may adjust the
                            timeline.
                        </p>
                        <DeliverablesEditor tasks={tasks} team={team} onChange={setTasks}/>
                    </>
                )}

                <FormError error={error}/>

                <FormActions>
                    {step > 0 && (
                        <Button variant="ghost" className="w-1/3" onClick={() => setStep(step - 1)}>
                            Back
                        </Button>
                    )}
                    {step < STEPS.length - 1 ? (
                        <Button type="submit" className="w-2/3">Next: {STEPS[step + 1]}</Button>
                    ) : (
                        <Button type="submit" className="w-2/3" disabled={createProject.isPending}>
                            {createProject.isPending ? 'Submitting…' : 'Submit for board review'}
                        </Button>
                    )}
                </FormActions>
            </form>
        </section>
    )
}
