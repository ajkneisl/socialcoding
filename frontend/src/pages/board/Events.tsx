import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { AttendancePanel } from '../../features/events/AttendancePanel'
import {
    useCreateEvent,
    useDeleteEvent,
    useEvents,
    useUpdateEvent,
} from '../../features/events/queries'
import type { Event } from '../../features/events/types'
import { Button } from '../../components/Button'
import { FormError } from '../../components/FormError'
import { ImageUpload } from '../../components/ImageUpload'
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
    attendance: false,
}

function PublishedRow({
    event,
    busy,
    deleting,
    onEdit,
    onDelete,
}: {
    event: Event
    busy: boolean
    deleting: boolean
    onEdit: () => void
    onDelete: () => void
}) {
    const [showAttendance, setShowAttendance] = useState(false)

    return (
        <div>
            <article className={`${row} flex flex-wrap items-center justify-between gap-4`}>
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
                        {event.attendance && <> · attendance on</>}
                    </p>
                </div>
                <div className="flex gap-[0.6rem]">
                    {event.attendance && (
                        <Button variant="ghost" onClick={() => setShowAttendance((s) => !s)}>
                            {showAttendance ? 'Hide' : 'Attendance'}
                        </Button>
                    )}
                    <Button variant="ghost" disabled={busy} onClick={onEdit}>
                        Edit
                    </Button>
                    <Button variant="danger" disabled={deleting} onClick={onDelete}>
                        Delete
                    </Button>
                </div>
            </article>
            {event.attendance && showAttendance && (
                <div className="px-1 pb-5">
                    <AttendancePanel event={event} />
                </div>
            )}
        </div>
    )
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
    const [creating, setCreating] = useState(false)
    const [editingId, setEditingId] = useState<number | null>(null)
    const formRef = useRef<HTMLDivElement>(null)

    const { page, setPage, pageCount, pageItems } = usePaged(events, PAGE_SIZE)

    function set<K extends keyof typeof form>(key: K, value: (typeof form)[K]) {
        setForm((f) => ({ ...f, [key]: value }))
    }

    function scrollToForm() {
        requestAnimationFrame(() =>
            formRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
        )
    }

    function reset() {
        setForm(emptyEvent)
        setEditingId(null)
        setCreating(false)
    }

    function startCreate() {
        setForm(emptyEvent)
        setEditingId(null)
        setCreating(true)
        createEvent.reset()
        scrollToForm()
    }

    function startEdit(event: Event) {
        setCreating(false)
        setEditingId(event.id)
        setForm({
            title: event.title,
            summary: event.summary,
            body: event.body,
            startsAt: toLocalInput(event.startsAt),
            location: event.location ?? '',
            burrowUrl: event.burrowUrl ?? '',
            imageUrl: event.imageUrl ?? '',
            attendance: event.attendance,
        })
        updateEvent.reset()
        scrollToForm()
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
            attendance: form.attendance,
        }
        if (editingId != null) {
            updateEvent.mutate({ id: editingId, event: payload }, { onSuccess: reset })
        } else {
            createEvent.mutate(payload, { onSuccess: reset })
        }
    }

    const valid = form.title.trim() && form.summary.trim() && form.startsAt
    const editing = editingId != null
    const showForm = creating || editing
    const busy = createEvent.isPending || updateEvent.isPending
    const error = (editing ? updateEvent.error : createEvent.error)?.message

    return (
        <>
            <div className="mb-7 flex flex-wrap items-center justify-between gap-4">
                <h2 className="m-0">Events</h2>
                {!showForm && <Button onClick={startCreate}>New event</Button>}
            </div>

            {showForm && (
                <div ref={formRef} className="mb-14">
                    <SectionHead title={editing ? 'Edit event' : 'Publish an event'} />
                    <div className={card}>
                        <div className="flex flex-col gap-4">
                            <div className="flex gap-4 max-md:flex-col">
                                <label>
                                    Title
                                    <input
                                        value={form.title}
                                        onChange={(e) => set('title', e.target.value)}
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
                                />
                            </label>
                            <label>
                                Details{' '}
                                <textarea
                                    value={form.body}
                                    onChange={(e) => set('body', e.target.value)}
                                    rows={6}
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
                                Event image <span className="text-text-soft">(optional)</span>
                                <ImageUpload
                                    value={form.imageUrl}
                                    onChange={(url) => set('imageUrl', url)}
                                />
                            </label>
                            <label className="flex-row items-center gap-2">
                                <input
                                    type="checkbox"
                                    className="h-4 w-4 cursor-pointer accent-gold p-0"
                                    checked={form.attendance}
                                    onChange={(e) => set('attendance', e.target.checked)}
                                />
                                Track attendance
                            </label>
                            <FormError error={error} />
                            <div className="flex gap-[0.6rem]">
                                <Button disabled={!valid || busy} onClick={submit}>
                                    {editing ? 'Save changes' : 'Publish event'}
                                </Button>
                                <Button variant="ghost" disabled={busy} onClick={reset}>
                                    Cancel
                                </Button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {events.length > 0 ? (
                <>
                    <div className="border-t border-line">
                        {pageItems.map((event) => (
                            <PublishedRow
                                key={event.id}
                                event={event}
                                busy={busy}
                                deleting={deleteEvent.isPending}
                                onEdit={() => startEdit(event)}
                                onDelete={() => deleteEvent.mutate(event.id)}
                            />
                        ))}
                    </div>
                    <Pagination page={page} pageCount={pageCount} onChange={setPage} />
                </>
            ) : (
                !showForm && (
                    <p className="border-t border-line py-[1.4rem] text-text-soft">
                        No events yet. Create one to get things rolling.
                    </p>
                )
            )}
        </>
    )
}
