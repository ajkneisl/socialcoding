import type { ReactNode } from 'react'
import { Eyebrow } from './Eyebrow'
import { card, container } from './styles'

/** Narrow centered card page, used for sign-in prompts and similar gates. */
export function NoticeCard({
    eyebrow,
    title,
    children,
}: {
    eyebrow: string
    title: string
    children: ReactNode
}) {
    return (
        <section className={`${container} max-w-[560px] pb-16 pt-12`}>
            <div className={`${card} px-8 py-10 text-center`}>
                <Eyebrow>{eyebrow}</Eyebrow>
                <h2>{title}</h2>
                {children}
            </div>
        </section>
    )
}
