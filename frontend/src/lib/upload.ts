/** Uploads an image to the object store and resolves to its public URL. */
export async function uploadImage(token: string, file: File): Promise<string> {
    const form = new FormData()
    form.append('file', file)

    const res = await fetch('/api/uploads/image', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: form,
    })

    if (!res.ok) {
        let message = `Upload failed (${res.status})`
        try {
            const data = await res.json()
            if (data?.error) message = data.error
        } catch {
            // non-JSON error body; keep default message
        }
        throw new Error(message)
    }

    const data = (await res.json()) as { url: string }
    return data.url
}
