import { Link } from 'react-router-dom'
import { usePendingProjects } from '../../features/board/queries'
import { useEvents } from '../../features/events/queries'
import { useProjects } from '../../features/projects/queries'
import { card } from '../../components/styles'

export default function Overview() {
    const { data: pending = [] } = usePendingProjects()
    const { data: approved = [] } = useProjects()
    const { data: events = [] } = useEvents()

    const active = approved.filter((p) => p.active).length

    const stats = [
        {
            value: approved.length + pending.length,
            label: 'Total projects',
            to: '/board/projects',
            highlight: false,
        },
        {
            value: pending.length,
            label: 'Awaiting review',
            to: '/board/projects',
            highlight: pending.length > 0,
        },
        {
            value: active,
            label: 'Active on the home page',
            to: '/board/projects',
            highlight: false,
        },
        {
            value: events.length,
            label: 'Events published',
            to: '/board/events',
            highlight: false,
        },
    ]

    return (
        <>
            <p className="mb-7 max-w-[60ch] text-text-soft">
                A quick outline of where things stand. Jump into a section to take action.
            </p>

            <div className="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-4">
                {stats.map((s) => (
                    <Link
                        key={s.label}
                        to={s.to}
                        className={`${card} hover:border-gold hover:no-underline`}
                    >
                        <strong
                            className={`block text-[2.4rem] font-extrabold leading-none ${
                                s.highlight ? 'text-gold' : 'text-text'
                            }`}
                        >
                            {s.value}
                        </strong>
                        <span className="mt-2 block text-[0.9rem] text-text-soft">{s.label}</span>
                    </Link>
                ))}
            </div>
        </>
    )
}
