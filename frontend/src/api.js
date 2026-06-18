async function request(url, options = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  });
  if (res.status === 401) {
    const err = new Error('unauthorized');
    err.status = 401;
    throw err;
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    const err = new Error(body.error || 'request-failed');
    err.status = res.status;
    throw err;
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export const getMe      = ()               => request('/api/me');
export const getTickets = ()               => request('/api/tickets');
export const getComments = (orderId)       => request(`/api/tickets/${orderId}/comments`);
export const submitAction = (orderId, action, reason) =>
  request(`/api/tickets/${orderId}/action`, {
    method: 'POST',
    body: JSON.stringify({ action, reason: reason ?? null }),
  });
export const submitComment = (orderId, text) =>
  request(`/api/tickets/${orderId}/comment`, {
    method: 'POST',
    body: JSON.stringify({ text }),
  });
