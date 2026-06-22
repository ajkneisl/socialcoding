import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../auth-context'
import { useAttendEvent, useEvent } from '../features/events/queries'
import GoogleSignIn from '../features/auth/GoogleSignIn'
import { LinkButton } from '../components/Button'
import { container, card } from '../components/styles'

function Shell({ children }: { children: ReactNode }) {
    return (
        <section className={`${container} max-w-[520px] pb-16 pt-16`}>
            <div className={`${card} px-8 py-10 text-center`}>{children}</div>
        </section>
    )
}

export default function EventAttend() {
    const { id } = useParams()
    const eventId = Number(id)
    const { user, loading } = useAuth()
    const { data: event } = useEvent(eventId)
    const attend = useAttendEvent()
    const [signInError, setSignInError] = useState('')

    // Fire the check-in exactly once, as soon as we have a signed-in user.
    const fired = useRef(false)
    useEffect(() => {
        if (!user || fired.current || Number.isNaN(eventId)) return
        fired.current = true
        attend.mutate(eventId)
    }, [user, eventId, attend])

    const eventName = event ? `“${event.title}”` : 'this event'

    if (loading) {
        return <Shell>Checking your session…</Shell>
    }

    // Not signed in: prompt Google immediately, then the effect above checks them in.
    if (!user) {
        return (
            <Shell>
                <h2>Check in to {eventName}</h2>
                <p className="text-text-soft">Sign in with your UMN Google account to check in.</p>
                <GoogleSignIn autoPrompt onError={setSignInError} />
                {signInError && <p className="mt-3 text-red-400">{signInError}</p>}
            </Shell>
        )
    }

    if (attend.isPending || attend.isIdle) {
        return <Shell>Checking you in…</Shell>
    }

    if (attend.isError) {
        return (
            <Shell>
                <h2>Couldn't check you in</h2>
                <p className="text-text-soft">{attend.error.message}</p>
                <div className="mt-6 flex justify-center">
                    <LinkButton variant="ghost" to={`/events/${eventId}`}>
                        View event
                    </LinkButton>
                </div>
            </Shell>
        )
    }

    const repeat = attend.data?.status === 'already'

    return (
        <Shell>
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-gold/15 text-3xl text-gold">
                ✓
            </div>
            <h2>{repeat ? "You're already checked in" : "You're checked in!"}</h2>
            <p className="text-text-soft">
                {repeat
                    ? `We already counted you for ${eventName}.`
                    : `Thanks for coming to ${eventName}.`}
            </p>
            <div className="mt-6 flex justify-center gap-3">
                <LinkButton to={`/events/${eventId}`}>View event</LinkButton>
                <LinkButton variant="ghost" to="/events">
                    All events
                </LinkButton>
            </div>
            <p className="mt-6">
                <Link to="/" className="font-mono text-[0.8rem]">
                    ← Home
                </Link>
            </p>
        </Shell>
    )
}
