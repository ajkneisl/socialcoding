import { Avatar } from '../../components/Avatar'
import { GitHubIcon, LinkedInIcon } from '../../components/BrandIcons'
import { ExternalLinkIcon } from '../../components/ExternalLinkIcon'
import { card } from '../../components/styles'
import type { Person } from './types'

/** Strips the protocol and any trailing slash so a URL reads as a bare domain/path. */
function displayUrl(url: string) {
    return url.replace(/^https?:\/\//, '').replace(/\/$/, '')
}

export function PersonCard({ person }: { person: Person }) {
    const meta = [
        person.joinedTerm && `joined ${person.joinedTerm}`,
        person.gradYear && `class of ${person.gradYear}`,
    ]
        .filter(Boolean)
        .join(' · ')

    return (
        <article
            className={`${card} flex items-start gap-4 px-5 py-[1.15rem] transition-[border-color,transform] duration-150 hover:-translate-y-0.5 hover:border-text-faint`}
        >
            <Avatar name={person.name} avatarUrl={person.avatarUrl} />
            <div>
                <h3 className="m-0 flex flex-wrap items-center gap-2 text-base">
                    {person.name}
                    <span
                        className={`font-mono text-[0.72rem] font-semibold uppercase tracking-[0.12em] ${
                            person.role === 'BOARD' ? 'text-gold' : 'text-text-soft'
                        }`}
                    >
                        {person.role === 'BOARD' ? (person.title ?? 'Board') : 'Member'}
                    </span>
                </h3>
                {meta && (
                    <p className="mb-0 mt-[0.15rem] font-mono text-[0.8rem] text-text-soft">{meta}</p>
                )}
                {person.company && (
                    <p className="mb-0 mt-[0.15rem] font-mono text-[0.8rem] text-gold">
                        {person.company}
                    </p>
                )}
                <div className="mt-[0.4rem] flex flex-wrap items-center gap-3 font-mono text-[0.8rem]">
                    {person.github && (
                        <a
                            href={`https://github.com/${person.github}`}
                            target="_blank"
                            rel="noreferrer"
                            aria-label="GitHub"
                            className="text-text-soft hover:text-text"
                        >
                            <GitHubIcon />
                        </a>
                    )}
                    {person.linkedin && (
                        <a
                            href={person.linkedin}
                            target="_blank"
                            rel="noreferrer"
                            aria-label="LinkedIn"
                            className="text-text-soft hover:text-text"
                        >
                            <LinkedInIcon />
                        </a>
                    )}
                    {person.website && (
                        <a
                            href={person.website}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-[0.3rem]"
                        >
                            {displayUrl(person.website)}
                            <ExternalLinkIcon />
                        </a>
                    )}
                </div>
            </div>
        </article>
    )
}
