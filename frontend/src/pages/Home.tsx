import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type Person, type Project } from '../api'
import CompanyMarquee from '../components/CompanyMarquee'
import { PersonCard, ProjectCard } from '../components/cards'

const BOARD_ORDER = [
  'President',
  'Vice President',
  'Treasurer',
  'Vice Treasurer',
  'Events',
  'Communications',
  'Relations',
  'Recruitment',
]

export default function Home() {
  const [projects, setProjects] = useState<Project[]>([])
  const [people, setPeople] = useState<Person[]>([])

  useEffect(() => {
    api.projects().then(setProjects).catch(() => setProjects([]))
    api.people().then(setPeople).catch(() => setPeople([]))
  }, [])

  const activeProjects = useMemo(() => projects.filter((p) => p.active), [projects])

  const companies = useMemo(() => {
    const seen = new Set<string>()
    for (const p of people) {
      const c = p.company?.trim()
      if (c) seen.add(c)
    }
    return [...seen].sort()
  }, [people])

  const board = useMemo(
    () =>
      people
        .filter((p) => p.role === 'BOARD')
        .sort((a, b) => {
          const rank = (p: Person) => {
            const i = BOARD_ORDER.indexOf(p.title ?? '')
            return i === -1 ? BOARD_ORDER.length : i
          }
          return rank(a) - rank(b) || a.name.localeCompare(b.name)
        }),
    [people],
  )

  return (
    <>
      <section className="hero">
        <div className="container hero-inner">
          <img src="/logo.svg" alt="" className="hero-logo" />
          <div>
            <h1>Social Coding</h1>
            <p className="hero-tagline mono">at the University of Minnesota — Twin Cities</p>
            <p className="hero-sub">
              A student-run community for building real software with real teammates. Whatever
              your major or experience level, if you want to ship something, you belong here.
            </p>
            <div className="hero-actions">
              <a
                className="btn btn-primary btn-lg"
                href="https://discord.gg/social-coding"
                target="_blank"
                rel="noreferrer"
              >
                Get Involved
              </a>
              <a
                className="btn btn-ghost btn-lg"
                href="https://github.com/social-coding-umn"
                target="_blank"
                rel="noreferrer"
              >
                <svg
                  viewBox="0 0 16 16"
                  width="18"
                  height="18"
                  fill="currentColor"
                  aria-hidden="true"
                >
                  <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.42 7.42 0 0 1 2-.27c.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z" />
                </svg>
                GitHub
              </a>
            </div>
          </div>
        </div>
      </section>

      <section className="stats-band">
        <div className="container stats">
          <div className="stat">
            <strong>#1</strong>
            <span>largest programming club at the University of Minnesota</span>
          </div>
          <div className="stat">
            <strong>100+</strong>
            <span>projects created since 2016</span>
          </div>
          <div className="stat">
            <strong>1,000+</strong>
            <span>members and counting</span>
          </div>
        </div>
      </section>

      <section className="section container">
        <p className="eyebrow mono">About us</p>
        <h2>Coursework teaches syntax. We practice software.</h2>
        <div className="prose">
          <p>
            Social Coding was founded in 2016 to give students of every background and discipline
            a place to learn from one another and write great code together. Classes rarely cover
            the collaborative side of engineering — code review, version control, planning with a
            team — so that's exactly what we practice, on projects that real Gophers use around
            campus.
          </p>
          <details className="more">
            <summary className="mono">more about the club</summary>
            <p>
              There's no application and no experience requirement — show up, pick a project, and
              start building. Members work in small teams led by a project lead, with the board
              reviewing new project pitches each semester. Beyond projects we run workshops,
              resume reviews, and socials, and alumni regularly come back to talk about life in
              industry. If you've never written a line of code, someone here will happily sit
              down and get you through your first commit.
            </p>
          </details>
        </div>
      </section>

      <section className="section container">
        <p className="eyebrow mono">How a semester works</p>
        <h2>The Social Coding timeline</h2>
        <ol className="timeline">
          <li>
            <h3>Planning phase</h3>
            <p>Pitch ideas, form teams, and scope what's shippable this semester.</p>
          </li>
          <li>
            <h3>Build time</h3>
            <p>Weekly work sessions — design, code, review, repeat.</p>
          </li>
          <li>
            <h3>MVP presentation</h3>
            <p>Demo a working core to the club and gather feedback.</p>
          </li>
          <li>
            <h3>Build time</h3>
            <p>Iterate on what you learned and polish the rough edges.</p>
          </li>
          <li>
            <h3>Final presentation</h3>
            <p>Show off the finished product at the end of the semester.</p>
          </li>
        </ol>
      </section>

      {companies.length > 0 && (
        <section className="section marquee-section">
          <div className="container">
            <p className="eyebrow mono">Alumni &amp; members</p>
            <h2>Where our people end up</h2>
          </div>
          <CompanyMarquee companies={companies} />
        </section>
      )}

      {board.length > 0 && (
        <section className="section container">
          <p className="eyebrow mono">Board</p>
          <h2>Meet the board</h2>
          <div className="grid grid-people board-grid">
            {board.map((p) => (
              <PersonCard key={p.id} person={p} />
            ))}
          </div>
        </section>
      )}

      <section className="section container">
        <div className="section-head row">
          <div>
            <p className="eyebrow mono">Projects</p>
            <h2>Currently active</h2>
          </div>
          <Link to="/account" className="btn btn-ghost">
            Pitch your own →
          </Link>
        </div>
        <div className="grid grid-projects">
          {activeProjects.slice(0, 6).map((p) => (
            <ProjectCard key={p.id} project={p} />
          ))}
        </div>
      </section>

      <section className="section container">
        <div className="cta">
          <div>
            <h2>Have an idea worth building?</h2>
            <p>
              Join us at our meetings, pitch your idea, get a group, and start building.
            </p>
          </div>
          <Link to="/account" className="btn btn-primary btn-lg">
            Create a project
          </Link>
        </div>
      </section>
    </>
  )
}
