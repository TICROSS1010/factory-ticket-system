import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { getMe, getTickets } from '../api.js';
import Navbar from '../components/Navbar.jsx';
import TicketRow from '../components/TicketRow.jsx';

export default function TicketsPage() {
  const navigate = useNavigate();
  const [me, setMe] = useState(null);
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback((silent = false) => {
    if (silent) setLoading(true);
    setError(null);
    Promise.all([getMe(), getTickets()])
      .then(([meData, ticketData]) => {
        setMe(meData);
        setTickets(ticketData);
        setLoading(false);
      })
      .catch((err) => {
        if (err.status === 401) {
          navigate('/login');
        } else {
          setError('Failed to load tickets.');
          setLoading(false);
        }
      });
  }, [navigate]);

  useEffect(() => {
    load();
    const id = setInterval(() => load(true), 5000);
    return () => clearInterval(id);
  }, [load]);

  if (loading) {
    return <div className="loading-page">LOADING...</div>;
  }

  return (
    <div className="tickets-body">
      <Navbar username={me?.username} role={me?.role} onRefresh={load} />

      <div className="content">
        {error && <div className="error-banner">{error}</div>}

        <div className="queue-count">
          {tickets.length} ORDERS IN QUEUE // RUSH FIRST
        </div>

        <table>
          <thead>
            <tr>
              <th>Order ID</th>
              <th>Customer</th>
              <th>Priority</th>
              <th>Due Date</th>
              <th>Type</th>
              <th>Qty</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {tickets.length === 0 ? (
              <tr>
                <td colSpan="7">
                  <div className="empty">NO ORDERS IN QUEUE</div>
                </td>
              </tr>
            ) : (
              tickets.map((ticket) => (
                <TicketRow
                  key={ticket.orderId}
                  ticket={ticket}
                  role={me?.role}
                  currentUser={me?.username}
                  onActionComplete={load}
                />
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
