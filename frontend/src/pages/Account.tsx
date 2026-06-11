import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, type Project } from '../api'
import { useAuth } from '../auth-context'
import GoogleSignIn from '../components/GoogleSignIn'
import { ProjectCard } from '../components/cards'
import { initials } from '../util'

function CreateProjectForm({ onCreated }: { onCreated: (project: Project) => void }) {
  const { token } = useAuth()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [repoUrl, setRepoUrl] = useState('')
  const [siteUrl, setSiteUrl] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!token) return
    setSubmitting(true)
    setError(null)
    try {
      const project = await api.createProject(token, {
        title,
        description,
        repoUrl: repoUrl || undefined,
        siteUrl: siteUrl || undefined,
      })
      onCreated(project)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="card form" onSubmit={submit}>
      <h3>Pitch a project</h3>
      <p className="muted">
        Submissions are reviewed by the board. Once approved, your project appears on the public
        projects page.
      </p>
      <label>
        Title
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
          maxLength={200}
          placeholder="e.g. Gopher Study Group Finder"
        />
      </label>
      <label>
        Description
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          required
          rows={3}
          placeholder="What are you building, who is it for, and what help do you need?"
        />
      </label>
      <div className="form-row">
        <label>
          Repository URL <span className="muted">(optional)</span>
          <input
            type="url"
            value={repoUrl}
            onChange={(e) => setRepoUrl(e.target.value)}
            placeholder="https://github.com/…"
          />
        </label>
        <label>
          Website <span className="muted">(optional)</span>
          <input
            type="url"
            value={siteUrl}
            onChange={(e) => setSiteUrl(e.target.value)}
            placeholder="https://…"
          />
        </label>
      </div>
      {error && <p className="form-error">{error}</p>}
      <button className="btn btn-primary" type="submit" disabled={submitting}>
        {submitting ? 'Submitting…' : 'Submit for review'}
      </button>
    </form>
  )
}

function ProfileForm() {
  const { user, token, refreshUser } = useAuth()
  const [joinedTerm, setJoinedTerm] = useState(user?.joinedTerm ?? '')
  const [gradYear, setGradYear] = useState(user?.gradYear ? String(user.gradYear) : '')
  const [github, setGithub] = useState(user?.github ?? '')
  const [linkedin, setLinkedin] = useState(user?.linkedin ?? '')
  const [website, setWebsite] = useState(user?.website ?? '')
  const [company, setCompany] = useState(user?.company ?? '')
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!token) return
    setError(null)
    setSaved(false)
    try {
      await api.updateProfile(token, {
        joinedTerm: joinedTerm || null,
        gradYear: gradYear ? Number(gradYear) : null,
        github: github || null,
        linkedin: linkedin || null,
        website: website || null,
        company: company || null,
      })
      await refreshUser()
      setSaved(true)
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return (
    <form className="card form" onSubmit={submit}>
      <h3>Your profile</h3>
      <p className="muted">This is what shows on the People page.</p>
      <div className="form-row">
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
      <div className="form-row">
        <label>
          GitHub username
          <input value={github} onChange={(e) => setGithub(e.target.value)} placeholder="goldy" />
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
      <div className="form-row">
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
          Company <span className="muted">(where you work or interned)</span>
          <input
            value={company}
            onChange={(e) => setCompany(e.target.value)}
            placeholder="e.g. Target"
            maxLength={255}
          />
        </label>
      </div>
      {error && <p className="form-error">{error}</p>}
      {saved && <p className="form-success">Profile saved.</p>}
      <button className="btn btn-ghost" type="submit">
        Save profile
      </button>
    </form>
  )
}

export default function Account() {
  const { user, token, loading, logout } = useAuth()
  const [myProjects, setMyProjects] = useState<Project[]>([])
  const [creating, setCreating] = useState(false)
  const [loginError, setLoginError] = useState<string | null>(null)

  useEffect(() => {
    if (token && user) {
      api.myProjects(token).then(setMyProjects).catch(() => setMyProjects([]))
    }
  }, [token, user])

  const onLoginError = useCallback((message: string) => setLoginError(message), [])

  if (loading) {
    return (
      <section className="section container page">
        <p className="muted">Loading…</p>
      </section>
    )
  }

  if (!user) {
    return (
      <section className="section container page narrow">
        <div className="card signin-card">
          <p className="eyebrow mono">Account</p>
          <h2>Sign in</h2>
          <p className="muted">
            Use your University of Minnesota Google account (<strong>@umn.edu</strong>). Other
            accounts won't work.
          </p>
          <GoogleSignIn onError={onLoginError} />
          {loginError && <p className="form-error">{loginError}</p>}
        </div>
      </section>
    )
  }

  return (
    <section className="section container page">
      <div className="account-head">
        {user.avatarUrl ? (
          <img className="avatar avatar-lg" src={user.avatarUrl} alt="" referrerPolicy="no-referrer" />
        ) : (
          <div className="avatar avatar-lg avatar-initials">{initials(user.name)}</div>
        )}
        <div>
          <h2>{user.name}</h2>
          <p className="mono muted">
            {user.email}
            {user.role === 'BOARD' && ' · board'}
          </p>
        </div>
        <div className="account-actions">
          {!creating && (
            <button className="btn btn-primary" onClick={() => setCreating(true)}>
              Create Project
            </button>
          )}
          <button className="btn btn-ghost" onClick={logout}>
            Sign out
          </button>
        </div>
      </div>

      {creating && (
        <CreateProjectForm
          onCreated={(project) => {
            setMyProjects((prev) => [...prev, project])
            setCreating(false)
          }}
        />
      )}

      <ProfileForm key={user.id} />

      <div className="section-head">
        <h3>Your projects</h3>
        <p className="muted">
          Pending projects are visible only to you and the board until they're approved.
        </p>
      </div>
      {myProjects.length === 0 ? (
        <p className="muted">Nothing yet — pitch your first project above.</p>
      ) : (
        <div className="grid grid-projects">
          {myProjects.map((p) => (
            <ProjectCard key={p.id} project={p} showStatus />
          ))}
        </div>
      )}
    </section>
  )
}
