import type { ComponentPropsWithoutRef } from 'react'
import { Link } from 'react-router-dom'

const base =
    'inline-flex cursor-pointer items-center justify-center gap-[0.4rem] whitespace-nowrap rounded-lg border border-transparent text-[0.92rem] font-semibold px-[1.15rem] py-[0.55rem] transition-colors hover:no-underline disabled:cursor-default disabled:opacity-50'

const variants = {
    primary: 'bg-gold text-ink hover:bg-gold-bright hover:text-ink',
    ghost: 'border-line bg-transparent text-text hover:border-text-faint hover:bg-bg-raised',
    danger: 'bg-crimson text-white hover:bg-crimson-bright',
}

export type ButtonVariant = keyof typeof variants

type StyleProps = {
    variant?: ButtonVariant
    /** Larger padding and font, for hero/CTA placements. */
    lg?: boolean
}

function classes(variant: ButtonVariant, lg?: boolean, extra?: string) {
    return [base, variants[variant], lg && 'px-[1.6rem] py-3 text-base', extra]
        .filter(Boolean)
        .join(' ')
}

export function Button({
    variant = 'primary',
    lg,
    type = 'button',
    className,
    ...props
}: StyleProps & ComponentPropsWithoutRef<'button'>) {
    return <button type={type} className={classes(variant, lg, className)} {...props} />
}

export function LinkButton({
    variant = 'primary',
    lg,
    className,
    ...props
}: StyleProps & ComponentPropsWithoutRef<typeof Link>) {
    return <Link className={classes(variant, lg, className)} {...props} />
}

export function AnchorButton({
    variant = 'primary',
    lg,
    className,
    ...props
}: StyleProps & ComponentPropsWithoutRef<'a'>) {
    return <a className={classes(variant, lg, className)} {...props} />
}
