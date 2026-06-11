import { useCallback, useEffect, useState } from 'react'
import { api, type Project } from '../api'
import { useAuth } from '../auth-context'

function PendingProject({
  project,
  onReviewed,
}: {
  project: Project
  onReviewed: (id: number) => void
}) {
  const { token } = useAuth()
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function review(decision: 'approve' | 'reject') {
    if (!token) return
    setBusy(true)
    setError(null)
    try {
      await api.reviewProject(token, project.id, decision, note || undefined)
      onReviewed(project.id)
    } catch (err) {
      setError((err as Error).message)
      setBusy(false)
    }
  }

  return (
    <article className="row review-row">
      <div className="row-head">
        <h3>{project.title}</h3>
        <span className="mono muted">
          {new Date(project.submittedAt).toLocaleDateString()} · {project.ownerName}
        </span>
      </div>
      <p>{project.description}</p>
      {project.repoUrl && (
        <a href={project.repoUrl} target="_blank" rel="noreferrer" className="mono">
          repository ↗
        </a>
      )}
      <label>
        Note to submitter <span className="muted">(optional)</span>
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
    </article>
  )
}

function ApprovedProjectRow({ project }: { project: Project }) {
  const { token } = useAuth()
  const [active, setActive] = useState(project.active)
  const [busy, setBusy] = useState(false)

  async function toggle() {
    if (!token) return
    setBusy(true)
    try {
      await api.reviewProject(token, project.id, active ? 'deactivate' : 'activate')
      setActive(!active)
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className="row manage-row">
      <div>
        <h3>{project.title}</h3>
        <p className="mono muted">
          led by {project.ownerName} · {active ? 'shown on the home page' : 'listed under past projects'}
        </p>
      </div>
      <button className="btn btn-ghost" disabled={busy} onClick={toggle}>
        {active ? 'Mark inactive' : 'Mark active'}
      </button>
    </article>
  )
}

export default function Board() {
  const { user, token, loading } = useAuth()
  const [pending, setPending] = useState<Project[]>([])
  const [approved, setApproved] = useState<Project[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (token && user?.role === 'BOARD') {
      api.pendingProjects(token).then(setPending).catch((e: Error) => setError(e.message))
      api.projects().then(setApproved).catch(() => setApproved([]))
    }
  }, [token, user])

  const onReviewed = useCallback(
    (id: number) => setPending((prev) => prev.filter((p) => p.id !== id)),
    [],
  )

  if (loading) {
    return (
      <section className="section container page">
        <p className="muted">Loading…</p>
      </section>
    )
  }

  if (!user || user.role !== 'BOARD') {
    return (
      <section className="section container page">
        <h2>Board only</h2>
        <p className="muted">This page is for Social Coding board members.</p>
      </section>
    )
  }

  return (
    <section className="section container page">
      <div className="section-head">
        <p className="eyebrow mono">Board</p>
        <h2>Pending project approvals</h2>
        <p className="muted">Approved projects go live on the public projects page immediately.</p>
      </div>
      {error && <p className="form-error">{error}</p>}
      {pending.length === 0 ? (
        <p className="muted">Queue is empty. Nice work.</p>
      ) : (
        <div className="list">
          {pending.map((p) => (
            <PendingProject key={p.id} project={p} onReviewed={onReviewed} />
          ))}
        </div>
      )}

      {approved.length > 0 && (
        <>
          <div className="section-head past-head">
            <h2>Approved projects</h2>
            <p className="muted">
              Active projects appear on the home page; inactive ones move to “past projects.”
            </p>
          </div>
          <div className="list">
            {approved.map((p) => (
              <ApprovedProjectRow key={p.id} project={p} />
            ))}
          </div>
        </>
      )}
    </section>
  )
}
