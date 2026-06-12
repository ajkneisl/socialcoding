import type { ReactNode } from 'react'

const base =
    'inline-block whitespace-nowrap rounded-full font-mono text-[0.7rem] font-semibold px-[0.6rem] py-[0.18rem]'

const variants = {
    approved: 'bg-green-400/12 text-green-400',
    pending: 'bg-amber-400/12 text-amber-400',
    rejected: 'bg-red-400/12 text-red-400',
    inactive: 'border border-line bg-panel text-text-faint',
    board: 'bg-crimson text-gold',
}

export type BadgeVariant = keyof typeof variants

export function Badge({ variant, children }: { variant: BadgeVariant; children: ReactNode }) {
    return <span className={`${base} ${variants[variant]}`}>{children}</span>
}
