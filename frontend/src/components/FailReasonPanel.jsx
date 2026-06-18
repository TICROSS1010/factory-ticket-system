import { useState } from 'react';
import { submitAction } from '../api.js';

const MAX = 500;

export default function FailReasonPanel({ orderId, onClose, onFailed }) {
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!reason.trim()) return;
    setSubmitting(true);
    try {
      await submitAction(orderId, 'FAILED', reason.trim());
      onFailed();
    } catch (err) {
      alert(err.message === 'comment-too-long'
        ? 'Reason exceeds the 500 character limit.'
        : 'Failed to submit rejection.');
      setSubmitting(false);
    }
  }

  const charClass = reason.length >= MAX
    ? 'comment-char-count at-limit'
    : reason.length >= MAX * 0.9
    ? 'comment-char-count near-limit'
    : 'comment-char-count';

  return (
    <form onSubmit={handleSubmit}>
      <div className="fail-label">REJECTION REASON REQUIRED</div>
      <textarea
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        placeholder={`Describe why this order is being rejected... (max ${MAX} characters)`}
        maxLength={MAX}
        required
        autoFocus
      />
      <div className={charClass}>{reason.length} / {MAX}</div>
      <div className="comment-panel-actions">
        <button
          className="btn-post-comment btn-fail-confirm"
          type="submit"
          disabled={submitting || !reason.trim()}
        >
          Confirm Fail
        </button>
        <button className="btn-cancel-comment" type="button" onClick={onClose}>Cancel</button>
      </div>
    </form>
  );
}
