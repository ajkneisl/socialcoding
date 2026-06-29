import {useCallback, useState, type ComponentType, type FormEvent} from 'react'
import {Link} from 'react-router-dom'
import {authApi} from '../features/auth/api'
import GoogleSignIn from '../features/auth/GoogleSignIn'
import {useMyInvites, useMyProjects, useRespondToInvite} from '../features/projects/queries'
import type {Project} from '../features/projects/types'
import {ProjectCard} from '../features/projects/ProjectCard'
import {useAuth} from '../auth-context'
import {Avatar} from '../components/Avatar'
import {GitHubIcon, LinkedInIcon} from '../components/BrandIcons'
import {Button, LinkButton} from '../components/Button'
import {CheckIcon} from '../components/CheckIcon'
import {Chip} from '../components/Chip'
import {Switch} from '../components/Switch'
import {FormError} from '../components/FormError'
import {NoticeCard} from '../components/NoticeCard'
import {PageMessage} from '../components/PageMessage'
import {Spinner} from '../components/Spinner'
import {card, page} from '../components/styles'

/** Gear glyph for the settings toggle. */
function CogIcon({className}: {className?: string}) {
    return (
        <svg
            viewBox="0 0 24 24"
            width="18"
            height="18"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
            className={className}
        >
            <circle cx="12" cy="12" r="3"/>
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
    )
}

type ProfileFieldKey = 'joinedTerm' | 'gradYear' | 'github' | 'linkedin' | 'website' | 'company'
type OptionalFieldKey = 'github' | 'linkedin' | 'website' | 'company'

/** The opt-in profile details, shown as pills until the user adds them. */
const OPTIONAL_FIELDS: {
    key: OptionalFieldKey
    label: string
    type?: string
    maxLength?: number
    icon?: ComponentType<{className?: string}>
}[] = [
    {key: 'github', label: 'GitHub', icon: GitHubIcon},
    {key: 'linkedin', label: 'LinkedIn', icon: LinkedInIcon},
    {key: 'website', label: 'Portfolio', type: 'url'},
    {key: 'company', label: 'Employers', maxLength: 255},
]

function ProfileForm() {
    const {user, token, refreshUser} = useAuth()
    const initial: Record<ProfileFieldKey, string> = {
        joinedTerm: user?.joinedTerm ?? '',
        gradYear: user?.gradYear ? String(user.gradYear) : '',
        github: user?.github ?? '',
        linkedin: user?.linkedin ?? '',
        website: user?.website ?? '',
        company: user?.company ?? '',
    }
    const [values, setValues] = useState(initial)
    // Only optional fields the user has already filled in start out visible; the rest are opt-in.
    const [active, setActive] = useState<OptionalFieldKey[]>(
        OPTIONAL_FIELDS.filter((f) => initial[f.key] !== '').map((f) => f.key),
    )
    const [listed, setListed] = useState(user?.listed ?? true)
    const [saving, setSaving] = useState(false)
    const [saved, setSaved] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const setValue = (key: ProfileFieldKey, value: string) =>
        setValues((v) => ({...v, [key]: value}))

    const addField = (key: OptionalFieldKey) => setActive((a) => [...a, key])

    const removeField = (key: OptionalFieldKey) => {
        setActive((a) => a.filter((k) => k !== key))
        setValue(key, '')
    }

    const activeFields = OPTIONAL_FIELDS.filter((f) => active.includes(f.key))
    const availableFields = OPTIONAL_FIELDS.filter((f) => !active.includes(f.key))

    async function submit(e: FormEvent) {
        e.preventDefault()
        if (!token || saving) return
        setError(null)
        setSaved(false)
        setSaving(true)
        try {
            await authApi.updateProfile(token, {
                joinedTerm: values.joinedTerm.trim() || null,
                gradYear: values.gradYear ? Number(values.gradYear) : null,
                github: values.github.trim() || null,
                linkedin: values.linkedin.trim() || null,
                website: values.website.trim() || null,
                company: values.company.trim() || null,
                listed,
            })
            await refreshUser()
            setSaved(true)
            // Revert the checkmark back to the default label after a moment.
            setTimeout(() => setSaved(false), 2000)
        } catch (err) {
            setError((err as Error).message)
        } finally {
            setSaving(false)
        }
    }

    return (
        <form className={`${card} mb-8 flex flex-col gap-[1.1rem]`} onSubmit={submit}>
            <div>
                <h3 className="m-0">Your profile</h3>
                <p className="m-0 text-text-soft">This is what shows on the People page.</p>
            </div>

            <div className="flex flex-wrap gap-[0.9rem] [&>label]:min-w-[11rem]">
                <label>
                    Joined
                    <input
                        value={values.joinedTerm}
                        onChange={(e) => setValue('joinedTerm', e.target.value)}
                        maxLength={32}
                    />
                </label>
                <label>
                    Class of
                    <input
                        type="number"
                        value={values.gradYear}
                        onChange={(e) => setValue('gradYear', e.target.value)}
                        min={2000}
                        max={2100}
                    />
                </label>
            </div>

            {activeFields.length > 0 && (
                <div className="flex flex-wrap gap-[0.9rem]">
                    {activeFields.map((f) => (
                        <div
                            key={f.key}
                            className="flex min-w-[11rem] flex-1 flex-col gap-[0.3rem] text-[0.9rem] font-medium"
                        >
                            <div className="flex items-center justify-between gap-2">
                                <span className="flex items-center gap-[0.4rem]">
                                    {f.icon && <f.icon />}
                                    {f.label}
                                </span>
                                <button
                                    type="button"
                                    onClick={() => removeField(f.key)}
                                    aria-label={`Remove ${f.label}`}
                                    className="cursor-pointer font-mono text-[0.75rem] font-normal text-text-faint hover:text-crimson"
                                >
                                    remove
                                </button>
                            </div>
                            <input
                                type={f.type ?? 'text'}
                                value={values[f.key]}
                                onChange={(e) => setValue(f.key, e.target.value)}
                                maxLength={f.maxLength}
                                aria-label={f.label}
                            />
                        </div>
                    ))}
                </div>
            )}

            {availableFields.length > 0 && (
                <div className="flex flex-col gap-[0.5rem]">
                    <span className="font-mono text-[0.8rem] text-text-soft">
                        Add to your profile
                    </span>
                    <div className="flex flex-wrap gap-[0.5rem]">
                        {availableFields.map((f) => (
                            <Chip key={f.key} sm onClick={() => addField(f.key)}>
                                {f.icon ? <f.icon /> : '+'} {f.label}
                            </Chip>
                        ))}
                    </div>
                </div>
            )}

            <Switch
                checked={listed}
                onChange={setListed}
                label="Show up on member list"
                description="When on, your profile appears on the public People page."
            />
            <FormError error={error}/>
            <Button variant="primary" type="submit" disabled={saving} className="self-start">
                {saving ? (
                    <>
                        <Spinner className="h-4 w-4" />
                        Saving…
                    </>
                ) : saved ? (
                    <>
                        <CheckIcon />
                        Saved
                    </>
                ) : (
                    'Save profile'
                )}
            </Button>
        </form>
    )
}

