import { Avatar } from '../../components/Avatar'
import { card } from '../../components/styles'
import type { Person } from './types'

export function PersonCard({ person }: { person: Person }) {
    return (
        <article
            className={`${card} flex items-start gap-4 px-5 py-[1.15rem] transition-[border-color,transform] duration-150 hover:-translate-y-0.5 hover:border-text-faint`}
        >
            <Avatar name={person.name} avatarUrl={person.avatarUrl} />
            <div>
                <h3 className="m-0 flex flex-wrap items-center gap-2 text-base">{person.name}</h3>
                {person.role === 'BOARD' && (
                    <p className="mb-0 mt-[0.15rem] font-mono text-[0.72rem] font-semibold uppercase tracking-[0.12em] text-gold">
                        {person.title ?? 'Board'}
                    </p>
                )}
                <p className="mb-0 mt-[0.15rem] font-mono text-[0.8rem] text-text-soft">
                    {[
                        person.joinedTerm && `joined ${person.joinedTerm}`,
                        person.gradYear && `class of ${person.gradYear}`,
                    ]
                        .filter(Boolean)
                        .join(' · ') || 'member'}
                </p>
                {person.company && (
                    <p className="mb-0 mt-[0.15rem] font-mono text-[0.8rem] text-gold">
                        {person.company}
                    </p>
                )}
                <div className="mt-[0.4rem] flex gap-4 font-mono text-[0.8rem]">
                    {person.github && (
                        <a
                            href={`https://github.com/${person.github}`}
                            target="_blank"
                            rel="noreferrer"
                        >
                            github
                        </a>
                    )}
                    {person.linkedin && (
                        <a href={person.linkedin} target="_blank" rel="noreferrer">
                            linkedin
                        </a>
                    )}
                    {person.website && (
                        <a href={person.website} target="_blank" rel="noreferrer">
                            portfolio
                        </a>
                    )}
                </div>
            </div>
        </article>
    )
}
