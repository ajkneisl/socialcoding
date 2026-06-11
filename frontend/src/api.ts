export type Role = 'MEMBER' | 'BOARD'
export type ProjectStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface Person {
  id: number
  name: string
  joinedTerm: string | null
  gradYear: number | null
  github: string | null
  linkedin: string | null
  website: string | null
  company: string | null
  role: Role
  title?: string | null
  avatarUrl?: string | null
}

export interface User {
  id: number
  email: string
  name: string
  role: Role
  joinedTerm: string | null
  gradYear: number | null
  github: string | null
  linkedin: string | null
  website: string | null
  company: string | null
  title?: string | null
  avatarUrl?: string | null
}

export interface Project {
  id: number
  title: string
  description: string
  longDescription: string | null
  repoUrl: string | null
  siteUrl: string | null
  status: ProjectStatus
  active: boolean
  ownerName: string
  submittedAt: number
  reviewNote?: string | null
}

export interface LoginResponse {
  token: string
  user: User
}

async function request<T>(
  path: string,
  options: { method?: string; body?: unknown; token?: string | null } = {},
): Promise<T> {
  const headers: Record<string, string> = {}
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (options.token) headers['Authorization'] = `Bearer ${options.token}`

  const res = await fetch(path, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  if (!res.ok) {
    let message = `Request failed (${res.status})`
    try {
      const data = await res.json()
      if (data?.error) message = data.error
    } catch {
      // non-JSON error body; keep default message
    }
    throw new Error(message)
  }

  if (res.status === 204 || res.headers.get('content-length') === '0') {
    return undefined as T
  }
  return res.json() as Promise<T>
}

export const api = {
  people: () => request<Person[]>('/api/people'),
  projects: () => request<Project[]>('/api/projects'),
  loginWithGoogle: (credential: string) =>
    request<LoginResponse>('/api/auth/google', { method: 'POST', body: { credential } }),
  me: (token: string) => request<User>('/api/me', { token }),
  updateProfile: (
    token: string,
    profile: {
      joinedTerm?: string | null
      gradYear?: number | null
      github?: string | null
      linkedin?: string | null
      website?: string | null
      company?: string | null
    },
  ) => request<User>('/api/me', { method: 'POST', body: profile, token }),
  createProject: (
    token: string,
    project: {
      title: string
      description: string
      longDescription?: string
      repoUrl?: string
      siteUrl?: string
    },
  ) => request<Project>('/api/projects', { method: 'POST', body: project, token }),
  myProjects: (token: string) => request<Project[]>('/api/projects/mine', { token }),
  pendingProjects: (token: string) => request<Project[]>('/api/board/projects', { token }),
  reviewProject: (
    token: string,
    id: number,
    decision: 'approve' | 'reject' | 'activate' | 'deactivate',
    note?: string,
  ) =>
    request<void>(`/api/board/projects/${id}/${decision}`, {
      method: 'POST',
      body: { note: note ?? null },
      token,
    }),
}
