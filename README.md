# Sourcing Refinement Loop

A local AI recruiter. Describe who you want in plain English and Groq (llama-3.3-70b-versatile) extracts hard filters plus a subjective fit rubric. The filters run in memory over `profiles.json`, the survivors get scored against the rubric with explanations that cite real profile fields, and you steer the results with chat feedback until you freeze the search.

Put `profiles.json` in `backend/src/main/resources/`. In one terminal run `cd backend && export GROQ_API_KEY=... && ./mvnw spring-boot:run` (Java 21, serves on :8080). In another run `cd frontend && npm install && npm run dev` and open http://localhost:5173. Vite proxies `/api` to the backend, so there is no CORS config to deal with.

## Technical Decisions

- **Stateless backend.** The client holds the filters, rubric and last candidates and sends them along with every call. No session store, no DB, nothing to clean up. The cost is that a page refresh loses the session, which is fine for a timeboxed MVP.
- **Filtering is plain Java streams, not the LLM.** The model only sees candidates that already passed the objective filters. Skills match on word boundaries against skills, title and summary, so "Java" doesn't hit "JavaScript". Locations and company types are substring matches. The extraction prompt gets the dataset's real vocabulary and is told to emit aliases (Bangalore/Bengaluru).
- **One scoring call, capped at 15 candidates.** Groq's token-per-minute limits are tight, so batching or parallel calls would burn through them fast. If more profiles match, the UI says so and the fix is tighter filters.
- **Citations are checked server-side.** Each `cited_fields` entry must name a real profile field, and the value must actually appear in it. Anything that doesn't is dropped from the card.
- **Failure handling.** One retry on a 429 (honoring `retry-after` up to 8s) and one retry on malformed JSON or a JSON-mode 400. If scoring still fails, the filter matches come back unranked with a warning instead of an error page. If extraction or refinement fails, the UI keeps its current state and shows the message.
- **Freeze is a client-side lock** plus a JSON export. There is no persistence.