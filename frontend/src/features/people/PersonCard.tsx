import { Avatar } from '../../components/Avatar'
import { card } from '../../components/styles'
import type { Person } from './types'

const socialLink =
    'inline-flex text-text-soft transition-[color,transform] duration-150 hover:-translate-y-0.5 hover:scale-110 hover:text-gold hover:no-underline'

const githubLogo = (
    <svg viewBox="0 0 16 16" width="18" height="18" fill="currentColor" aria-hidden="true">
        <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.42 7.42 0 0 1 2-.27c.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z" />
    </svg>
)

const linkedinLogo = (
    <svg viewBox="0 0 16 16" width="18" height="18" fill="currentColor" aria-hidden="true">
        <path d="M13.632 13.635h-2.37V9.922c0-.886-.018-2.025-1.234-2.025-1.235 0-1.424.964-1.424 1.96v3.778h-2.37V6H8.51v1.04h.033c.317-.6 1.091-1.233 2.246-1.233 2.401 0 2.845 1.58 2.845 3.637v4.188zM3.558 4.955a1.376 1.376 0 1 1 0-2.751 1.376 1.376 0 0 1 0 2.751zm1.187 8.68H2.37V6h2.376v7.635zM14.816 0H1.18C.528 0 0 .516 0 1.153v13.694C0 15.482.528 16 1.18 16h13.635c.652 0 1.185-.518 1.185-1.153V1.153C16 .516 15.467 0 14.816 0z" />
    </svg>
)

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
                <div className="mt-[0.4rem] flex items-center gap-3 font-mono text-[0.8rem]">
                    {person.github && (
                        <a
                            href={`https://github.com/${person.github}`}
                            target="_blank"
                            rel="noreferrer"
                            className={socialLink}
                            aria-label={`${person.name} on GitHub`}
                        >
                            {githubLogo}
                        </a>
                    )}
                    {person.linkedin && (
                        <a
                            href={person.linkedin}
                            target="_blank"
                            rel="noreferrer"
                            className={socialLink}
                            aria-label={`${person.name} on LinkedIn`}
                        >
                            {linkedinLogo}
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
