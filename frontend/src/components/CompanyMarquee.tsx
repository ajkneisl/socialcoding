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
    <span className="marquee-item" title={name}>
      {!imageFailed && (
        <img
          src={`https://www.google.com/s2/favicons?domain=${companyDomain(name)}&sz=64`}
          alt=""
          loading="lazy"
          onError={() => setImageFailed(true)}
        />
      )}
      <span className="marquee-name">{name}</span>
    </span>
  )
}

export default function CompanyMarquee({ companies }: { companies: string[] }) {
  if (companies.length === 0) return null

  const track = (hidden: boolean) => (
    <div className="marquee-track" aria-hidden={hidden || undefined}>
      {companies.map((name) => (
        <CompanyLogo key={name} name={name} />
      ))}
    </div>
  )

  return (
    <div className="marquee">
      {track(false)}
      {track(true)}
    </div>
  )
}
