import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { authApi } from '../features/auth/api'
import GoogleSignIn from '../features/auth/GoogleSignIn'
import { projectsApi } from '../features/projects/api'
import type { Project } from '../features/projects/types'
import { ProjectCard } from '../features/projects/ProjectCard'
import { useAuth } from '../auth-context'
import { Avatar } from '../components/Avatar'
import { Button, LinkButton } from '../components/Button'
import { Switch } from '../components/Switch'
import { FormError } from '../components/FormError'
import { NoticeCard } from '../components/NoticeCard'
import { PageMessage } from '../components/PageMessage'
import { card, page } from '../components/styles'

function ProfileForm() {
    const { user, token, refreshUser } = useAuth()
    const [joinedTerm, setJoinedTerm] = useState(user?.joinedTerm ?? '')
    const [gradYear, setGradYear] = useState(user?.gradYear ? String(user.gradYear) : '')
    const [github, setGithub] = useState(user?.github ?? '')
    const [linkedin, setLinkedin] = useState(user?.linkedin ?? '')
    const [website, setWebsite] = useState(user?.website ?? '')
    const [company, setCompany] = useState(user?.company ?? '')
    const [listed, setListed] = useState(user?.listed ?? true)
    const [saved, setSaved] = useState(false)
    const [error, setError] = useState<string | null>(null)

    async function submit(e: FormEvent) {
        e.preventDefault()
        if (!token) return
        setError(null)
        setSaved(false)
        try {
            await authApi.updateProfile(token, {
                joinedTerm: joinedTerm || null,
                gradYear: gradYear ? Number(gradYear) : null,
                github: github || null,
                linkedin: linkedin || null,
                website: website || null,
                company: company || null,
                listed,
            })
            await refreshUser()
            setSaved(true)
        } catch (err) {
            setError((err as Error).message)
        }
    }

    return (
        <form className={`${card} mb-8 flex flex-col gap-[0.9rem]`} onSubmit={submit}>
            <h3 className="m-0">Your profile</h3>
            <p className="m-0 text-text-soft">This is what shows on the People page.</p>
            <div className="flex flex-wrap gap-[0.9rem] [&>label]:min-w-[11rem]">
                <label>
                    Joined
                    <input
                        value={joinedTerm}
                        onChange={(e) => setJoinedTerm(e.target.value)}
                        placeholder="Fall 2025"
                        maxLength={32}
                    />
                </label>
                <label>
                    Class of
                    <input
                        type="number"
                        value={gradYear}
                        onChange={(e) => setGradYear(e.target.value)}
                        placeholder="2028"
                        min={2000}
                        max={2100}
                    />
                </label>
            </div>
            <div className="flex flex-wrap gap-[0.9rem] [&>label]:min-w-[11rem]">
                <label>
                    GitHub username
                    <input
                        value={github}
                        onChange={(e) => setGithub(e.target.value)}
                        placeholder="goldy"
                    />
                </label>
                <label>
                    LinkedIn URL
                    <input
                        type="url"
                        value={linkedin}
                        onChange={(e) => setLinkedin(e.target.value)}
                        placeholder="https://linkedin.com/in/…"
                    />
                </label>
            </div>
            <div className="flex flex-wrap gap-[0.9rem] [&>label]:min-w-[11rem]">
                <label>
                    Portfolio website
                    <input
                        type="url"
                        value={website}
                        onChange={(e) => setWebsite(e.target.value)}
                        placeholder="https://yoursite.dev"
                    />
                </label>
                <label>
                    <input
                        value={company}
                        onChange={(e) => setCompany(e.target.value)}
                        placeholder="e.g. Target"
                        maxLength={255}
                    />
                </label>
            </div>
            <Switch
                checked={listed}
                onChange={setListed}
                label="Show up on member list"
                description="When on, your profile appears on the public People page."
            />
            <FormError error={error} />
            {saved && <p className="m-0 text-green-400">Profile saved.</p>}
            <Button variant="primary" type="submit" className="self-start">
                Save profile
            </Button>
        </form>
    )
}

export default function Account() {
    const { user, token, loading, logout } = useAuth()
    const [myProjects, setMyProjects] = useState<Project[]>([])
    const [loginError, setLoginError] = useState<string | null>(null)

    useEffect(() => {
        if (token && user) {
            projectsApi
                .mine(token)
                .then(setMyProjects)
                .catch(() => setMyProjects([]))
        }
    }, [token, user])

    const onLoginError = useCallback((message: string) => setLoginError(message), [])

    if (loading) {
        return <PageMessage>Loading…</PageMessage>
    }

    if (!user) {
        return (
            <NoticeCard eyebrow="Account" title="Sign in">
                <p className="text-text-soft">
                    Use your University of Minnesota Google account (<strong>@umn.edu</strong>).
                    Other accounts won't work.
                </p>
                <GoogleSignIn onError={onLoginError} />
                <FormError error={loginError} className="mt-4" />
            </NoticeCard>
        )
    }

    return (
        <section className={page}>
            <div className="mb-8 flex flex-wrap items-center gap-5">
                <Avatar name={user.name} avatarUrl={user.avatarUrl} size="lg" />
                <div>
                    <h2 className="mb-[0.1rem]">{user.name}</h2>
                    <p className="m-0 font-mono text-[0.8rem] text-text-soft">
                        {user.email}
                        {user.role === 'BOARD' && ' · board'}
                    </p>
                </div>
                <div className="ml-auto flex gap-[0.6rem] max-md:ml-0 max-md:w-full">
                    <LinkButton to="/projects/new">Create Project</LinkButton>
                    <Button variant="ghost" onClick={logout}>
                        Sign out
                    </Button>
                </div>
            </div>

            <ProfileForm key={user.id} />

            <div className="mb-7 max-w-[680px]">
                <h3>Your projects</h3>
                <p className="text-text-soft">
                    Projects you lead or are a teammate on. Pending design docs are visible only to
                    your team and the board until they're approved.
                </p>
            </div>
            {myProjects.length === 0 ? (
                <p className="text-text-soft">
                    Nothing yet, <Link to="/projects/new">start your first project</Link>.
                </p>
            ) : (
                <div className="grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-[1.1rem]">
                    {myProjects.map((p) => (
                        <ProjectCard key={p.id} project={p} showStatus linkToDoc />
                    ))}
                </div>
            )}
        </section>
    )
}
