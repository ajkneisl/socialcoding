/** A spinning loading indicator. Pass height/width via [className] (e.g. "h-8 w-8"). */
export function Spinner({ className }: { className?: string }) {
    return (
        <span
            role="status"
            aria-label="Loading"
            className={`inline-block animate-spin rounded-full border-2 border-line border-t-gold ${
                className ?? ''
            }`}
        />
    )
}
