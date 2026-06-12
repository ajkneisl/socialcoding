import { Badge, type BadgeVariant } from '../../components/Badge'
import type { ProjectStatus } from './types'

const statusBadges: Record<ProjectStatus, { label: string; variant: BadgeVariant }> = {
    PENDING: { label: 'Awaiting board review', variant: 'pending' },
    APPROVED: { label: 'Approved', variant: 'approved' },
    REJECTED: { label: 'Not approved', variant: 'rejected' },
}

export function StatusBadge({ status }: { status: ProjectStatus }) {
    const { label, variant } = statusBadges[status]
    return <Badge variant={variant}>{label}</Badge>
}

export function ActiveBadge({ active }: { active: boolean }) {
    return (
        <Badge variant={active ? 'approved' : 'inactive'}>{active ? 'Active' : 'Inactive'}</Badge>
    )
}

export function ReviewNote({ note, className }: { note: string; className?: string }) {
    return (
        <p
            className={['rounded-lg bg-red-400/12 px-3 py-2 text-[0.9rem]', className]
                .filter(Boolean)
                .join(' ')}
        >
            Board feedback: {note}
        </p>
    )
}
