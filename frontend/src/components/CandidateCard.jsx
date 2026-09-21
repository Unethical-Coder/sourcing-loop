const tone = (s) => (s >= 75 ? 'bg-emerald-500' : s >= 50 ? 'bg-amber-500' : 'bg-rose-400');

export default function CandidateCard({ rank, candidate }) {
  const { profile: p, evaluation: e } = candidate;
  const cited = Object.entries(e.cited_fields ?? {});
  const scored = typeof e.score === 'number';
  const skills = p.skills ?? [];

  return (
    <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-start gap-4">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-slate-900 text-sm font-semibold text-white">
          {rank}
        </div>
        <div className="min-w-0 flex-1">
          <h3 className="truncate font-semibold">{p.name}</h3>
          <p className="truncate text-sm text-slate-600">
            {p.current_title} · {p.current_company}
          </p>
          <p className="text-xs text-slate-500">
            {p.location} · {p.years_experience} yrs
          </p>
        </div>
        <div className="w-24 shrink-0 text-right">
          <div className="text-2xl font-semibold tabular-nums">{scored ? e.score : '—'}</div>
          <div className="mt-1 h-1.5 rounded-full bg-slate-100">
            <div className={`h-full rounded-full ${tone(e.score)}`} style={{ width: `${scored ? e.score : 0}%` }} />
          </div>
        </div>
      </div>

      <div className="mt-4 flex flex-wrap gap-1.5">
        {p.current_company_type && (
          <span className="rounded-md bg-violet-50 px-2 py-0.5 text-xs font-medium text-violet-700">
            {p.current_company_type}
          </span>
        )}
        {skills.slice(0, 8).map((s) => (
          <span key={s} className="rounded-md bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
            {s}
          </span>
        ))}
        {skills.length > 8 && <span className="px-1 py-0.5 text-xs text-slate-400">+{skills.length - 8}</span>}
      </div>

      <p className="mt-4 text-sm leading-relaxed text-slate-700">{e.explanation}</p>

      {scored && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {cited.length === 0 && <span className="text-xs text-amber-600">No verified field citations</span>}
          {cited.map(([field, value]) => (
            <span key={field} className="rounded bg-indigo-50 px-2 py-0.5 font-mono text-[11px] text-indigo-700">
              {field}: {Array.isArray(value) ? value.join(', ') : String(value)}
            </span>
          ))}
        </div>
      )}
    </article>
  );
}