import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth-context'

function Logo() {
  return (
    <Link to="/" className="logo">
      <img src="/logo.svg" alt="Social Coding" className="logo-img" />
      <span className="logo-text">
        Social Coding
      </span>
    </Link>
  )
}

export default function Layout() {
  const { user } = useAuth()

  return (
    <div className="site">
      <header className="nav-wrap">
        <nav className="nav container">
          <Logo />
          <div className="nav-links">
            <NavLink to="/projects">Projects</NavLink>
            <NavLink to="/people">People</NavLink>
            {user?.role === 'BOARD' && <NavLink to="/board">Board</NavLink>}
          </div>
          <div className="nav-actions">
            <a
              className="btn btn-ghost"
              href="https://discord.gg/social-coding"
              target="_blank"
              rel="noreferrer"
            >
              Discord
            </a>
            <NavLink to="/account" className="btn btn-primary">
              {user ? user.name.split(' ')[0] : 'Sign in'}
            </NavLink>
          </div>
        </nav>
      </header>

      <main>
        <Outlet />
      </main>

      <footer className="footer">
        <div className="container footer-grid">
          <div>
            <Logo />
            <p className="footer-blurb">
              A student-run software development community at the University of Minnesota —
              Twin Cities. Everyone is welcome, no experience required.
            </p>
            <p className="footer-blurb mono">weekly meetings · Bruininks Hall 315</p>
          </div>
          <div>
            <h4>Site</h4>
            <Link to="/projects">Projects</Link>
            <Link to="/people">People</Link>
            <Link to="/account">Account</Link>
          </div>
          <div>
            <h4>Connect</h4>
            <a href="mailto:coding@umn.edu">coding@umn.edu</a>
            <a href="https://github.com/social-coding-umn" target="_blank" rel="noreferrer">
              GitHub
            </a>
            <a href="https://www.linkedin.com/company/social-coding" target="_blank" rel="noreferrer">
              LinkedIn
            </a>
          </div>
        </div>
        <div className="container footer-legal">
          <p>
            Social Coding @ UMN is a registered student organization and is not directly
            affiliated with the University of Minnesota.
          </p>
        </div>
      </footer>
    </div>
  )
}
