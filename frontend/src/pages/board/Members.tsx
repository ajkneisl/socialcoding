import { useState } from 'react'
import { useAuth } from '../../auth-context'
import {
    useBoardMembers,
    useUpdateMemberRole,
    useUpdateMemberTitle,
} from '../../features/board/queries'
import type { User } from '../../features/auth/types'
import { Avatar } from '../../components/Avatar'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { FormError } from '../../components/FormError'
import { SectionHead } from '../../components/SectionHead'

const row = 'flex flex-wrap items-center gap-4 border-b border-line px-1 py-[1.1rem]'

function RoleNameEditor({ member }: { member: User }) {
    const updateTitle = useUpdateMemberTitle()
    const [title, setTitle] = useState(member.title ?? '')

    const dirty = title.trim() !== (member.title ?? '')

    function save() {
        updateTitle.mutate({ id: member.id, title: title.trim() || null })
    }

    return (
        <div className="mt-2 flex items-center gap-2">
            <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Board"
                maxLength={64}
                aria-label={`Role name for ${member.name}`}
                className="max-w-[16rem] text-[0.85rem]"
            />
            {dirty && (
                <Button variant="ghost" disabled={updateTitle.isPending} onClick={save}>
                    {updateTitle.isPending ? 'Saving…' : 'Save'}
                </Button>
            )}
        </div>
    )
}

function MemberRow({ member, isSelf }: { member: User; isSelf: boolean }) {
    const updateRole = useUpdateMemberRole()
    const isBoard = member.role === 'BOARD'

    function toggle() {
        updateRole.mutate({ id: member.id, role: isBoard ? 'MEMBER' : 'BOARD' })
    }

    return (
        <div className={row}>
            <Avatar name={member.name} avatarUrl={member.avatarUrl} size="sm" />
            <div className="min-w-0 flex-1">
                <p className="m-0 flex items-center gap-2 font-medium">
                    {member.name}
                    {isBoard && <Badge variant="board">{member.title ?? 'board'}</Badge>}
                </p>
                <p className="m-0 font-mono text-[0.8rem] text-text-soft">{member.email}</p>
                {isBoard && <RoleNameEditor key={member.title ?? ''} member={member} />}
            </div>
            {isSelf ? (
                <span className="font-mono text-[0.8rem] text-text-faint">you</span>
            ) : (
                <Button
                    variant={isBoard ? 'danger' : 'ghost'}
                    disabled={updateRole.isPending}
                    onClick={toggle}
                >
                    {updateRole.isPending
                        ? 'Saving…'
                        : isBoard
                          ? 'Remove from board'
                          : 'Make board'}
                </Button>
            )}
        </div>
    )
}

export default function BoardMembers() {
    const { user } = useAuth()
    const { data: members = [], isLoading, error } = useBoardMembers()

    if (isLoading) {
        return <p className="text-text-soft">Loading…</p>
    }

    if (error) {
        return <FormError error={error.message} />
    }

    return (
        <div className="max-w-[680px]">
            <SectionHead title="Board members">
                Promote members to the board or step them back down, and rename the role each board
                member holds. You can't change your own role.
            </SectionHead>
            <div className="border-t border-line">
                {members.map((member) => (
                    <MemberRow key={member.id} member={member} isSelf={member.id === user?.id} />
                ))}
            </div>
        </div>
    )
}
