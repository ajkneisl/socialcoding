import { Link, useParams } from 'react-router-dom'
import { useEvent } from '../features/events/queries'
import { checkInClosed } from '../features/events/util'
import { AnchorButton, Button, LinkButton } from '../components/Button'
import { ExternalLinkIcon } from '../components/ExternalLinkIcon'
import { Markdown } from '../components/Markdown'
import { PageMessage } from '../components/PageMessage'
import { page } from '../components/styles'

export default function EventDetail() {
    const { id } = useParams()
    const { data: event, isLoading } = useEvent(Number(id))

    if (isLoading) {
        return <PageMessage>Loading…</PageMessage>
    }

    if (!event) {
        return (
            <section className={page}>
                <h2>Event not found</h2>
                <p className="text-text-soft">
                    This event may have been removed.{' '}
                    <Link to="/events">Back to all events →</Link>
                </p>
            </section>
        )
    }

    const date = new Date(event.startsAt)

    return (
        <section className={page}>
            <Link to="/events" className="font-mono text-[0.8rem]">
                ← All events
            </Link>

            <article className="mt-6 max-w-[760px]">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-[0.8rem]">
                    <time dateTime={date.toISOString()} className="text-gold">
                        {date.toLocaleDateString(undefined, {
                            weekday: 'long',
                            month: 'long',
                            day: 'numeric',
                            year: 'numeric',
                        })}
                        {' · '}
                        {date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })}
                    </time>
                    {event.location && <span className="text-text-soft">· {event.location}</span>}
                </div>

                <h1 className="mb-2 mt-2 text-[clamp(2rem,4vw,2.8rem)] font-extrabold">
                    {event.title}
                </h1>

                {event.imageUrl && (
                    <img
                        src={event.imageUrl}
                        alt=""
                        className="my-6 max-h-[440px] w-full rounded-xl border border-line object-cover"
                    />
                )}

                <p className="text-[1.13rem] text-text-soft">{event.summary}</p>

                {event.body && <Markdown className="mt-4 max-w-[70ch]">{event.body}</Markdown>}

                {(event.burrowUrl || event.attendance) && (
                    <div className="mt-8 flex flex-wrap gap-3">
                        {event.attendance &&
                            (checkInClosed(event.startsAt) ? (
                                <Button disabled title="Check-in has closed">
                                    Check in
                                </Button>
                            ) : (
                                <LinkButton to={`/events/${event.id}/attend`}>
                                    Check in
                                </LinkButton>
                            ))}
                        {event.burrowUrl && (
                            <AnchorButton
                                variant={event.attendance ? 'ghost' : 'primary'}
                                href={event.burrowUrl}
                                target="_blank"
                                rel="noreferrer"
                            >
                                View Event on Burrow
                                <ExternalLinkIcon />
                            </AnchorButton>
                        )}
                    </div>
                )}

                <p className="mb-0 mt-8 font-mono text-[0.75rem] text-text-faint">
                    Posted by {event.authorName}
                </p>
            </article>
        </section>
    )
}
