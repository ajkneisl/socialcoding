import {useState} from 'react'
import type {Person} from '../people/types'
import {initials} from '../../util'

function MemberAvatar({name, avatarUrl}: { name: string; avatarUrl?: string | null }) {
    return avatarUrl ? (
        <img className="avatar avatar-sm" src={avatarUrl} alt="" referrerPolicy="no-referrer"/>
    ) : (
        <div className="avatar avatar-sm avatar-initials" aria-hidden="true">
            {initials(name)}
        </div>
    )
}

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
        <div className="team-picker">
            <div className="autocomplete">
                <input
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="Add teammates by name…"
                    aria-label="Search members"
                />
                {matches.length > 0 && (
                    <ul className="autocomplete-list">
                        {matches.map((p) => (
                            <li key={p.id}>
                                <button
                                    type="button"
                                    onClick={() => {
                                        onChange([...memberIds, p.id], leadId)
                                        setQuery('')
                                    }}
                                >
                                    <MemberAvatar name={p.name} avatarUrl={p.avatarUrl}/>
                                    <span>{p.name}</span>
                                    {p.company && <span className="mono muted">{p.company}</span>}
                                </button>
                            </li>
                        ))}
                    </ul>
                )}
            </div>
            <ul className="member-list">
                {memberIds.map((id) => {
                    const person = byId.get(id)
                    if (!person) return null
                    const isLead = id === leadId
                    return (
                        <li key={id} className="member-row">
                            <MemberAvatar name={person.name} avatarUrl={person.avatarUrl}/>
                            <span className="member-name">{person.name}</span>
                            {isLead ? (
                                <span className="badge badge-board">team lead</span>
                            ) : (
                                <button
                                    type="button"
                                    className="link-btn mono"
                                    onClick={() => onChange(memberIds, id)}
                                >
                                    make lead
                                </button>
                            )}
                            {!isLead && !lockedIds.includes(id) && (
                                <button
                                    type="button"
                                    className="link-btn mono"
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
