import { useEffect, useRef } from 'react'
import { GOOGLE_CLIENT_ID, useAuth } from '../../auth-context'

declare global {
    interface Window {
        google?: {
            accounts: {
                id: {
                    initialize: (config: {
                        client_id: string
                        hd?: string
                        callback: (response: { credential: string }) => void
                    }) => void
                    renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void
                }
            }
        }
    }
}

const GSI_SRC = 'https://accounts.google.com/gsi/client'

function loadGsiScript(): Promise<void> {
    return new Promise((resolve, reject) => {
        if (window.google?.accounts) return resolve()
        const existing = document.querySelector<HTMLScriptElement>(`script[src="${GSI_SRC}"]`)
        const script = existing ?? document.createElement('script')
        if (!existing) {
            script.src = GSI_SRC
            script.async = true
            document.head.appendChild(script)
        }
        script.addEventListener('load', () => resolve())
        script.addEventListener('error', () => reject(new Error('Could not load Google Sign-In')))
    })
}

export default function GoogleSignIn({ onError }: { onError: (message: string) => void }) {
    const { loginWithCredential } = useAuth()
    const buttonRef = useRef<HTMLDivElement>(null)
    const unconfigured = !GOOGLE_CLIENT_ID

    useEffect(() => {
        const clientId = GOOGLE_CLIENT_ID
        if (!clientId) return
        let cancelled = false
        loadGsiScript()
            .then(() => {
                if (cancelled || !buttonRef.current || !window.google) return
                window.google.accounts.id.initialize({
                    client_id: clientId,
                    hd: 'umn.edu',
                    callback: ({ credential }) => {
                        loginWithCredential(credential).catch((err: Error) => onError(err.message))
                    },
                })
                window.google.accounts.id.renderButton(buttonRef.current, {
                    theme: 'filled_black',
                    size: 'large',
                    text: 'signin_with',
                    shape: 'pill',
                    width: 280,
                })
            })
            .catch((err: Error) => onError(err.message))
        return () => {
            cancelled = true
        }
    }, [loginWithCredential, onError])

    if (unconfigured) {
        return <p className="text-text-soft">There's an issue.</p>
    }

    return <div ref={buttonRef} className="mt-5 flex justify-center" />
}
