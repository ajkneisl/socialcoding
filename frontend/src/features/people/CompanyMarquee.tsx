import { useState } from 'react'

// Known companies whose website isn't just `name.com`.
const DOMAIN_OVERRIDES: Record<string, string> = {
    oit: 'it.umn.edu',
    epic: 'epicsystems.com',
}

function companyDomain(name: string): string {
    const slug = name.toLowerCase().replace(/[^a-z0-9]/g, '')
    return DOMAIN_OVERRIDES[slug] ?? `${slug}.com`
}

function CompanyLogo({ name }: { name: string }) {
    const [imageFailed, setImageFailed] = useState(false)

    return (
        <span
            className="inline-flex items-center gap-3 whitespace-nowrap opacity-75 transition-opacity hover:opacity-100"
            title={name}
        >
            {!imageFailed && (
                <img
                    className="h-9 w-9 rounded-lg bg-white object-contain p-[3px]"
                    src={`https://www.google.com/s2/favicons?domain=${companyDomain(name)}&sz=64`}
                    alt=""
                    loading="lazy"
                    onError={() => setImageFailed(true)}
                />
            )}
            <span className="font-mono text-[0.95rem] font-semibold tracking-[0.02em] text-text-soft">
                {name}
            </span>
        </span>
    )
}

export default function CompanyMarquee({ companies }: { companies: string[] }) {
    if (companies.length === 0) return null

    const track = (hidden: boolean) => (
        <div
            className="flex animate-marquee items-center gap-16 pr-16 group-hover:[animation-play-state:paused] motion-reduce:animate-none"
            aria-hidden={hidden || undefined}
        >
            {companies.map((name) => (
                <CompanyLogo key={name} name={name} />
            ))}
        </div>
    )

    return (
        <div className="group mt-8 overflow-hidden [mask-image:linear-gradient(90deg,transparent,#000_8%,#000_92%,transparent)]">
            <div className="flex w-max">
                {track(false)}
                {track(true)}
            </div>
        </div>
    )
}
