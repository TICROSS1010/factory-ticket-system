export default function Navbar({ username, role, onRefresh }) {
  return (
    <div className="navbar">
      <span className="nav-title">FACTORY TICKET SYSTEM</span>
      <div className="nav-right">
        <button className="nav-refresh" onClick={onRefresh}>↻ REFRESH</button>
        <span className="nav-user">{username}</span>
        <span className="nav-role">{role}</span>
        <form method="post" action="/logout" style={{ display: 'inline' }}>
          <button type="submit" className="nav-logout">sign out</button>
        </form>
      </div>
    </div>
  );
}
