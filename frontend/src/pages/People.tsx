import {useEffect, useMemo, useState} from 'react'
import {peopleApi} from '../features/people/api'
import type {Person} from '../features/people/types'
import {PersonCard} from '../components/cards'

export default function People() {
    const [people, setPeople] = useState<Person[]>([])
    const [query, setQuery] = useState('')
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        peopleApi.list().then(setPeople).catch((e: Error) => setError(e.message))
    }, [])

    const companies = useMemo(() => {
        const counts = new Map<string, number>()
        for (const p of people) {
            const c = p.company?.trim()
            if (c) counts.set(c, (counts.get(c) ?? 0) + 1)
        }
        return [...counts.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    }, [people])

    const filtered = useMemo(() => {
        const q = query.trim().toLowerCase()
        if (!q) return people
        return people.filter(
            (p) =>
                p.name.toLowerCase().includes(q) ||
                (p.joinedTerm ?? '').toLowerCase().includes(q) ||
                (p.company ?? '').toLowerCase().includes(q) ||
                String(p.gradYear ?? '').includes(q),
        )
    }, [people, query])

    return (
        <section className="section container page">
            <div className="section-head">
                <p className="eyebrow mono">People</p>
                <h2>Members</h2>
                <p className="muted">
                    Everyone who has been part of Social Coding. Sign in to add yourself.
                </p>
            </div>

            {companies.length > 0 && (
                <div className="companies">
                    <h3>Companies our members work at</h3>
                    <div className="company-chips">
                        {companies.map(([name, count]) => (
                            <button
                                key={name}
                                type="button"
                                className={`chip${query === name ? ' chip-active' : ''}`}
                                onClick={() => setQuery(query === name ? '' : name)}
                            >
                                {name}
                                {count > 1 && <span className="chip-count">{count}</span>}
                            </button>
                        ))}
                    </div>
                </div>
            )}

            <input
                type="search"
                className="search"
                placeholder="Search by name, term, class year, or company…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search members"
            />

            {error && <p className="form-error">{error}</p>}

            <div className="grid grid-people">
                {filtered.map((p) => (
                    <PersonCard key={p.id} person={p}/>
                ))}
            </div>
            {!error && filtered.length === 0 && (
                <p className="muted">No members match “{query}”.</p>
            )}
        </section>
    )
}
