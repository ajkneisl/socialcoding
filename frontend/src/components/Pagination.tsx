const navButton =
    'cursor-pointer rounded-md border border-line px-3 py-1 font-mono text-[0.8rem] text-text-soft hover:border-gold hover:text-gold disabled:cursor-default disabled:opacity-40 disabled:hover:border-line disabled:hover:text-text-soft'

/** Prev / page indicator / Next controls. Renders nothing for a single page. */
export function Pagination({
    page,
    pageCount,
    onChange,
    className,
}: {
    page: number
    pageCount: number
    onChange: (page: number) => void
    className?: string
}) {
    if (pageCount <= 1) return null
    return (
        <div
            className={['flex items-center justify-center gap-4 pt-6', className]
                .filter(Boolean)
                .join(' ')}
        >
            <button
                type="button"
                className={navButton}
                disabled={page === 0}
                onClick={() => onChange(page - 1)}
            >
                ← Prev
            </button>
            <span className="font-mono text-[0.8rem] text-text-faint">
                Page {page + 1} of {pageCount}
            </span>
            <button
                type="button"
                className={navButton}
                disabled={page >= pageCount - 1}
                onClick={() => onChange(page + 1)}
            >
                Next →
            </button>
        </div>
    )
}
