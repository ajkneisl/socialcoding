import { useMemo, useState } from 'react'
import { usePeople } from '../features/people/queries'
import { PersonCard } from '../features/people/PersonCard'
import { Chip } from '../components/Chip'
import { FormError } from '../components/FormError'
import { SectionHead } from '../components/SectionHead'
import { page } from '../components/styles'

export default function People() {
    const { data: people = [], error } = usePeople()
    const [query, setQuery] = useState('')

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
        <section className={page}>
            <SectionHead eyebrow="People" title="Members">
                Everyone who has been part of Social Coding. Sign in to add yourself.
            </SectionHead>

            {companies.length > 0 && (
                <div className="mb-8">
                    <h3 className="mb-3 text-[1.05rem]">Companies our members work at</h3>
                    <div className="flex flex-wrap gap-2">
                        {companies.map(([name, count]) => (
                            <Chip
                                key={name}
                                active={query === name}
                                onClick={() => setQuery(query === name ? '' : name)}
                            >
                                {name}
                                {count > 1 && (
                                    <span className="font-semibold text-gold">{count}</span>
                                )}
                            </Chip>
                        ))}
                    </div>
                </div>
            )}

            <input
                type="search"
                className="mb-6 w-full max-w-[440px] px-4 py-[0.6rem]"
                placeholder="Search by name, term, class year, or company…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search members"
            />

            <FormError error={error?.message} />

            <div className="grid grid-cols-[repeat(auto-fill,minmax(250px,1fr))] gap-[1.1rem]">
                {filtered.map((p) => (
                    <PersonCard key={p.id} person={p} />
                ))}
            </div>
            {!error && filtered.length === 0 && (
                <p className="text-text-soft">No members match “{query}”.</p>
            )}
        </section>
    )
}
