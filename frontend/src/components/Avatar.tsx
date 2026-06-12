import { initials } from '../util'

const sizes = {
    sm: 'h-[30px] w-[30px] text-[0.72rem]',
    md: 'h-[52px] w-[52px] text-base',
    lg: 'h-[72px] w-[72px] text-[1.3rem]',
}

export function Avatar({
    name,
    avatarUrl,
    size = 'md',
}: {
    name: string
    avatarUrl?: string | null
    size?: keyof typeof sizes
}) {
    if (avatarUrl) {
        return (
            <img
                className={`${sizes[size]} shrink-0 rounded-full object-cover`}
                src={avatarUrl}
                alt=""
                referrerPolicy="no-referrer"
            />
        )
    }
    return (
        <div
            className={`${sizes[size]} flex shrink-0 items-center justify-center rounded-full border border-gold/25 bg-gold/12 font-bold text-gold`}
            aria-hidden="true"
        >
            {initials(name)}
        </div>
    )
}
