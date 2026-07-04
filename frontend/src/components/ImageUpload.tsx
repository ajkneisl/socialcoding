import { useRef, useState } from 'react'
import { useAuth } from '../auth-context'
import { uploadImage } from '../lib/upload'
import { Button } from './Button'
import { FormError } from './FormError'

/**
 * Picks an image, uploads it to the object store, and reports the resulting URL through [onChange].
 * An empty [value] means no image; "Remove" clears it back to that state.
 */
export function ImageUpload({
    value,
    onChange,
}: {
    value: string
    onChange: (url: string) => void
}) {
    const { token } = useAuth()
    const inputRef = useRef<HTMLInputElement>(null)
    const [uploading, setUploading] = useState(false)
    const [error, setError] = useState<string | null>(null)

    async function pick(file: File | undefined) {
        if (!file || !token) return
        setError(null)
        setUploading(true)
        try {
            onChange(await uploadImage(token, file))
        } catch (err) {
            setError((err as Error).message)
        } finally {
            setUploading(false)
            if (inputRef.current) inputRef.current.value = ''
        }
    }

    return (
        <div className="flex flex-col gap-2">
            <div className="flex items-center gap-3">
                {value ? (
                    <img
                        src={value}
                        alt="Selected upload preview"
                        className="h-16 w-16 rounded-md border border-line object-cover"
                    />
                ) : (
                    <div className="flex h-16 w-16 items-center justify-center rounded-md border border-dashed border-line text-center text-[0.7rem] text-text-faint">
                        No image
                    </div>
                )}
                <div className="flex gap-2">
                    <Button
                        variant="ghost"
                        disabled={uploading}
                        onClick={() => inputRef.current?.click()}
                    >
                        {uploading ? 'Uploading…' : value ? 'Replace' : 'Upload image'}
                    </Button>
                    {value && !uploading && (
                        <Button variant="ghost" onClick={() => onChange('')}>
                            Remove
                        </Button>
                    )}
                </div>
            </div>
            <input
                ref={inputRef}
                type="file"
                accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml"
                className="hidden"
                onChange={(e) => pick(e.target.files?.[0])}
            />
            <FormError error={error} />
        </div>
    )
}
