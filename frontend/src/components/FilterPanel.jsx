import { useState } from 'react';

const input =
  'w-full rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 disabled:bg-slate-50 disabled:text-slate-400';
const label = 'mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500';

function TagInput({ title, values, onChange, disabled }) {
  const [draft, setDraft] = useState('');

  const add = () => {
    const v = draft.trim().replace(/,$/, '');
    if (v && !values.includes(v)) onChange([...values, v]);
    setDraft('');
  };

  const onKeyDown = (e) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      add();
    } else if (e.key === 'Backspace' && !draft && values.length) {
      onChange(values.slice(0, -1));
    }
  };

  return (
    <div>
      <span className={label}>{title}</span>
      <div className="flex flex-wrap gap-1.5 rounded-lg border border-slate-300 bg-white p-2 focus-within:border-indigo-500 focus-within:ring-2 focus-within:ring-indigo-200">
        {values.map((v) => (
          <span key={v} className="flex items-center gap-1 rounded-md bg-indigo-50 px-2 py-0.5 text-xs text-indigo-700">
            {v}
            <button
              type="button"
              disabled={disabled}
              onClick={() => onChange(values.filter((x) => x !== v))}
              className="text-indigo-400 hover:text-indigo-700"
            >
              ×
            </button>
          </span>
        ))}
        <input
          value={draft}
          disabled={disabled}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKeyDown}
          onBlur={add}
          placeholder={values.length ? '' : 'Add…'}
          className="min-w-16 flex-1 bg-transparent text-sm outline-none"
        />
      </div>
    </div>
  );
}

export default function FilterPanel({ filters, rubric, onFilters, onRubric, dirty, onRerun, disabled, loading }) {
  const set = (patch) => onFilters({ ...filters, ...patch });
  const num = (v) => (v === '' ? null : Number(v));

  return (
    <aside
      className={`h-fit space-y-5 rounded-xl border border-slate-200 bg-white p-5 shadow-sm lg:sticky lg:top-20 ${
        loading ? 'animate-pulse opacity-60' : ''
      }`}
    >
      <div>
        <span className={label}>Experience (years)</span>
        <div className="flex items-center gap-2">
          <input
            type="number"
            min="0"
            step="0.5"
            placeholder="Min"
            disabled={disabled}
            value={filters.min_experience ?? ''}
            onChange={(e) => set({ min_experience: num(e.target.value) })}
            className={input}
          />
          <span className="text-slate-400">–</span>
          <input
            type="number"
            min="0"
            step="0.5"
            placeholder="Max"
            disabled={disabled}
            value={filters.max_experience ?? ''}
            onChange={(e) => set({ max_experience: num(e.target.value) })}
            className={input}
          />
        </div>
      </div>

      <TagInput title="Skills (all required)" values={filters.skills} onChange={(skills) => set({ skills })} disabled={disabled} />
      <TagInput title="Locations (any)" values={filters.locations} onChange={(locations) => set({ locations })} disabled={disabled} />
      <TagInput
        title="Company type (any)"
        values={filters.company_types}
        onChange={(company_types) => set({ company_types })}
        disabled={disabled}
      />

      <div>
        <span className={label}>Fit rubric</span>
        <textarea
          rows={6}
          disabled={disabled}
          value={rubric.join('\n')}
          onChange={(e) => onRubric(e.target.value.split('\n'))}
          placeholder="One criterion per line"
          className={`${input} resize-y leading-relaxed`}
        />
      </div>

      <button
        onClick={onRerun}
        disabled={disabled || !dirty}
        className="w-full rounded-lg bg-indigo-600 px-3 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-500 disabled:opacity-40"
      >
        Re-run with edits
      </button>
    </aside>
  );
}