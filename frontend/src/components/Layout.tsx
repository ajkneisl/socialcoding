import { useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth-context'
import { LinkButton } from './Button'
import { Avatar } from './Avatar'
import { container } from './styles'

function Logo() {
    return (
        <Link
            to="/"
            className="inline-flex items-center gap-[0.65rem] text-[1.02rem] font-bold text-text hover:no-underline"
        >
            <img src="/logo.svg" alt="Social Coding" className="block h-10 w-auto" />
            <span className="max-md:hidden">Social Coding</span>
        </Link>
    )
}

function navLinkClass({ isActive }: { isActive: boolean }) {
    return `border-b-2 py-1 text-[0.95rem] font-medium hover:no-underline ${
        isActive ? 'border-crimson text-gold' : 'border-transparent text-text-soft hover:text-text'
    }`
}

export default function Layout() {
    const { user } = useAuth()
    const [menuOpen, setMenuOpen] = useState(false)

    const navLinks = (
        <>
            <NavLink to="/projects" className={navLinkClass} onClick={() => setMenuOpen(false)}>
                Projects
            </NavLink>

            <NavLink to="/events" className={navLinkClass} onClick={() => setMenuOpen(false)}>
                Events
            </NavLink>

            <NavLink to="/people" className={navLinkClass} onClick={() => setMenuOpen(false)}>
                People
            </NavLink>

            {user?.role === 'BOARD' && (
                <NavLink to="/board" className={navLinkClass} onClick={() => setMenuOpen(false)}>
                    Board
                </NavLink>
            )}
        </>
    )

    return (
        <div className="flex min-h-screen flex-col">
            <header className="sticky top-0 z-50 border-b border-line-soft bg-bg/82 backdrop-blur-[12px]">
                <nav className={`${container} flex h-[68px] items-center gap-8 max-md:gap-4`}>
                    <Logo />

                    <div className="flex flex-1 gap-6 max-md:hidden">{navLinks}</div>

                    <div className="flex items-center gap-[0.6rem] max-md:flex-1 max-md:justify-end">
                        {user ? (
                            <Link to="/account" aria-label="Account" className="hover:no-underline">
                                <Avatar name={user.name} avatarUrl={user.avatarUrl} size="sm" />
                            </Link>
                        ) : (
                            <LinkButton to="/account">Sign in</LinkButton>
                        )}

                        <button
                            type="button"
                            className="hidden cursor-pointer items-center justify-center rounded-lg border border-line p-2 text-text hover:bg-bg-raised max-md:inline-flex"
                            aria-label="Toggle navigation menu"
                            aria-expanded={menuOpen}
                            onClick={() => setMenuOpen((open) => !open)}
                        >
                            <svg
                                viewBox="0 0 24 24"
                                width="22"
                                height="22"
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="2"
                                strokeLinecap="round"
                                aria-hidden="true"
                            >
                                {menuOpen ? (
                                    <path d="M6 6l12 12M18 6L6 18" />
                                ) : (
                                    <path d="M4 7h16M4 12h16M4 17h16" />
                                )}
                            </svg>
                        </button>
                    </div>
                </nav>

                {menuOpen && (
                    <div className="hidden border-t border-line-soft max-md:block">
                        <div className={`${container} flex flex-col gap-1 py-3`}>{navLinks}</div>
                    </div>
                )}
            </header>

            <main className="flex-1">
                <Outlet />
            </main>

            <footer className="mt-16 border-t border-line-soft bg-bg-raised pb-8 pt-14 text-text-soft">
                <div
                    className={`${container} grid grid-cols-[2fr_1fr_1fr] gap-8 max-md:grid-cols-1`}
                >
                    <div className="flex flex-col items-start gap-[0.4rem]">
                        <Logo />

                        <p className="mb-0 mt-1 max-w-[40ch]">
                            A student-run software development community at the University of
                            Minnesota Twin Cities. Everyone is welcome, no experience required.
                        </p>

                        <p className="m-0 max-w-[40ch] font-mono text-[0.8rem]">
                            Weekly Meetings · Bruininks Hall 312
                        </p>
                    </div>

                    <div className="flex flex-col items-start gap-[0.4rem]">
                        <h4 className="mb-2 text-[0.8rem] uppercase tracking-[0.08em] text-text">
                            Site
                        </h4>

                        <Link to="/projects" className="text-text-soft hover:text-gold">
                            Projects
                        </Link>

                        <Link to="/events" className="text-text-soft hover:text-gold">
                            Events
                        </Link>

                        <Link to="/people" className="text-text-soft hover:text-gold">
                            People
                        </Link>

                        <Link to="/account" className="text-text-soft hover:text-gold">
                            Account
                        </Link>
                    </div>

                    <div className="flex flex-col items-start gap-[0.4rem]">
                        <h4 className="mb-2 text-[0.8rem] uppercase tracking-[0.08em] text-text">
                            Connect
                        </h4>

                        <a href="mailto:coding@umn.edu" className="text-text-soft hover:text-gold">
                            coding@umn.edu
                        </a>

                        <a
                            href="https://github.umn.edu/Minnesota-Social-Coding"
                            target="_blank"
                            rel="noreferrer"
                            className="text-text-soft hover:text-gold"
                        >
                            GitHub
                        </a>

                        <a
                            href="https://www.linkedin.com/company/social-coding-umn/"
                            target="_blank"
                            rel="noreferrer"
                            className="text-text-soft hover:text-gold"
                        >
                            LinkedIn
                        </a>
                    </div>
                </div>

                <div
                    className={`${container} mt-10 border-t border-line-soft pt-6 text-[0.85rem] text-text-faint`}
                >
                    <p className="m-0">
                        Social Coding @ UMN is a registered student organization and is not directly
                        affiliated with the University of Minnesota.
                    </p>
                </div>
            </footer>
        </div>
    )
}
