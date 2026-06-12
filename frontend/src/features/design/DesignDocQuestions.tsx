import type {DesignDoc} from './types'
import type {DesignSection} from './sections'

export function DesignDocQuestions({
                                       doc,
                                       onChange,
                                       section,
                                   }: {
    doc: DesignDoc
    onChange: (doc: DesignDoc) => void
    section: DesignSection
}) {
    return (
        <>
            {section.questions.map((q) => (
                <label key={q.field}>
                    {q.label}
                    <textarea
                        rows={3}
                        value={doc[q.field] ?? ''}
                        placeholder={q.placeholder}
                        onChange={(e) => onChange({...doc, [q.field]: e.target.value})}
                    />
                </label>
            ))}
        </>
    )
}
