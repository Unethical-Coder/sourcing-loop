# Sourcing Loop

An AI-powered recruiter sourcing and refinement loop built for the Flexiple Engineering Hiring assignment.

The recruiter describes a hiring requirement in natural language. The application uses a real server-side LLM to generate structured objective filters and a subjective fit rubric, filters the supplied candidate dataset locally, scores matching profiles with the LLM, and lets the recruiter refine the search through feedback until the shortlist is frozen.

## Features

- Natural-language recruiter search
- Real server-side Groq LLM calls
- Structured objective filters
- Subjective role-specific fit rubric
- Local filtering over the supplied 48-profile dataset
- LLM-based candidate scoring and ranking
- Candidate explanations grounded in profile fields
- Top 5 recruiter-facing candidates
- Editable filters and rubric
- Conversational refinement
- Freeze protection for unapplied edits
- Designed empty-results state
- LLM/API error state with retry
- Loading/thinking states
- Frozen final shortlist
- JSON export
- Stateless single-session workflow

## Tech Stack

**Frontend**
- React
- Vite
- Tailwind CSS

**Backend**
- Java 21
- Spring Boot 3
- Maven

**AI**
- Groq API

**Data**
- `backend/src/main/resources/profiles.json`
- 48 fictional candidate profiles
- No database or persistent storage

## Requirements

- Java 21
- Maven
- Node.js + npm
- Groq API key

## Running Locally

### 1. Start the backend

From the repository root:

```powershell
cd backend
$env:GROQ_API_KEY="your-groq-api-key"
mvn spring-boot:run
```

Backend:

```text
http://localhost:8080
```

The API key is read from:

```text
GROQ_API_KEY
```

The key is never stored in the repository.

### 2. Start the frontend

Open a second terminal:

```powershell
cd frontend
npm install
npm run dev
```

Frontend:

```text
http://localhost:5173
```

Vite proxies `/api` requests to the Spring Boot backend.

## How It Works

### 1. Free text → filters + rubric

The recruiter enters a requirement such as:

```text
RDS developers with 4-7 years of experience who have worked at startups in Bangalore
```

The backend calls the LLM to generate:

- Objective filters
- A subjective fit rubric

Both are shown in the UI and can be edited directly.

### 2. Local filtering

The objective filters are applied in Java against:

```text
backend/src/main/resources/profiles.json
```

The LLM does not decide which profiles pass the hard filters.

### 3. Scoring

Profiles that pass the filters are scored against the fit rubric using the LLM.

The UI presents the top 5 candidates at a time. Each candidate includes a score and a short explanation grounded in real profile data.

### 4. Refinement

The recruiter can edit the filters/rubric or provide conversational feedback, for example:

```text
Candidate 2 is too junior.
```

```text
Candidates 1 and 4 are closer to what I need.
```

```text
Prefer stronger database scaling experience.
```

The application uses that feedback to update the search state, rerun the search, and show the updated shortlist.

### 5. Freeze

Once satisfied, the recruiter can freeze the search.

The final state contains:

- Final objective filters
- Final fit rubric
- Final ranked shortlist

The frozen state is locked and can be exported as JSON.

## Failure and Recovery

The application deliberately handles failure states instead of crashing.

### Loading

The UI shows thinking/progress states while LLM requests are running.

### Empty results

When zero profiles match the objective filters, the recruiter sees an explicit empty state with an option to clear the filters and rerun the search.

### LLM/API failure

When an LLM/API request fails:

- The current search state is preserved
- The error is shown clearly
- A `Try again` action is provided
- The retry repeats the failed operation

This keeps the recruiter in the current workflow instead of forcing a restart.

## Freeze Protection

Editing filters or the rubric marks the search as having unapplied changes.

The recruiter cannot freeze those changes until the search has been rerun.

```text
Edit filters/rubric
        ↓
Unapplied changes
        ↓
Freeze disabled
        ↓
Re-run with edits
        ↓
Updated results
        ↓
Freeze enabled
```

## Grounded Explanations

Candidate explanations are required to cite actual fields from the candidate profile.

The backend validates cited fields before they are displayed.

## Project Structure

```text
sourcing-loop/
├── backend/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/sourcing/
│   │       │       ├── model/
│   │       │       ├── service/
│   │       │       └── web/
│   │       └── resources/
│   │           ├── application.properties
│   │           └── profiles.json
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── App.jsx
│   │   ├── api.js
│   │   ├── index.css
│   │   └── main.jsx
│   ├── index.html
│   ├── package.json
│   └── vite.config.js
│
├── .gitignore
├── LICENSE
└── README.md
```

## Prompts

The LLM prompts are kept in the backend source code and are part of the repository so they can be reviewed.

The application uses prompts for:

- Requirement extraction
- Candidate scoring
- Grounded explanations
- Search refinement

## Technical Decisions

### Stateless single-session design

The assignment requires one sourcing session, so there is no authentication, database, session store, or persistence between page reloads.

The current search state is held by the client and sent to the backend when needed for refinement operations.

### Deterministic objective filtering

Hard requirements are filtered locally in Java.

This keeps objective filtering predictable and leaves the LLM responsible for interpreting requirements, subjective fit, and refinement.

### LLM scoring

Candidates that pass the hard filters are scored against the generated rubric with Groq.

The scoring batch is intentionally limited to control latency and API usage.

### Structured output

LLM responses used as application state are parsed into structured models rather than rendered as arbitrary text.

The backend handles malformed responses and request failures deliberately.

## What I Prioritised

Within the assignment's three-hour time box, I prioritised:

1. A complete end-to-end sourcing loop
2. Real LLM calls
3. Editable filters and rubric
4. Local objective filtering
5. Candidate scoring and grounded explanations
6. Conversational refinement
7. Clear loading, empty, error, retry, and frozen states

## What I Cut

To stay focused on the requested workflow, I did not build:

- Authentication
- Multiple recruiter roles
- Persistent search history
- Database-backed candidate storage
- Production-scale talent infrastructure
- Resume ingestion
- Analytics dashboards
- Multi-session persistence
- Production deployment infrastructure

## Assignment Scope

This implementation runs the sourcing refinement loop against the supplied sample dataset. It is intentionally a local, single-session implementation rather than a production-scale recruiting platform.

## License

MIT License.
