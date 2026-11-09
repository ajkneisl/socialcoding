import { useEffect, useId, useRef, type ReactNode } from 'react'

/**
 * A centered modal on the native `<dialog>`, which provides the focus trap and Escape handling.
 * Mount it to open it; `onClose` should unmount it.
 */
export function Modal({
    title,
    onClose,
    children,
}: {
    title: ReactNode
    onClose: () => void
    children: ReactNode
}) {
    const ref = useRef<HTMLDialogElement>(null)
    const titleId = useId()

    useEffect(() => {
        const dialog = ref.current
        if (dialog && !dialog.open) dialog.showModal()
    }, [])

    return (
        <dialog
            ref={ref}
            aria-labelledby={titleId}
            onClose={onClose}
            // Mouse down rather than click, so dragging a selection out of an input can't dismiss.
            onMouseDown={(e) => e.target === e.currentTarget && onClose()}
            className="m-auto w-[min(34rem,calc(100%_-_2rem))] rounded-xl border border-line bg-panel p-0 text-text backdrop:bg-black/60"
        >
            <div className="p-6">
                <h3 id={titleId} className="mb-4">
                    {title}
                </h3>
                {children}
            </div>
        </dialog>
    )
}
