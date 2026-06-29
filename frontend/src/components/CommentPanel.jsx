import { useState, useEffect, useRef } from 'react';
import { getComments, submitComment } from '../api.js';

const MAX = 500;

export default function CommentPanel({ orderId, onClose, onPosted }) {
  const [messages, setMessages] = useState(null);
  const [text, setText] = useState('');
  const [posting, setPosting] = useState(false);
  const chatRef = useRef(null);

  useEffect(() => {
    getComments(orderId)
      .then(setMessages)
      .catch(() => setMessages([]));
  }, [orderId]);

  useEffect(() => {
    if (chatRef.current) chatRef.current.scrollTop = chatRef.current.scrollHeight;
  }, [messages]);

  async function handlePost(e) {
    e.preventDefault();
    if (!text.trim()) return;
    setPosting(true);
    try {
      await submitComment(orderId, text.trim());
      setText('');
      onPosted(true);
      const updated = await getComments(orderId);
      setMessages(updated);
    } catch {
      alert('Failed to post comment.');
    } finally {
      setPosting(false);
    }
  }

  const charClass = text.length >= MAX
    ? 'comment-char-count at-limit'
    : text.length >= MAX * 0.9
    ? 'comment-char-count near-limit'
    : 'comment-char-count';

  return (
    <>
      <div className="chat-area" ref={chatRef}>
        {messages === null ? (
          <div className="chat-loading">LOADING...</div>
        ) : messages.length === 0 ? (
          <div className="chat-empty">NO MESSAGES YET</div>
        ) : (
          messages.map((m, i) => {
            const isRejection = m.type === 'FAILED';
            return (
              <div key={i} className={`chat-msg${isRejection ? ' chat-msg-rejection' : ''}`}>
                <div className="chat-meta">
                  <span className="chat-worker">{m.worker}</span>
                  <span className="chat-time">{new Date(m.timestamp).toLocaleString()}</span>
                  {isRejection && <span className="chat-rejection-label">QC REJECTION</span>}
                </div>
                <div className="chat-text">{m.text}</div>
              </div>
            );
          })
        )}
      </div>

      <form onSubmit={handlePost}>
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={`Add a message... (max ${MAX} characters)`}
          maxLength={MAX}
          autoFocus
        />
        <div className={charClass}>{text.length} / {MAX}</div>
        <div className="comment-panel-actions">
          <button className="btn-post-comment" type="submit" disabled={posting || !text.trim()}>
            Post
          </button>
          <button className="btn-cancel-comment" type="button" onClick={onClose}>Close</button>
        </div>
      </form>
    </>
  );
}
