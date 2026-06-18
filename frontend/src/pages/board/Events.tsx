import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
    useCreateEvent,
    useDeleteEvent,
    useEvents,
    useUpdateEvent,
} from '../../features/events/queries'
import type { Event } from '../../features/events/types'
import { Button } from '../../components/Button'
import { FormError } from '../../components/FormError'
import { Pagination } from '../../components/Pagination'
import { usePaged } from '../../components/usePaged'
import { SectionHead } from '../../components/SectionHead'
import { card } from '../../components/styles'

const row = 'border-b border-line px-1 py-[1.4rem] hover:bg-bg-raised'
const PAGE_SIZE = 8

const emptyEvent = {
    title: '',
    summary: '',
    body: '',
    startsAt: '',
    location: '',
    burrowUrl: '',
    imageUrl: '',
}

/** Epoch ms → the local "YYYY-MM-DDTHH:mm" string a datetime-local input expects. */
function toLocalInput(ms: number) {
    const d = new Date(ms)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(
        d.getHours(),
    )}:${pad(d.getMinutes())}`
}

export default function BoardEvents() {
    const { data: events = [] } = useEvents()
    const createEvent = useCreateEvent()
    const updateEvent = useUpdateEvent()
    const deleteEvent = useDeleteEvent()

    const [form, setForm] = useState(emptyEvent)
    const [editingId, setEditingId] = useState<number | null>(null)
    const formRef = useRef<HTMLDivElement>(null)

    const { page, setPage, pageCount, pageItems } = usePaged(events, PAGE_SIZE)

    function set<K extends keyof typeof form>(key: K, value: string) {
        setForm((f) => ({ ...f, [key]: value }))
    }

    function reset() {
        setForm(emptyEvent)
        setEditingId(null)
    }

    function startEdit(event: Event) {
        setEditingId(event.id)
        setForm({
            title: event.title,
            summary: event.summary,
            body: event.body,
            startsAt: toLocalInput(event.startsAt),
            location: event.location ?? '',
            burrowUrl: event.burrowUrl ?? '',
            imageUrl: event.imageUrl ?? '',
        })
        formRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }

    function submit() {
        if (!valid) return
        const payload = {
            title: form.title,
            summary: form.summary,
            body: form.body || undefined,
            startsAt: new Date(form.startsAt).getTime(),
            location: form.location || undefined,
            burrowUrl: form.burrowUrl || undefined,
            imageUrl: form.imageUrl || undefined,
        }
        if (editingId != null) {
            updateEvent.mutate({ id: editingId, event: payload }, { onSuccess: reset })
        } else {
            createEvent.mutate(payload, { onSuccess: reset })
        }
    }

    const valid = form.title.trim() && form.summary.trim() && form.startsAt
    const editing = editingId != null
    const busy = createEvent.isPending || updateEvent.isPending
    const error = (editing ? updateEvent.error : createEvent.error)?.message

    return (
        <>
            <div ref={formRef}>
                <SectionHead title={editing ? 'Edit event' : 'Publish an event'}>
                    Events show up on the public Events page and its calendar.
                </SectionHead>
            </div>

            <div className={card}>
                <div className="flex flex-col gap-4">
                    <div className="flex gap-4 max-md:flex-col">
                        <label>
                            Title
                            <input
                                value={form.title}
                                onChange={(e) => set('title', e.target.value)}
                                placeholder="Spring Tech Kickoff"
                                maxLength={200}
                            />
                        </label>
                        <label>
                            Date &amp; time
                            <input
                                type="datetime-local"
                                value={form.startsAt}
                                onChange={(e) => set('startsAt', e.target.value)}
                            />
                        </label>
                    </div>
                    <label>
                        Summary
                        <input
                            value={form.summary}
                            onChange={(e) => set('summary', e.target.value)}
                            placeholder="One-line blurb shown in the list"
                        />
                    </label>
                    <label>
                        Details{' '}
                        <span className="text-text-soft">
                            (shown under “Read more” · supports Markdown)
                        </span>
                        <textarea
                            value={form.body}
                            onChange={(e) => set('body', e.target.value)}
                            rows={6}
                            placeholder="The full write-up… **bold**, _italic_, lists, [links](https://…)"
                        />
                    </label>
                    <div className="flex gap-4 max-md:flex-col">
                        <label>
                            Location <span className="text-text-soft">(optional)</span>
                            <input
                                value={form.location}
                                onChange={(e) => set('location', e.target.value)}
                                placeholder="Bruininks Hall 315"
                            />
                        </label>
                        <label>
                            Burrow link <span className="text-text-soft">(optional)</span>
                            <input
                                value={form.burrowUrl}
                                onChange={(e) => set('burrowUrl', e.target.value)}
                                placeholder="https://burrow.org/event/…"
                            />
                        </label>
                    </div>
                    <label>
                        Image URL <span className="text-text-soft">(optional)</span>
                        <input
                            value={form.imageUrl}
                            onChange={(e) => set('imageUrl', e.target.value)}
                            placeholder="https://…/poster.png"
                        />
                    </label>
                    <FormError error={error} />
                    <div className="flex gap-[0.6rem]">
                        <Button disabled={!valid || busy} onClick={submit}>
                            {editing ? 'Save changes' : 'Publish event'}
                        </Button>
                        {editing && (
                            <Button variant="ghost" disabled={busy} onClick={reset}>
                                Cancel
                            </Button>
                        )}
                    </div>
                </div>
            </div>

            {events.length > 0 && (
                <>
                    <SectionHead title="Published events" className="mt-14" />
                    <div className="border-t border-line">
                        {pageItems.map((event) => (
                            <article
                                key={event.id}
                                className={`${row} flex flex-wrap items-center justify-between gap-4`}
                            >
                                <div className="min-w-0">
                                    <h3 className="m-0 text-base">
                                        <Link
                                            to={`/events/${event.id}`}
                                            className="text-text hover:text-gold hover:no-underline"
                                        >
                                            {event.title}
                                        </Link>
                                    </h3>
                                    <p className="mb-0 mt-[0.1rem] font-mono text-[0.8rem] text-text-soft">
                                        {new Date(event.startsAt).toLocaleString()}
                                        {event.location && <> · {event.location}</>}
                                    </p>
                                </div>
                                <div className="flex gap-[0.6rem]">
                                    <Button
                                        variant="ghost"
                                        disabled={busy}
                                        onClick={() => startEdit(event)}
                                    >
                                        Edit
                                    </Button>
                                    <Button
                                        variant="danger"
                                        disabled={deleteEvent.isPending}
                                        onClick={() => deleteEvent.mutate(event.id)}
                                    >
                                        Delete
                                    </Button>
                                </div>
                            </article>
                        ))}
                    </div>
                    <Pagination page={page} pageCount={pageCount} onChange={setPage} />
                </>
            )}
        </>
    )
}
