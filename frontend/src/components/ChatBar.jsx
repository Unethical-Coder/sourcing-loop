import { useEffect, useRef } from 'react';

export default function ChatBar({ messages, value, onChange, onSubmit, disabled }) {
  const log = useRef(null);

  useEffect(() => {
    log.current?.scrollTo({ top: log.current.scrollHeight });
  }, [messages]);

  return (
    <footer className="fixed inset-x-0 bottom-0 border-t border-slate-200 bg-white/90 backdrop-blur">
      <div className="mx-auto max-w-7xl px-6 py-3">
        {messages.length > 0 && (
          <div ref={log} className="mb-3 max-h-28 space-y-1.5 overflow-y-auto text-sm">
            {messages.map((m, i) => (
              <p key={i} className={m.role === 'user' ? 'text-slate-900' : 'text-slate-500'}>
                <span className="mr-2 font-medium">{m.role === 'user' ? 'You' : 'Sourcer'}</span>
                {m.text}
              </p>
            ))}
          </div>
        )}
        <form onSubmit={onSubmit} className="flex gap-2">
          <input
            value={value}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            placeholder={
              disabled ? 'Search is locked' : 'Give feedback, e.g. "Candidate 2 is too junior" or "prefer people who led a team"'
            }
            className="flex-1 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 disabled:bg-slate-50"
          />
          <button
            type="submit"
            disabled={disabled || !value.trim()}
            className="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-medium text-white hover:bg-indigo-500 disabled:opacity-40"
          >
            Refine
          </button>
        </form>
      </div>
    </footer>
  );
}