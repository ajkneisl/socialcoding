import type { ReactNode } from 'react'

/** Right-aligned action row at the bottom of a form (Back/Next, Cancel/Save). */
export function FormActions({ children }: { children: ReactNode }) {
    return <div className="mt-2 flex justify-end gap-[0.6rem]">{children}</div>
}
