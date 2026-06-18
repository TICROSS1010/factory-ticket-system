import { useState } from 'react';
import { submitAction } from '../api.js';
import CommentPanel from './CommentPanel.jsx';
import FailReasonPanel from './FailReasonPanel.jsx';

const CONFIRM_MSGS = {
  CONFIRMED: 'Confirm this order and send to production?',
  REJECTED:  'Reject this order? It will be cancelled.',
  COMPLETED: 'Mark this order complete and send to Quality Check?',
  RESET:     'Do you want to reset all work done on this order?',
  PASSED:    'Pass this order and send to Packing?',
  PACKED:    'Confirm order is packed and send to Shipping?',
  SHIPPED:   'Confirm this order has shipped and mark as Delivered?',
};

function hasUnseenComment(ticket, currentUser) {
  if (!ticket.lastCommentAt || !ticket.lastCommentBy) return false;
  if (ticket.lastCommentBy === currentUser) return false;
  const seen = localStorage.getItem(`seen-${currentUser}-${ticket.orderId}`);
  return seen !== ticket.lastCommentAt;
}

export default function TicketRow({ ticket, role, currentUser, onActionComplete }) {
  const [showComment,    setShowComment]    = useState(false);
  const [showFailReason, setShowFailReason] = useState(false);
  const [busy, setBusy] = useState(false);
  const [unseen, setUnseen] = useState(() => hasUnseenComment(ticket, currentUser));

  async function doAction(action, reason) {
    const msg = CONFIRM_MSGS[action];
    if (msg && !window.confirm(msg)) return;
    setBusy(true);
    try {
      await submitAction(ticket.orderId, action, reason);
      onActionComplete();
    } catch (err) {
      alert(err.message === 'fail-reason-required'
        ? 'A rejection reason is required.'
        : err.message === 'comment-too-long'
        ? 'Text exceeds the 500 character limit.'
        : 'Action failed. Please try again.');
      setBusy(false);
    }
  }

  function openComment() {
    setShowComment(true);
    if (ticket.lastCommentAt) {
      localStorage.setItem(`seen-${currentUser}-${ticket.orderId}`, ticket.lastCommentAt);
      setUnseen(false);
    }
  }

  function closeComment() { setShowComment(false); }
  function openFail()    { setShowFailReason(true);  }
  function closeFail()   { setShowFailReason(false); }

  const isOnHold = ticket.workStatus === 'ON_HOLD';

  return (
    <>
      <tr className={isOnHold ? 'row-on-hold' : ''}>
        <td>
          <div className="order-id">{ticket.orderId}</div>
          {ticket.returnCount > 0 && <span className="qc-return">↩ QC</span>}
          {role === 'LINE_WORKER' && ticket.returnReason && (
            <div className="qc-reason">{ticket.returnReason}</div>
          )}
        </td>
        <td className="text-main">{ticket.customer}</td>
        <td>
          <span className={`badge badge-${ticket.priority}`}>{ticket.priority}</span>
        </td>
        <td className="text-sub">{ticket.dueDate}</td>
        <td className="text-main">{ticket.orderType}</td>
        <td className="text-main">{ticket.quantity}</td>
        <td>
          <div className="actions">
            {role === 'SALES' && (
              <>
                <button className="btn btn-confirm" disabled={busy} onClick={() => doAction('CONFIRMED')}>Confirm</button>
                <button className="btn btn-reject"  disabled={busy} onClick={() => doAction('REJECTED')}>Reject</button>
              </>
            )}

            {role === 'LINE_WORKER' && (
              <>
                {!ticket.workStatus && (
                  <button className="btn btn-start" disabled={busy} onClick={() => doAction('STARTED')}>Start</button>
                )}
                {ticket.workStatus === 'IN_PROGRESS' && (
                  <>
                    <button className="btn btn-hold"     disabled={busy} onClick={() => doAction('HOLD')}>Hold Work</button>
                    <button className="btn btn-complete" disabled={busy} onClick={() => doAction('COMPLETED')}>Complete</button>
                    <button className="btn btn-reset"    disabled={busy} onClick={() => doAction('RESET')}>Reset</button>
                  </>
                )}
                {ticket.workStatus === 'ON_HOLD' && (
                  <>
                    <button className="btn btn-resume" disabled={busy} onClick={() => doAction('RESUMED')}>Resume</button>
                    <button className="btn btn-reset"  disabled={busy} onClick={() => doAction('RESET')}>Reset</button>
                  </>
                )}
              </>
            )}

            {role === 'QUALITY' && (
              <>
                <button className="btn btn-pass" disabled={busy} onClick={() => doAction('PASSED')}>Pass</button>
                <button className="btn btn-fail" disabled={busy} onClick={openFail}>Fail</button>
              </>
            )}

            {role === 'PACKER' && (
              <button className="btn btn-packed" disabled={busy} onClick={() => doAction('PACKED')}>Confirm Packed</button>
            )}

            {role === 'SHIPPING' && (
              <button className="btn btn-shipped" disabled={busy} onClick={() => doAction('SHIPPED')}>Confirm Shipped</button>
            )}

            <button
              className="btn-comment"
              onClick={showComment ? closeComment : openComment}
              title="Comments"
            >
              💬
              {unseen && <span className="comment-badge">!</span>}
            </button>
          </div>
        </td>
      </tr>

      {showComment && (
        <tr className="comment-panel">
          <td colSpan="7">
            <CommentPanel
              orderId={ticket.orderId}
              onClose={closeComment}
              onPosted={onActionComplete}
            />
          </td>
        </tr>
      )}

      {showFailReason && role === 'QUALITY' && (
        <tr className="comment-panel">
          <td colSpan="7">
            <FailReasonPanel
              orderId={ticket.orderId}
              onClose={closeFail}
              onFailed={onActionComplete}
            />
          </td>
        </tr>
      )}
    </>
  );
}