function InvitesSection() {
    const {data: invites = []} = useMyInvites()
    const respond = useRespondToInvite()

    if (invites.length === 0) return null

    const pendingId = respond.isPending ? respond.variables?.id : undefined

    return (
        <div className="mb-10">
            <div className="mb-7 max-w-[680px]">
                <h3>Project invites</h3>
                <p className="text-text-soft">
                    Accept to join the team or decline to remove yourself.
                </p>
            </div>
            <div className="flex flex-col gap-[0.9rem]">
                {invites.map((invite) => (
                    <div
                        key={invite.id}
                        className={`${card} flex flex-wrap items-center justify-between gap-4`}
                    >
                        <div className="min-w-[14rem] flex-1">
                            <h4 className="m-0">{invite.title}</h4>
                            <div className="mt-[0.3rem] flex items-center gap-[0.65rem]">
                                <span className="font-mono text-[0.8rem] text-text-soft">
                                    invited by
                                </span>
                                <Avatar
                                    name={invite.teamLeadName}
                                    avatarUrl={invite.teamLeadAvatarUrl}
                                    size="sm"
                                />
                                <span className="font-medium">{invite.teamLeadName}</span>
                            </div>
                            <p className="mb-0 mt-[0.4rem] line-clamp-2 text-text-soft">
                                {invite.description}
                            </p>
                        </div>
                        <div className="flex gap-[0.6rem]">
                            <Button
                                variant="ghost"
                                disabled={respond.isPending}
                                onClick={() =>
                                    respond.mutate({id: invite.id, accept: false})
                                }
                            >
                                {pendingId === invite.id ? 'Working…' : 'Decline'}
                            </Button>
                            <Button
                                disabled={respond.isPending}
                                onClick={() => respond.mutate({id: invite.id, accept: true})}
                            >
                                Accept
                            </Button>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    )
}

export default function Account() {
    const {user, loading, logout} = useAuth()
    const {data: myProjects = []} = useMyProjects()
    const [loginError, setLoginError] = useState<string | null>(null)
    const [showSettings, setShowSettings] = useState(false)

    const onLoginError = useCallback((message: string) => setLoginError(message), [])

    if (loading) {
        return <PageMessage>Loading…</PageMessage>
    }

    if (!user) {
        return (
            <NoticeCard eyebrow="Account" title="Sign in">
                <p className="text-text-soft">
                    Use your University of Minnesota Google account (<strong>@umn.edu</strong>).
                </p>

                <GoogleSignIn onError={onLoginError}/>
                <FormError error={loginError} className="mt-4"/>
            </NoticeCard>
        )
    }

    return (
        <section className={page}>
            <div className="mb-8 flex flex-wrap items-center gap-5">
                <Avatar name={user.name} avatarUrl={user.avatarUrl} size="lg"/>
                <div>
                    <h2 className="mb-[0.1rem]">{user.name}</h2>
                    <p className="m-0 font-mono text-[0.8rem] text-text-soft">
                        {user.email}
                        {user.role === 'BOARD' && ' · board'}
                    </p>
                </div>
                <div className="ml-auto flex items-center gap-[0.6rem] max-md:ml-0 max-md:w-full">
                    <LinkButton to="/projects/new">Create Project</LinkButton>
                    <Button variant="ghost" onClick={logout}>
                        Sign out
                    </Button>
                    <Button
                        variant="ghost"
                        aria-label="Profile settings"
                        aria-expanded={showSettings}
                        className={`px-[0.7rem]! ${showSettings ? 'border-text-faint bg-bg-raised' : ''}`}
                        onClick={() => setShowSettings((s) => !s)}
                    >
                        <CogIcon/>
                    </Button>
                </div>
            </div>

            {showSettings && <ProfileForm key={user.id}/>}

            <InvitesSection/>

            <div className="mb-7 max-w-[680px]">
                <h3>Your projects</h3>
                <p className="text-text-soft">
                    Projects you lead or are a teammate on.
                </p>
            </div>
            {myProjects.length === 0 ? (
                <p className="text-text-soft">
                    Nothing yet, <Link to="/projects/new">start your first project</Link>.
                </p>
            ) : (
                <div className="grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-[1.1rem]">
                    {myProjects.map((p: Project) => (
                        <ProjectCard key={p.id} project={p} showStatus linkToDoc/>
                    ))}
                </div>
            )}
        </section>
    )
}
