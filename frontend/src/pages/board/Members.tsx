import { useState, type FormEvent } from 'react'
import { useAuth } from '../../auth-context'
import {
    useBoardMembers,
    useUpdateMemberProfile,
    useUpdateMemberRole,
    useUpdateMemberTitle,
} from '../../features/board/queries'
import type { User } from '../../features/auth/types'
import { Avatar } from '../../components/Avatar'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { CogIcon } from '../../components/CogIcon'
import { FormError } from '../../components/FormError'
import { Modal } from '../../components/Modal'
import { SectionHead } from '../../components/SectionHead'
import { Switch } from '../../components/Switch'

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

const PROFILE_FIELDS = [
    { key: 'company', label: 'Employer', maxLength: 255 },
    { key: 'github', label: 'GitHub', maxLength: 255 },
    { key: 'linkedin', label: 'LinkedIn', maxLength: 255 },
    { key: 'website', label: 'Portfolio', maxLength: 512 },
] as const

function MemberSettingsModal({
    member,
    isSelf,
    onClose,
}: {
    member: User
    isSelf: boolean
    onClose: () => void
}) {
    const { refreshUser } = useAuth()
    const updateProfile = useUpdateMemberProfile()
    const [gradYear, setGradYear] = useState(member.gradYear ? String(member.gradYear) : '')
    const [values, setValues] = useState({
        company: member.company ?? '',
        github: member.github ?? '',
        linkedin: member.linkedin ?? '',
        website: member.website ?? '',
    })
    const [listed, setListed] = useState(member.listed)

    function submit(e: FormEvent) {
        e.preventDefault()
        updateProfile.mutate(
            {
                id: member.id,
                profile: {
                    gradYear: gradYear ? Number(gradYear) : null,
                    company: values.company.trim() || null,
                    github: values.github.trim() || null,
                    linkedin: values.linkedin.trim() || null,
                    website: values.website.trim() || null,
                    listed,
                },
            },
            {
                onSuccess: () => {
                    if (isSelf) refreshUser()
                    onClose()
                },
            },
        )
    }

    return (
        <Modal title={`${member.name}'s profile`} onClose={onClose}>
            <form className="flex flex-col gap-[1.1rem]" onSubmit={submit}>
                <div className="grid grid-cols-2 gap-[0.9rem] max-md:grid-cols-1">
                    <label>
                        Class of
                        <input
                            type="number"
                            value={gradYear}
                            onChange={(e) => setGradYear(e.target.value)}
                            min={2000}
                            max={2100}
                        />
                    </label>
                    {PROFILE_FIELDS.map((f) => (
                        <label key={f.key}>
                            {f.label}
                            <input
                                value={values[f.key]}
                                onChange={(e) =>
                                    setValues((v) => ({ ...v, [f.key]: e.target.value }))
                                }
                                maxLength={f.maxLength}
                            />
                        </label>
                    ))}
                </div>
                <Switch
                    checked={listed}
                    onChange={setListed}
                    label="Show on the People page"
                    description="When on, this profile appears on the public People page."
                />
                <FormError error={updateProfile.error?.message} />
                <div className="flex justify-end gap-[0.6rem]">
                    <Button variant="ghost" onClick={onClose}>
                        Cancel
                    </Button>
                    <Button type="submit" disabled={updateProfile.isPending}>
                        {updateProfile.isPending ? 'Saving…' : 'Save'}
                    </Button>
                </div>
            </form>
        </Modal>
    )
}

function MemberRow({ member, isSelf }: { member: User; isSelf: boolean }) {
    const updateRole = useUpdateMemberRole()
    const [editing, setEditing] = useState(false)
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
            <div className="flex items-center gap-2">
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
                <Button
                    variant="ghost"
                    aria-label={`Edit ${member.name}'s profile`}
                    className="px-[0.7rem]!"
                    onClick={() => setEditing(true)}
                >
                    <CogIcon />
                </Button>
            </div>
            {editing && (
                <MemberSettingsModal
                    member={member}
                    isSelf={isSelf}
                    onClose={() => setEditing(false)}
                />
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
