import {Spinner} from '../../components/Spinner'
import {useAuth} from '../../auth-context'
import {useToggleLike} from './queries'
import type {Project} from './types'

const heart = (filled: boolean) => (
    <svg
        viewBox="0 0 24 24"
        width="15"
        height="15"
        fill={filled ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="2"
        strokeLinejoin="round"
        aria-hidden="true"
    >
        <path
            d="M12 21s-7.5-4.6-10-9.2C.6 9 1.7 5.5 5 5.5c2 0 3.2 1.2 4 2.3.8-1.1 2-2.3 4-2.3 3.3 0 4.4 3.5 3 6.3-2.5 4.6-10 9.2-10 9.2z"/>
    </svg>
)

const base = 'inline-flex items-center gap-[0.35rem] font-mono text-[0.8rem]'

/** A heart with the project's like count. Interactive when signed in; a static tally otherwise. */
export function LikeButton({project}: { project: Project }) {
    const {user} = useAuth()
    const toggle = useToggleLike()

    if (!user) {
        return (
            <span className={`${base} text-text-faint`} title="Sign in to like">
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
                project.liked ? 'text-gold' : 'text-text-faint hover:text-gold'
            }`}
        >
            {toggle.isPending ? <Spinner className="h-[15px] w-[15px]" /> : heart(project.liked)}
            {project.likes}
        </button>
    )
}
