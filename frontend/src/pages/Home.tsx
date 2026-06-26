import { useMemo } from 'react'
import { usePeople } from '../features/people/queries'
import type { Person } from '../features/people/types'
import { useProjects } from '../features/projects/queries'
import CompanyMarquee from '../features/people/CompanyMarquee'
import { ProjectCard } from '../features/projects/ProjectCard'
import { Avatar } from '../components/Avatar'
import { AnchorButton, LinkButton } from '../components/Button'
import { Eyebrow } from '../components/Eyebrow'
import { container } from '../components/styles'

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

const TIMELINE = [
    {
        title: 'Planning phase',
        body:
            "Pitch ideas, find a team to work with, and write a design doc for your plan. You're welcome " +
            'to continue projects semester over semester.',
    },
    {
        title: 'Build time',
        body: 'Turn the design doc into working software through hard work.',
    },
    {
        title: 'MVP presentation',
        body:
            'Demo a working core to the club, present a thorough slideshow of your idea, or any means to ' +
            'gather the feedback you and your team need.',
    },
    {
        title: 'Build time',
        body: 'Iterate on what you learned and polish the rough edges.',
    },
    {
        title: 'Final presentation',
        body: 'Show off the finished product at the end of the semester.',
    },
]

export default function Home() {
    const { data: projects = [] } = useProjects()
    const { data: people = [] } = usePeople()

    const activeProjects = useMemo(() => projects.filter((p) => p.active), [projects])

    const companies = useMemo(() => {
        const seen = new Set<string>()
        for (const p of people) {
            const c = p.company?.trim()
            if (c) seen.add(c)
        }
        return [...seen].sort()
    }, [people])

    const board = useMemo(
        () =>
            people
                .filter((p) => p.role === 'BOARD')
                .sort((a, b) => {
                    const rank = (p: Person) => {
                        const i = BOARD_ORDER.indexOf(p.title ?? '')
                        return i === -1 ? BOARD_ORDER.length : i
                    }
                    return rank(a) - rank(b) || a.name.localeCompare(b.name)
                }),
        [people],
    )

    return (
        <>
            <section className="border-b border-line-soft bg-[radial-gradient(48rem_24rem_at_80%_-20%,rgb(180_6_57/0.18),transparent_65%),radial-gradient(36rem_20rem_at_5%_115%,rgb(255_211_2/0.07),transparent_60%)]">
                <div
                    className={`${container} flex items-center gap-14 pb-[5.5rem] pt-24 max-md:flex-col max-md:items-start max-md:gap-8 max-md:py-14`}
                >
                    <img
                        src="/logo.svg"
                        alt=""
                        className="w-[230px] shrink-0 drop-shadow-[0_0_32px_rgb(255_211_2/0.18)] max-md:w-[150px]"
                    />
                    <div>
                        <h1 className="mb-[0.4rem] text-[clamp(2.6rem,6vw,4rem)] font-extrabold">
                            Social Coding
                        </h1>
                        <p className="mb-5 font-mono text-[0.8rem] text-gold">
                            at the University of Minnesota Twin Cities
                        </p>
                        <p className="mb-8 max-w-[54ch] text-[1.13rem] text-text-soft">
                            A student-run community for building real software with real teammates.
                            Whatever your major or experience level, if you want to ship something,
                            you belong here.
                        </p>
                        <div className="flex flex-wrap gap-[0.8rem]">
                            <AnchorButton
                                lg
                                href="https://discord.gg/5GGURYZPpp"
                                target="_blank"
                                rel="noreferrer"
                            >
                                Get Involved
                            </AnchorButton>

                            <AnchorButton
                                variant="ghost"
                                lg
                                href="https://discord.gg/AkS5tufeND"
                                target="_blank"
                                rel="noreferrer"
                            >
                                <svg
                                    viewBox="0 0 16 16"
                                    width="18"
                                    height="18"
                                    fill="currentColor"
                                    aria-hidden="true"
                                >
                                    <path d="M13.545 2.907a13.227 13.227 0 0 0-3.257-1.011.05.05 0 0 0-.052.025c-.141.25-.297.577-.406.833a12.19 12.19 0 0 0-3.658 0 8.258 8.258 0 0 0-.412-.833.051.051 0 0 0-.052-.025c-1.125.194-2.22.534-3.257 1.011a.041.041 0 0 0-.021.018C.356 6.024-.213 9.047.066 12.032c.001.014.01.028.021.037a13.276 13.276 0 0 0 3.995 2.02.05.05 0 0 0 .056-.019c.308-.42.582-.863.818-1.329a.05.05 0 0 0-.01-.059.051.051 0 0 0-.018-.011 8.875 8.875 0 0 1-1.248-.595.05.05 0 0 1-.02-.066.051.051 0 0 1 .015-.019c.084-.063.168-.129.248-.195a.05.05 0 0 1 .051-.007c2.619 1.196 5.454 1.196 8.041 0a.052.052 0 0 1 .053.007c.08.066.164.132.248.195a.051.051 0 0 1-.004.085 8.254 8.254 0 0 1-1.249.594.05.05 0 0 0-.03.03.052.052 0 0 0 .003.041c.24.465.515.909.817 1.329a.05.05 0 0 0 .056.019 13.235 13.235 0 0 0 4.001-2.02.049.049 0 0 0 .021-.037c.334-3.451-.559-6.449-2.366-9.106a.034.034 0 0 0-.02-.019Zm-8.198 7.307c-.789 0-1.438-.724-1.438-1.612 0-.889.637-1.613 1.438-1.613.807 0 1.45.73 1.438 1.613 0 .888-.637 1.612-1.438 1.612Zm5.316 0c-.788 0-1.438-.724-1.438-1.612 0-.889.637-1.613 1.438-1.613.807 0 1.451.73 1.438 1.613 0 .888-.631 1.612-1.438 1.612Z" />
                                </svg>
                                Discord
                            </AnchorButton>

                            <AnchorButton
                                variant="ghost"
                                lg
                                href="https://github.umn.edu/Minnesota-Social-Coding"
                                target="_blank"
                                rel="noreferrer"
                            >
                                <svg
                                    viewBox="0 0 16 16"
                                    width="18"
                                    height="18"
                                    fill="currentColor"
                                    aria-hidden="true"
                                >
                                    <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.42 7.42 0 0 1 2-.27c.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z" />
                                </svg>
                                GitHub
                            </AnchorButton>
                        </div>
                    </div>
                </div>
            </section>

            <section className="border-b border-line-soft bg-bg-raised">
                <div
                    className={`${container} grid grid-cols-3 gap-8 py-9 max-md:grid-cols-1 max-md:gap-5`}
                >
                    {[
                        ['#1', 'largest programming club at the University of Minnesota'],
                        ['100+', 'projects created since 2016'],
                        ['1,000+', 'members and counting'],
                    ].map(([value, label], i) => (
                        <div
                            key={label}
                            className={`flex flex-col gap-[0.2rem] ${
                                i > 0
                                    ? 'border-line max-md:border-t max-md:pt-5 md:border-l md:pl-8'
                                    : ''
                            }`}
                        >
                            <strong className="text-[2rem] font-extrabold leading-[1.1] tracking-[-0.02em] text-gold">
                                {value}
                            </strong>
                            <span className="max-w-[30ch] text-[0.92rem] text-text-soft">
                                {label}
                            </span>
                        </div>
                    ))}
                </div>
            </section>

            <section className={`${container} pb-4 pt-16`}>
                <Eyebrow>About us</Eyebrow>
                <h2>Coursework teaches syntax. We practice software.</h2>
                <div className="max-w-[68ch] text-text-soft">
                    <p className="mb-4">
                        Since 2016, Social Coding has been a vibrant community where students
                        collaborate on projects, learn industry skills, and express their creativity
                        through technology. Our foundation lies in the shared passion of friends
                        coding together and the desire to bridge the gap between coursework and
                        industry. As such, our mission is to create a dynamic space for students of
                        all backgrounds and disciplines to learn from one another and create amazing
                        code.
                    </p>

                    <p className="mb-4">
                        Social Coding is dedicated to breaking the barrier of entry into Software
                        Engineering, we believe that everyone has something to contribute and thus
                        do not require any prior experience to join.
                    </p>
                </div>
            </section>

            <section className={`${container} pb-4 pt-16`}>
                <Eyebrow>How a semester works</Eyebrow>
                <h2>The Social Coding timeline</h2>
                <ol className="mt-9 flex max-w-[680px] flex-col gap-7">
                    {TIMELINE.map((phase, i) => (
                        <li key={`${phase.title}-${i}`} className="relative pl-[2.4rem]">
                            <span
                                aria-hidden="true"
                                className="absolute left-0 top-0 z-[1] flex h-[1.6rem] w-[1.6rem] items-center justify-center rounded-full bg-gold font-mono text-[0.8rem] font-bold text-ink"
                            >
                                {i + 1}
                            </span>
                            {i < TIMELINE.length - 1 && (
                                <span
                                    aria-hidden="true"
                                    className="absolute -bottom-7 left-3 top-[1.8rem] w-[2px] bg-line"
                                />
                            )}
                            <h3 className="mb-1 text-base">{phase.title}</h3>
                            <p className="m-0 text-[0.9rem] text-text-soft">{phase.body}</p>
                        </li>
                    ))}
                </ol>
            </section>

            {companies.length > 0 && (
                <section className="overflow-hidden pb-4 pt-16">
                    <div className={container}>
                        <Eyebrow>Alumni &amp; members</Eyebrow>
                        <h2>Where our people end up</h2>
                    </div>
                    <CompanyMarquee companies={companies} />
                </section>
            )}

            {board.length > 0 && (
                <section className={`${container} pb-4 pt-16`}>
                    <Eyebrow>Board</Eyebrow>
                    <h2>Meet the board</h2>
                    <ul className="mt-7 grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-x-8 gap-y-[1.4rem]">
                        {board.map((p) => (
                            <li key={p.id} className="flex items-center gap-[0.85rem]">
                                <Avatar name={p.name} avatarUrl={p.avatarUrl} />
                                <div>
                                    <p className="m-0 font-semibold">{p.name}</p>
                                    <p className="mb-0 mt-[0.1rem] font-mono text-[0.7rem] font-semibold uppercase tracking-[0.12em] text-gold">
                                        {p.title ?? 'Board'}
                                    </p>
                                </div>
                            </li>
                        ))}
                    </ul>
                </section>
            )}

            <section className={`${container} pb-4 pt-16`}>
                <div className="mb-7 flex items-end justify-between gap-4">
                    <div>
                        <Eyebrow>Projects</Eyebrow>
                        <h2>Currently active</h2>
                    </div>

                    <LinkButton variant="ghost" to="/projects">
                        View all →
                    </LinkButton>
                </div>
                <div className="grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-[1.1rem]">
                    {activeProjects.slice(0, 6).map((p) => (
                        <ProjectCard key={p.id} project={p} />
                    ))}
                </div>
            </section>

            <section className={`${container} pb-4 pt-16`}>
                <div className="flex flex-wrap items-center justify-between gap-8 rounded-xl border border-line px-8 py-9">
                    <div>
                        <h2 className="text-2xl">Have an idea worth building?</h2>
                        <p className="m-0 max-w-[52ch] text-text-soft">
                            Join us at our meetings, pitch your idea, get a group, and start
                            building.
                        </p>
                    </div>
                    <LinkButton
                        lg
                        to="/projects/new"
                        onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
                    >
                        Create a project
                    </LinkButton>
                </div>
            </section>
        </>
    )
}
