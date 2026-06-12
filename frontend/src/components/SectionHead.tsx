import type { ReactNode } from 'react'
import { Eyebrow } from './Eyebrow'

/** Heading block for a page section: optional eyebrow, title, and a muted description. */
export function SectionHead({
    eyebrow,
    title,
    className,
    children,
}: {
    eyebrow?: string
    title: ReactNode
    className?: string
    children?: ReactNode
}) {
    return (
        <div className={['mb-7 max-w-[680px]', className].filter(Boolean).join(' ')}>
            {eyebrow && <Eyebrow>{eyebrow}</Eyebrow>}
            <h2>{title}</h2>
            {children != null && <p className="text-text-soft">{children}</p>}
        </div>
    )
}
