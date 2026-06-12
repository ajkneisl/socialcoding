import { useState } from 'react'
import type { Person } from '../people/types'
import { Avatar } from '../../components/Avatar'
import { Badge } from '../../components/Badge'

const linkBtn =
    'cursor-pointer border-0 bg-transparent p-0 font-mono text-[0.8rem] text-text-faint hover:text-gold hover:underline'

export function TeamPicker({
    people,
    memberIds,
    leadId,
    lockedIds = [],
    onChange,
}: {
    people: Person[]
    memberIds: number[]
    leadId: number
    /** Members that can't be removed (e.g. yourself while creating). */
    lockedIds?: number[]
    onChange: (memberIds: number[], leadId: number) => void
}) {
    const [query, setQuery] = useState('')
    const byId = new Map(people.map((p) => [p.id, p]))
    const q = query.trim().toLowerCase()
    const matches = q
        ? people
              .filter((p) => !memberIds.includes(p.id) && p.name.toLowerCase().includes(q))
              .slice(0, 6)
        : []

    return (
        <div className="flex flex-col gap-3">
            <div className="relative max-w-[440px]">
                <input
                    className="w-full"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="Add teammates by name…"
                    aria-label="Search members"
                />
                {matches.length > 0 && (
                    <ul className="absolute left-0 right-0 top-[calc(100%+4px)] z-20 rounded-lg border border-line bg-panel p-[0.3rem] shadow-[0_12px_32px_rgb(0_0_0/0.45)]">
                        {matches.map((p) => (
                            <li key={p.id}>
                                <button
                                    type="button"
                                    className="flex w-full cursor-pointer items-center gap-[0.6rem] rounded-md border-0 bg-transparent px-[0.6rem] py-[0.45rem] text-left text-[0.92rem] text-text hover:bg-bg-raised"
                                    onClick={() => {
                                        onChange([...memberIds, p.id], leadId)
                                        setQuery('')
                                    }}
                                >
                                    <Avatar name={p.name} avatarUrl={p.avatarUrl} size="sm" />
                                    <span>{p.name}</span>
                                    {p.company && (
                                        <span className="font-mono text-[0.8rem] text-text-soft">
                                            {p.company}
                                        </span>
                                    )}
                                </button>
                            </li>
                        ))}
                    </ul>
                )}
            </div>
            <ul className="flex flex-col gap-[0.45rem]">
                {memberIds.map((id) => {
                    const person = byId.get(id)
                    if (!person) return null
                    const isLead = id === leadId
                    return (
                        <li key={id} className="flex items-center gap-[0.65rem]">
                            <Avatar name={person.name} avatarUrl={person.avatarUrl} size="sm" />
                            <span className="font-medium">{person.name}</span>
                            {isLead ? (
                                <Badge variant="board">team lead</Badge>
                            ) : (
                                <button
                                    type="button"
                                    className={linkBtn}
                                    onClick={() => onChange(memberIds, id)}
                                >
                                    make lead
                                </button>
                            )}
                            {!isLead && !lockedIds.includes(id) && (
                                <button
                                    type="button"
                                    className={linkBtn}
                                    aria-label={`Remove ${person.name}`}
                                    onClick={() =>
                                        onChange(
                                            memberIds.filter((m) => m !== id),
                                            leadId,
                                        )
                                    }
                                >
                                    remove
                                </button>
                            )}
                        </li>
                    )
                })}
            </ul>
        </div>
    )
}
