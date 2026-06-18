import { useSearchParams } from 'react-router-dom';

export default function LoginPage() {
  const [params] = useSearchParams();
  const hasError = params.has('error');

  return (
    <div className="login-body">
      <div className="login-container">
        <div className="login-header">
          <div className="login-label">Internal Operations</div>
          <div className="login-title">Factory Ticket System</div>
          <div className="login-subtitle">DEV MODE // v1.0</div>
        </div>

        <form method="post" action="/login">
          <div className="login-field">
            <div className="login-field-label">Username</div>
            <input type="text" name="username" autoFocus />
          </div>

          <div className="login-field">
            <div className="login-field-label">Password</div>
            <input type="password" name="password" />
          </div>

          {hasError && (
            <div className="login-error">Invalid username or password</div>
          )}

          <button className="login-btn" type="submit">Sign In</button>
        </form>
      </div>
    </div>
  );
}
