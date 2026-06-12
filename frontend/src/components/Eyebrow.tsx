import type { ReactNode } from 'react'

/** Small gold uppercase label with a gradient bar, shown above section headings. */
export function Eyebrow({ children }: { children: ReactNode }) {
    return (
        <p className="mb-[0.6rem] flex items-center gap-[0.6rem] font-mono text-[0.8rem] font-semibold uppercase tracking-[0.16em] text-gold before:h-[2px] before:w-[1.6rem] before:rounded-[2px] before:bg-linear-to-r before:from-crimson before:to-gold before:content-['']">
            {children}
        </p>
    )
}
