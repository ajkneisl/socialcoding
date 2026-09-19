import type { DesignSection } from './sections'

/** The questions in one section, as editable fields. Works against any all-text doc spec. */
export function DesignDocQuestions<T extends Record<keyof T, string>>({
    doc,
    onChange,
    section,
}: {
    doc: T
    onChange: (doc: T) => void
    section: DesignSection<T>
}) {
    return (
        <>
            {section.questions.map((q) => (
                <label key={String(q.field)}>
                    {q.label}
                    <textarea
                        rows={3}
                        value={doc[q.field] ?? ''}
                        placeholder={q.placeholder}
                        onChange={(e) => onChange({ ...doc, [q.field]: e.target.value } as T)}
                    />
                </label>
            ))}
        </>
    )
}
