import { useState } from 'react';
import { search, refine, rescore } from './api';
import FilterPanel from './components/FilterPanel';
import CandidateCard from './components/CandidateCard';
import ChatBar from './components/ChatBar';

const EMPTY_FILTERS = { skills: [], min_experience: null, max_experience: null, locations: [], company_types: [] };

const DISPLAY_LIMIT = 5;

const EXAMPLES = [
  'RDS developers with 4-7 years of experience who worked at startups in Bangalore',
  'Backend engineers with Kafka experience, 5+ years, at product companies',
  'Data engineers with Spark and Airflow in Hyderabad',
];

const PILL = {
  idle: 'bg-slate-100 text-slate-600',
  thinking: 'bg-indigo-100 text-indigo-700',
  active: 'bg-emerald-100 text-emerald-700',
  frozen: 'bg-slate-900 text-white',
};

export default function App() {
  const [status, setStatus] = useState('idle');
  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [rubric, setRubric] = useState([]);
  const [candidates, setCandidates] = useState([]);
  const [total, setTotal] = useState(0);
  const [warning, setWarning] = useState(null);
  const [error, setError] = useState(null);
  const [messages, setMessages] = useState([]);
  const [feedback, setFeedback] = useState('');
  const [pending, setPending] = useState('');
  const [dirty, setDirty] = useState(false);

  const thinking = status === 'thinking';
  const frozen = status === 'frozen';
  const visibleCandidates = candidates.slice(0, DISPLAY_LIMIT);

  const snapshot = () => ({ query, filters, rubric: rubric.filter((r) => r.trim()) });

  const run = async (label, fallback, call) => {
    setStatus('thinking');
    setPending(label);
    setError(null);
    try {
      const res = await call();
      setQuery(res.state.query ?? query);
      setFilters(res.state.filters);
      setRubric(res.state.rubric);
      setCandidates(res.candidates);
      setTotal(res.total_matched);
      setWarning(res.warning);
      setDirty(false);
      if (res.note) setMessages((m) => [...m, { role: 'assistant', text: res.note }]);
      setStatus('active');
    } catch (e) {
      setError(e.message);
      setStatus(fallback);
    }
  };

  const onSearch = (e) => {
    e.preventDefault();
    const q = query.trim();
    if (!q) return;
    setMessages([]);
    setCandidates([]);
    setFilters(EMPTY_FILTERS);
    setRubric([]);
    run('Reading your brief and scoring candidates…', 'idle', () => search(q));
  };

  const onRefine = (e) => {
    e.preventDefault();
    const text = feedback.trim();
    if (!text || thinking || frozen) return;
    setMessages((m) => [...m, { role: 'user', text }]);
    setFeedback('');
    run('Applying your feedback…', 'active', () => refine(text, snapshot(), visibleCandidates));
  };

  const onRerun = () => run('Re-scoring with your edits…', 'active', () => rescore(snapshot()));

  const updateFilters = (next) => {
    setFilters(next);
    setDirty(true);
  };

  const updateRubric = (next) => {
    setRubric(next);
    setDirty(true);
  };

  const clearFilters = () => {
    setFilters(EMPTY_FILTERS);
    setDirty(true);
  };

  const exportJson = () => {
    const out = {
      ...snapshot(),
      candidates: visibleCandidates.map((c) => ({
        id: c.profile.id,
        name: c.profile.name,
        score: c.evaluation.score,
        explanation: c.evaluation.explanation,
      })),
    };
    const blob = new Blob([JSON.stringify(out, null, 2)], { type: 'application/json' });
    const a = Object.assign(document.createElement('a'), {
      href: URL.createObjectURL(blob),
      download: 'frozen-search.json',
    });
    a.click();
    URL.revokeObjectURL(a.href);
  };

  if (status === 'idle') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 px-6">
        <form onSubmit={onSearch} className="w-full max-w-2xl">
          <h1 className="text-3xl font-semibold tracking-tight text-slate-900">Who are you looking for?</h1>
          <p className="mt-2 text-slate-500">
            Describe the role in plain English. We'll pull out the hard filters and a fit rubric you can edit.
          </p>
          <textarea
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) onSearch(e);
            }}
            rows={3}
            autoFocus
            placeholder="RDS developers with 4-7 years of experience who worked at startups in Bangalore"
            className="mt-6 w-full resize-none rounded-xl border border-slate-300 bg-white p-4 text-base shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200"
          />
          {error && (
            <p className="mt-3 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 ring-1 ring-rose-200">{error}</p>
          )}
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <button
              type="submit"
              disabled={!query.trim()}
              className="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-500 disabled:opacity-40"
            >
              Find candidates
            </button>
            {EXAMPLES.map((ex) => (
              <button
                key={ex}
                type="button"
                onClick={() => setQuery(ex)}
                className="max-w-xs truncate rounded-full border border-slate-200 bg-white px-3 py-1 text-xs text-slate-600 hover:border-indigo-300 hover:text-indigo-700"
              >
                {ex}
              </button>
            ))}
          </div>
        </form>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/80 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center gap-4 px-6 py-3">
          <h1 className="text-lg font-semibold tracking-tight">Sourcing Loop</h1>
          <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium capitalize ${PILL[status]}`}>{status}</span>
          <p className="hidden min-w-0 flex-1 truncate text-sm text-slate-500 md:block">{query}</p>
          <div className="ml-auto flex gap-2">
            {frozen && (
              <button
                onClick={exportJson}
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium hover:bg-slate-100"
              >
                Export JSON
              </button>
            )}
            {status === 'active' && (
            <button
                onClick={() => setStatus('frozen')}
                disabled={dirty}
                title={dirty ? 'Re-run with your edits before freezing the search' : 'Freeze this search'}
                className="rounded-lg bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-40"
            >
                {dirty ? 'Re-run before freezing' : 'Freeze search'}
            </button>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto grid max-w-7xl gap-6 px-6 pb-48 pt-6 lg:grid-cols-[320px_1fr]">
        <FilterPanel
          filters={filters}
          rubric={rubric}
          onFilters={updateFilters}
          onRubric={updateRubric}
          dirty={dirty}
          onRerun={onRerun}
          disabled={thinking || frozen}
          loading={thinking}
        />

        <section className="space-y-4">
          {thinking && (
            <div className="flex items-center gap-3 rounded-lg bg-indigo-50 px-4 py-2.5 text-sm text-indigo-700 ring-1 ring-indigo-100">
              <span className="h-4 w-4 animate-spin rounded-full border-2 border-indigo-300 border-t-indigo-700" />
              {pending}
            </div>
          )}
          {error && (
            <div className="rounded-lg bg-rose-50 px-4 py-2.5 text-sm text-rose-700 ring-1 ring-rose-200">{error}</div>
          )}
          {warning && !thinking && total !== 0 &&(
            <div className="rounded-lg bg-amber-50 px-4 py-2.5 text-sm text-amber-800 ring-1 ring-amber-200">{warning}</div>
          )}
          {frozen && (
            <div className="rounded-lg bg-slate-900 px-4 py-2.5 text-sm text-white">
              Search frozen. Filters, rubric and ranking are locked.
            </div>
          )}

          {!thinking && candidates.length > 0 && (
            <p className="text-sm text-slate-500">
                {total} profiles pass the filters
                {candidates.length > DISPLAY_LIMIT &&
                ` · showing the top ${DISPLAY_LIMIT}`}
            </p>
          )}
          {thinking
            ? Array.from({ length: DISPLAY_LIMIT }, (_, i) => i).map((i) => (
                <div key={i} className="animate-pulse rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
                  <div className="flex gap-4">
                    <div className="h-9 w-9 rounded-full bg-slate-200" />
                    <div className="flex-1 space-y-2">
                      <div className="h-4 w-1/3 rounded bg-slate-200" />
                      <div className="h-3 w-1/2 rounded bg-slate-100" />
                    </div>
                    <div className="h-8 w-16 rounded bg-slate-200" />
                  </div>
                  <div className="mt-4 space-y-2">
                    <div className="h-3 rounded bg-slate-100" />
                    <div className="h-3 w-5/6 rounded bg-slate-100" />
                  </div>
                </div>
              ))
            : visibleCandidates.map((c, i) => (
                <CandidateCard key={c.profile.id} rank={i + 1} candidate={c} />
            ))}

            {!thinking && candidates.length === 0 && total === 0 && !error && (
            <div className="rounded-xl border border-slate-200 bg-white px-6 py-12 text-center shadow-sm">
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-xl text-slate-500">
                0
                </div>

                <h2 className="mt-4 text-base font-semibold text-slate-900">
                No candidates match these filters
                </h2>

                <p className="mx-auto mt-2 max-w-md text-sm leading-relaxed text-slate-500">
                Try widening the experience range, removing a required skill, or changing
                the location. You can also clear the filters and run the search again.
                </p>

                {!frozen && (
                <button
                    type="button"
                    onClick={clearFilters}
                    className="mt-5 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                    Clear filters
                </button>
                )}
            </div>
            )}
        </section>
      </main>

      <ChatBar
        messages={messages}
        value={feedback}
        onChange={setFeedback}
        onSubmit={onRefine}
        disabled={thinking || frozen}
      />
    </div>
  );
}