import type { ReactNode } from 'react'
import { page } from './styles'

/** Full page that shows a single muted status line (loading, empty, etc.). */
export function PageMessage({ children }: { children: ReactNode }) {
    return (
        <section className={page}>
            <p className="text-text-soft">{children}</p>
        </section>
    )
}
