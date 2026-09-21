async function post(url, body) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || `Request failed (${res.status})`);
  return data;
}

export const search = (query) => post('/api/search', { query });
export const refine = (feedback, state, candidates) => post('/api/refine', { feedback, state, candidates });
export const rescore = (state) => post('/api/rescore', { state });