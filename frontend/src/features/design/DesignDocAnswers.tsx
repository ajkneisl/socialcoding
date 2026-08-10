import { answersOf, sectionsFor } from './sections'
import type { DesignDocEntry } from './types'

/** A filed doc's answers, read-only — the same layout whichever spec the doc follows. */
export function DesignDocAnswers({ entry }: { entry: DesignDocEntry }) {
    const answers = answersOf(entry)

    return (
        <>
            {sectionsFor(entry).map((section) => (
                <div key={section.id} className="mb-7 last:mb-0">
                    <h4 className="mb-3 text-[0.85rem] uppercase tracking-[0.1em] text-gold">
                        {section.title}
                    </h4>
                    {section.questions.map((q) => (
                        <div key={String(q.field)} className="mb-[0.9rem]">
                            <p className="mb-[0.15rem] font-semibold">{q.label}</p>
                            <p className="m-0 max-w-[75ch] whitespace-pre-wrap text-text-soft">
                                {answers[q.field] || 'Not answered yet.'}
                            </p>
                        </div>
                    ))}
                </div>
            ))}
        </>
    )
}
