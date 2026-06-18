import { useState } from 'react'
import { useCreateEvent, useDeleteEvent, useEvents } from '../../features/events/queries'
import type { Event } from '../../features/events/types'
import { Button } from '../../components/Button'
import { FormError } from '../../components/FormError'
import { SectionHead } from '../../components/SectionHead'
import { card } from '../../components/styles'

const row = 'border-b border-line px-1 py-[1.4rem] hover:bg-bg-raised'

const emptyEvent = {
    title: '',
    summary: '',
    body: '',
    startsAt: '',
    location: '',
    burrowUrl: '',
    imageUrl: '',
}

export default function BoardEvents() {
    const { data: events = [] } = useEvents()
    const createEvent = useCreateEvent()
    const deleteEvent = useDeleteEvent()
    const [form, setForm] = useState(emptyEvent)

    function set<K extends keyof typeof form>(key: K, value: string) {
        setForm((f) => ({ ...f, [key]: value }))
    }

    function submit() {
        if (!form.title.trim() || !form.summary.trim() || !form.startsAt) return
        createEvent.mutate(
            {
                title: form.title,
                summary: form.summary,
                body: form.body || undefined,
                startsAt: new Date(form.startsAt).getTime(),
                location: form.location || undefined,
                burrowUrl: form.burrowUrl || undefined,
                imageUrl: form.imageUrl || undefined,
            },
            { onSuccess: () => setForm(emptyEvent) },
        )
    }

    const valid = form.title.trim() && form.summary.trim() && form.startsAt

    return (
        <>
            <SectionHead title="Publish an event">
                Events show up on the public Events page and its calendar.
            </SectionHead>

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
                        Details <span className="text-text-soft">(shown under “Read more”)</span>
                        <textarea
                            value={form.body}
                            onChange={(e) => set('body', e.target.value)}
                            rows={4}
                            placeholder="The full write-up…"
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
                    <FormError error={createEvent.error?.message} />
                    <div>
                        <Button disabled={!valid || createEvent.isPending} onClick={submit}>
                            Publish event
                        </Button>
                    </div>
                </div>
            </div>

            {events.length > 0 && (
                <>
                    <SectionHead title="Published events" className="mt-14" />
                    <div className="border-t border-line">
                        {events.map((event: Event) => (
                            <article
                                key={event.id}
                                className={`${row} flex flex-wrap items-center justify-between gap-4`}
                            >
                                <div>
                                    <h3 className="m-0 text-base">{event.title}</h3>
                                    <p className="mb-0 mt-[0.1rem] font-mono text-[0.8rem] text-text-soft">
                                        {new Date(event.startsAt).toLocaleString()}
                                        {event.location && <> · {event.location}</>}
                                    </p>
                                </div>
                                <Button
                                    variant="danger"
                                    disabled={deleteEvent.isPending}
                                    onClick={() => deleteEvent.mutate(event.id)}
                                >
                                    Delete
                                </Button>
                            </article>
                        ))}
                    </div>
                </>
            )}
        </>
    )
}
