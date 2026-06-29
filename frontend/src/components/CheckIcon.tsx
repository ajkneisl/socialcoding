/** Checkmark glyph, e.g. shown on a button after a successful save. */
export function CheckIcon({ className }: { className?: string }) {
    return (
        <svg
            viewBox="0 0 24 24"
            width="16"
            height="16"
            fill="none"
            stroke="currentColor"
            strokeWidth="3"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
            className={className}
        >
            <path d="M20 6 9 17l-5-5" />
        </svg>
    )
}
