import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../../auth-context'
import { Eyebrow } from '../../components/Eyebrow'
import { PageMessage } from '../../components/PageMessage'
import { page } from '../../components/styles'

function tabClass({ isActive }: { isActive: boolean }) {
    return `-mb-px border-b-2 pb-3 text-[0.95rem] font-medium hover:no-underline ${
        isActive ? 'border-crimson text-gold' : 'border-transparent text-text-soft hover:text-text'
    }`
}

/** Shared shell for the board dashboard: gates on the BOARD role and renders the tab nav. */
export default function BoardLayout() {
    const { user, loading } = useAuth()

    if (loading) {
        return <PageMessage>Loading…</PageMessage>
    }

    if (!user || user.role !== 'BOARD') {
        return (
            <section className={page}>
                <h2>Board only</h2>
                <p className="text-text-soft">This page is for Social Coding board members.</p>
            </section>
        )
    }

    return (
        <section className={page}>
            <Eyebrow>Board</Eyebrow>
            <h2 className="mb-5">Board dashboard</h2>

            <nav className="mb-8 flex gap-6 border-b border-line">
                <NavLink to="/board" end className={tabClass}>
                    Overview
                </NavLink>
                <NavLink to="/board/projects" className={tabClass}>
                    Projects
                </NavLink>
                <NavLink to="/board/events" className={tabClass}>
                    Events
                </NavLink>
                <NavLink to="/board/analytics" className={tabClass}>
                    Analytics
                </NavLink>
                <NavLink to="/board/settings" className={tabClass}>
                    Settings
                </NavLink>
            </nav>

            <Outlet />
        </section>
    )
}
