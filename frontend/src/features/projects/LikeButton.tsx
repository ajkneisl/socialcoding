import {Spinner} from '../../components/Spinner'
import {useAuth} from '../../auth-context'
import {useToggleLike} from './queries'
import type {Project} from './types'

const heart = (filled: boolean) => (
    <svg
        viewBox="0 0 24 24"
        width="14"
        height="14"
        fill={filled ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="2"
        strokeLinejoin="round"
        aria-hidden="true"
    >
        <path
            d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
    </svg>
)

const base =
    'inline-flex items-center gap-[0.45rem] rounded-full border px-[0.8rem] py-[0.4rem] font-mono text-[0.95rem] font-semibold'

/** A heart with the project's like count. Interactive when signed in; a static tally otherwise. */
export function LikeButton({project}: { project: Project }) {
    const {user} = useAuth()
    const toggle = useToggleLike()

    if (!user) {
        return (
            <span
                className={`${base} border-line text-text-faint`}
                title="Sign in to like"
            >
                {heart(false)}
                {project.likes}
            </span>
        )
    }

    return (
        <button
            type="button"
            onClick={() => toggle.mutate(project.id)}
            disabled={toggle.isPending}
            aria-pressed={project.liked}
            aria-label={project.liked ? 'Remove like' : 'Like project'}
            className={`${base} cursor-pointer transition-colors disabled:opacity-60 ${
                project.liked
                    ? 'border-gold bg-gold/10 text-gold'
                    : 'border-line text-text-soft hover:border-gold hover:bg-gold/10 hover:text-gold'
            }`}
        >
            {toggle.isPending ? <Spinner className="h-[20px] w-[20px]" /> : heart(project.liked)}
            {project.likes}
        </button>
    )
}
