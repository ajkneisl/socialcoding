export async function request<T>(
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
