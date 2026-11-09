import { useState, type FormEvent } from 'react'
import {
    useAddEmployer,
    useBoardEmployers,
    useRemoveEmployer,
    useSetEmployerVisible,
} from '../../features/board/queries'
import type { Employer } from '../../features/board/types'
import { Button } from '../../components/Button'
import { FormError } from '../../components/FormError'
import { SectionHead } from '../../components/SectionHead'
import { Switch } from '../../components/Switch'

function sourceLabel(memberCount: number) {
    if (memberCount === 0) return 'Not on any member profile'
    return `On ${memberCount} member ${memberCount === 1 ? 'profile' : 'profiles'}`
}

function EmployerRow({ employer }: { employer: Employer }) {
    const setVisible = useSetEmployerVisible()
    const remove = useRemoveEmployer()

    return (
        <div className="border-b border-line px-1 py-[0.9rem]">
            <div className="flex items-center justify-between gap-4">
                <Switch
                    checked={employer.visible}
                    disabled={setVisible.isPending}
                    onChange={(visible) => setVisible.mutate({ name: employer.name, visible })}
                    label={employer.name}
                    description={sourceLabel(employer.memberCount)}
                />
                {/* A profile employer would just reappear, so it can only be hidden. */}
                {employer.memberCount === 0 && (
                    <Button
                        variant="ghost"
                        disabled={remove.isPending}
                        onClick={() => remove.mutate(employer.key)}
                    >
                        {remove.isPending ? 'Removing…' : 'Remove'}
                    </Button>
                )}
            </div>
            <FormError
                error={setVisible.error?.message ?? remove.error?.message}
                className="mt-2"
            />
        </div>
    )
}

function AddEmployerForm() {
    const add = useAddEmployer()
    const [name, setName] = useState('')

    function submit(e: FormEvent) {
        e.preventDefault()
        add.mutate(name, { onSuccess: () => setName('') })
    }

    return (
        <form onSubmit={submit} className="mb-6">
            <div className="flex items-center gap-2">
                <input
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Add an employer"
                    maxLength={255}
                    aria-label="Employer name"
                    className="min-w-0 flex-1"
                />
                <Button type="submit" disabled={add.isPending || !name.trim()}>
                    {add.isPending ? 'Adding…' : 'Add'}
                </Button>
            </div>
            <FormError error={add.error?.message} className="mt-2" />
        </form>
    )
}

export default function BoardEmployers() {
    const { data: employers = [], isLoading, error } = useBoardEmployers()

    if (isLoading) {
        return <p className="text-text-soft">Loading…</p>
    }

    if (error) {
        return <FormError error={error.message} />
    }

    const shown = employers.filter((e) => e.visible).length

    return (
        <div className="max-w-[680px]">
            <SectionHead title="Employers">
                Choose which employers appear on the home page. New ones, whether added here or from
                a member's profile, stay hidden until you turn them on.
            </SectionHead>
            <AddEmployerForm />
            {employers.length === 0 ? (
                <p className="text-text-soft">No employers yet.</p>
            ) : (
                <>
                    <p className="mb-2 font-mono text-[0.8rem] text-text-soft">
                        {shown} of {employers.length} shown
                    </p>
                    <div className="border-t border-line">
                        {employers.map((employer) => (
                            <EmployerRow key={employer.key} employer={employer} />
                        ))}
                    </div>
                </>
            )}
        </div>
    )
}
