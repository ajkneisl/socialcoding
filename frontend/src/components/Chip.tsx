import type { ComponentPropsWithoutRef } from 'react'

export function Chip({
    active = false,
    sm = false,
    className,
    ...props
}: { active?: boolean; sm?: boolean } & ComponentPropsWithoutRef<'button'>) {
    const cls = [
        'inline-flex cursor-pointer items-center gap-[0.45rem] rounded-full border font-mono text-[0.8rem] transition-colors',
        sm ? 'px-[0.7rem] py-[0.2rem]' : 'px-[0.85rem] py-[0.3rem]',
        active
            ? 'border-gold bg-gold/12 text-gold'
            : 'border-line bg-bg-raised text-text-soft hover:border-gold hover:text-text',
        className,
    ]
        .filter(Boolean)
        .join(' ')
    return <button type="button" className={cls} {...props} />
}
