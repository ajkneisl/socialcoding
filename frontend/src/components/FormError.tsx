/** Renders nothing when there is no error, so call sites don't need a conditional. */
export function FormError({ error, className }: { error?: string | null; className?: string }) {
    if (!error) return null
    return (
        <p
            className={[
                'm-0 rounded-lg bg-red-400/12 px-[0.85rem] py-[0.55rem] text-red-400',
                className,
            ]
                .filter(Boolean)
                .join(' ')}
        >
            {error}
        </p>
    )
}
