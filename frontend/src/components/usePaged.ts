import { useMemo, useState } from 'react'

/** Slices a list into pages and tracks the current page, clamping when the list shrinks. */
export function usePaged<T>(items: T[], pageSize: number) {
    const [page, setPage] = useState(0)
    const pageCount = Math.max(1, Math.ceil(items.length / pageSize))
    // Derive a clamped page during render so a shrinking list (filter/delete) can't strand us.
    const safePage = Math.min(page, pageCount - 1)

    const pageItems = useMemo(
        () => items.slice(safePage * pageSize, safePage * pageSize + pageSize),
        [items, safePage, pageSize],
    )

    return { page: safePage, setPage, pageCount, pageItems }
}
