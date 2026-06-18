type SwitchProps = {
    checked: boolean
    onChange: (checked: boolean) => void
    label: string
    description?: string
    disabled?: boolean
}

/** A labeled on/off slider toggle. */
export function Switch({ checked, onChange, label, description, disabled }: SwitchProps) {
    return (
        <div
            onClick={() => !disabled && onChange(!checked)}
            className={`flex flex-row items-center gap-3 ${
                disabled ? 'opacity-50' : 'cursor-pointer'
            }`}
        >
            <button
                type="button"
                role="switch"
                aria-checked={checked}
                aria-label={label}
                disabled={disabled}
                className={`relative h-6 w-11 shrink-0 rounded-full border transition-colors ${
                    checked ? 'border-gold bg-gold' : 'border-line bg-bg-raised'
                }`}
            >
                <span
                    className={`absolute top-[2px] h-[18px] w-[18px] rounded-full transition-all ${
                        checked ? 'left-[22px] bg-ink' : 'left-[2px] bg-text-soft'
                    }`}
                />
            </button>
            <span className="leading-tight">
                <span className="font-semibold">{label}</span>
                {description && <span className="block text-text-soft">{description}</span>}
            </span>
        </div>
    )
}
