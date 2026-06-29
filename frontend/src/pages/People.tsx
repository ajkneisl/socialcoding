import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { listPeople } from '../features/people/api'
import type { Person } from '../features/people/types'
import { PersonCard } from '../features/people/PersonCard'
import { useAuth } from '../auth-context'
import { FormError } from '../components/FormError'
import { SectionHead } from '../components/SectionHead'
import { page } from '../components/styles'

const BOARD_ORDER = [
    'President',
    'Vice President',
    'Treasurer',
    'Vice Treasurer',
    'Events',
    'Communications',
    'Relations',
    'Recruitment',
]

const memberGrid = 'grid grid-cols-[repeat(auto-fill,minmax(250px,1fr))] gap-[1.1rem]'

function boardRank(person: Person) {
    const i = BOARD_ORDER.indexOf(person.title ?? '')
    return i === -1 ? BOARD_ORDER.length : i
}

export default function People() {
    const { user } = useAuth()
    const [people, setPeople] = useState<Person[]>([])
    const [query, setQuery] = useState('')
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        listPeople()
            .then(setPeople)
            .catch((e: Error) => setError(e.message))
    }, [])

    const filtered = useMemo(() => {
        const q = query.trim().toLowerCase()
        if (!q) return people
        return people.filter(
            (p) =>
                p.name.toLowerCase().includes(q) ||
                (p.joinedTerm ?? '').toLowerCase().includes(q) ||
                String(p.gradYear ?? '').includes(q),
        )
    }, [people, query])

    const boardMembers = useMemo(
        () =>
            filtered
                .filter((p) => p.role === 'BOARD')
                .sort((a, b) => boardRank(a) - boardRank(b) || a.name.localeCompare(b.name)),
        [filtered],
    )

    const otherMembers = useMemo(() => filtered.filter((p) => p.role !== 'BOARD'), [filtered])

    return (
        <section className={page}>
            <SectionHead eyebrow="People" title="Members">
                Everyone who has been part of Social Coding. Sign in to add yourself.
            </SectionHead>

            {user && (
                <div className="mb-8 flex flex-wrap items-center gap-3">
                    <span className="text-text-soft italic">
                        {user.listed
                            ? "You're visible on the public member list."
                            : "You're not shown on the public member list."}{' '}
                        <Link to="/account">Change this in your profile.</Link>
                    </span>
                </div>
            )}

            <input
                type="search"
                className="mb-6 w-full max-w-[440px] px-4 py-[0.6rem]"
                placeholder="Search by name, term, or class year…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search members"
            />

            <FormError error={error} />

            {boardMembers.length > 0 && (
                <div className="mb-10">
                    <h3 className="mb-3 text-[1.05rem]">Board</h3>
                    <div className={memberGrid}>
                        {boardMembers.map((p) => (
                            <PersonCard key={p.id} person={p} />
                        ))}
                    </div>
                </div>
            )}
            {otherMembers.length > 0 && (
                <div>
                    {boardMembers.length > 0 && <h3 className="mb-3 text-[1.05rem]">Members</h3>}
                    <div className={memberGrid}>
                        {otherMembers.map((p) => (
                            <PersonCard key={p.id} person={p} />
                        ))}
                    </div>
                </div>
            )}
            {!error && filtered.length === 0 && (
                <p className="text-text-soft">No members listed.</p>
            )}
        </section>
    )
}
