import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Calendar } from '../features/events/Calendar'
import { useEvents } from '../features/events/queries'
import type { Event } from '../features/events/types'
import { dayKey } from '../features/events/util'
import { ExternalLinkIcon } from '../components/ExternalLinkIcon'
import { FormError } from '../components/FormError'
import { Pagination } from '../components/Pagination'
import { usePaged } from '../components/usePaged'
import { SectionHead } from '../components/SectionHead'
import { page } from '../components/styles'

const PAGE_SIZE = 8

function EventCard({ event }: { event: Event }) {
    const date = new Date(event.startsAt)
    const href = `/events/${event.id}`

    return (
        <article className="flex gap-5 border-b border-line py-7 max-md:flex-col">
            {event.imageUrl && (
                <Link to={href} className="shrink-0 hover:no-underline">
                    <img
                        src={event.imageUrl}
                        alt=""
                        className="h-[120px] w-[200px] rounded-lg border border-line object-cover max-md:h-[180px] max-md:w-full"
                    />
                </Link>
            )}

            <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-[0.8rem]">
                    <time dateTime={date.toISOString()} className="text-gold">
                        {date.toLocaleDateString(undefined, {
                            weekday: 'short',
                            month: 'long',
                            day: 'numeric',
                            year: 'numeric',
                        })}
                        {' · '}
                        {date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })}
                    </time>
                    {event.location && <span className="text-text-soft">· {event.location}</span>}
                </div>

                <h3 className="mb-1 mt-2 text-lg">
                    <Link to={href} className="text-text hover:text-gold hover:no-underline">
                        {event.title}
                    </Link>
                </h3>
                <p className="mb-0 max-w-[70ch] text-text-soft">{event.summary}</p>

                <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-2 font-mono text-[0.8rem]">
                    <Link to={href} className="text-gold hover:underline">
                        Read more →
                    </Link>
                    {event.burrowUrl && (
                        <a
                            href={event.burrowUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-[0.35rem] md:ml-auto"
                        >
                            View Event on Burrow
                            <ExternalLinkIcon />
                        </a>
                    )}
                </div>
            </div>
        </article>
    )
}

export default function Events() {
    const { data: events = [], isLoading, error } = useEvents()
    const [selectedKey, setSelectedKey] = useState<string | null>(null)

    const visible = useMemo(
        () =>
            selectedKey
                ? events.filter((e) => dayKey(new Date(e.startsAt)) === selectedKey)
                : events,
        [events, selectedKey],
    )

    const { page: pageNum, setPage, pageCount, pageItems } = usePaged(visible, PAGE_SIZE)

    return (
        <section className={page}>
            <SectionHead eyebrow="Events" title="What's happening">
                Meetings and events from across Social Coding.
            </SectionHead>

            <FormError error={error?.message} />

            <div className="grid grid-cols-[1fr_320px] items-start gap-10 max-md:grid-cols-1">
                <div className="border-t border-line">
                    {isLoading ? (
                        <p className="pt-7 text-text-soft">Loading…</p>
                    ) : visible.length === 0 ? (
                        <p className="pt-7 text-text-soft">
                            {selectedKey
                                ? 'No events on that day.'
                                : 'No events yet. Check back soon.'}
                        </p>
                    ) : (
                        <>
                            {pageItems.map((e) => (
                                <EventCard key={e.id} event={e} />
                            ))}
                            <Pagination
                                page={pageNum}
                                pageCount={pageCount}
                                onChange={setPage}
                            />
                        </>
                    )}
                </div>

                <div className="max-md:row-start-1 md:sticky md:top-[84px]">
                    <Calendar
                        events={events}
                        selectedKey={selectedKey}
                        onSelectDay={(key) => {
                            setSelectedKey(key)
                            setPage(0)
                        }}
                    />
                </div>
            </div>
        </section>
    )
}
