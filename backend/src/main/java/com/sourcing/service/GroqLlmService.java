package com.sourcing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sourcing.SourcingException;
import com.sourcing.model.CandidateEvaluation;
import com.sourcing.model.CandidateProfile;
import com.sourcing.model.ScoredCandidate;
import com.sourcing.model.SourcingState;
import com.sourcing.model.SourcingState.ObjectiveFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class GroqLlmService {

    private static final Logger log = LoggerFactory.getLogger(GroqLlmService.class);
    private static final String MODEL = "openai/gpt-oss-20b";

    public record Refinement(SourcingState state, String summary) {}

    private record Row(int rank, String id, String name, String currentTitle, double yearsExperience,
                       String currentCompany, String currentCompanyType, String location,
                       Integer score, String explanation) {}

    private static final String EXTRACT_PROMPT = """
            You turn a recruiter's free-text search into a structured sourcing brief.
            Reply with a single JSON object shaped exactly like this:
            {
              "filters": {
                "skills": [string],
                "min_experience": number or null,
                "max_experience": number or null,
                "locations": [string],
                "company_types": [string]
              },
              "rubric": [string]
            }

            Rules for "filters" (hard, objective, checkable against a profile):
            - skills: hard requirements only. Use the shortest distinctive term that covers every variant in the
              dataset vocabulary ("RDS" rather than "AWS RDS"). Nice-to-haves go in the rubric.
            - min_experience / max_experience: years, inclusive. "4-7 years" -> 4 and 7. "5+" -> min 5, max null.
              Not stated -> null.
            - locations: include common aliases ("Bangalore" and "Bengaluru"). Prefer spellings from the vocabulary.
            - company_types: pick from the vocabulary's company_types, and only when the recruiter says what kind
              of company the person works or worked at.
            - Leave a field empty rather than guessing.

            Rules for "rubric" (subjective criteria a human would judge):
            - 3 to 5 short criteria, most important first, each a full phrase
              (e.g. "Hands-on production experience with RDS, not just light usage").
            - Capture preferences that a filter cannot check.
            """;

    private static final String SCORE_PROMPT = """
            You are a recruiter scoring candidates against a fit rubric.
            Use ONLY the data in each candidate profile. Never assume facts that are not in it.
            Reply with a single JSON object:
            {
              "evaluations": [
                {
                  "id": string,
                  "score": integer 0-100,
                  "explanation": string,
                  "cited_fields": { "<profile field name>": "<value copied verbatim from that field>" }
                }
              ]
            }

            Rules:
            - Exactly one evaluation per candidate, using the id you were given.
            - explanation: 2-3 sentences. Every claim must point at a concrete profile value and name the field,
              e.g. "years_experience is 5", "current_company_type is startup", "skills include AWS RDS".
            - cited_fields: every field you relied on, with the exact value from the profile. At least 2 entries.
              For list fields (skills, past_companies) cite the specific items you used.
            - If a rubric criterion is not supported by the profile, mark it down and say which field shows the gap.
            - Hard filters are already applied. Score rubric fit only, and spread scores so the ranking is meaningful.
            """;

    private static final String REFINE_PROMPT = """
            You adjust a recruiter's sourcing brief based on their chat feedback.
            You get the current filters, the current rubric, the ranked candidates they are looking at
            ("Candidate 1" is rank 1), and their feedback.
            Reply with a single JSON object:
            {
              "filters": {
                "skills": [string],
                "min_experience": number or null,
                "max_experience": number or null,
                "locations": [string],
                "company_types": [string]
              },
              "rubric": [string],
              "summary": string
            }

            Rules:
            - Return the FULL updated filters and rubric, not a diff. Keep everything the feedback does not touch.
            - Turn feedback about a specific candidate into a general rule using that candidate's actual fields.
              "Candidate 1 is too junior" when Candidate 1 has years_experience 3 means raising min_experience
              above 3, not excluding that person by name.
            - Objective, checkable constraints go in filters. Judgement calls go in the rubric
              ("prefer people who led a team" changes the rubric, not the filters).
            - Use the dataset vocabulary for spellings and keep location aliases.
            - Never widen or drop a hard filter unless the feedback asks for it.
            - summary: one plain sentence saying what you changed and why.
            """;

    private final RestClient client;
    private final ObjectMapper mapper;
    private final DataService data;
    private final String apiKey;

    public GroqLlmService(RestClient.Builder builder, ObjectMapper mapper, DataService data,
                          @Value("${GROQ_API_KEY:}") String apiKey) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(45));
        this.client = builder
                .baseUrl("https://api.groq.com/openai/v1")
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.mapper = mapper;
        this.data = data;
        this.apiKey = apiKey;
    }

    public SourcingState extract(String query) {
        var user = """
                Recruiter search: %s

                Dataset vocabulary:
                %s
                """.formatted(query, json(data.vocabulary()));
        var root = chat(EXTRACT_PROMPT, user);
        return new SourcingState(query, filtersFrom(root, ObjectiveFilters.empty()), rubricFrom(root, List.of()));
    }

    public List<ScoredCandidate> score(List<CandidateProfile> batch, List<String> rubric) {
        var user = """
                Rubric:
                %s

                Candidates:
                %s
                """.formatted(numbered(rubric), json(batch));
        var root = chat(SCORE_PROMPT, user);
        var node = root.path("evaluations");
        if (!node.isArray()) throw malformed(new IllegalStateException("no evaluations array"));

        Map<String, CandidateEvaluation> byId = read(node, new TypeReference<List<CandidateEvaluation>>() {})
                .stream()
                .filter(e -> e.id() != null)
                .collect(Collectors.toMap(CandidateEvaluation::id, e -> e, (a, b) -> a));

        return batch.stream()
                .map(p -> new ScoredCandidate(p, byId.containsKey(p.id())
                        ? ground(byId.get(p.id()), p)
                        : CandidateEvaluation.unscored(p.id(), "The model did not return an evaluation for this candidate.")))
                .sorted(Comparator.comparing((ScoredCandidate s) -> s.evaluation().score(),
                        Comparator.nullsLast(Comparator.<Integer>reverseOrder())))
                .toList();
    }

    public Refinement refine(SourcingState current, String feedback, List<ScoredCandidate> shown) {
        var rows = IntStream.range(0, shown.size()).mapToObj(i -> {
            var p = shown.get(i).profile();
            var e = shown.get(i).evaluation();
            return new Row(i + 1, p.id(), p.name(), p.currentTitle(), p.yearsExperience(), p.currentCompany(),
                    p.currentCompanyType(), p.location(), e.score(), e.explanation());
        }).toList();

        var user = """
                Current filters:
                %s

                Current rubric:
                %s

                Candidates on screen (ranked):
                %s

                Dataset vocabulary:
                %s

                Recruiter feedback: %s
                """.formatted(json(current.filters()), numbered(current.rubric()), json(rows),
                json(data.vocabulary()), feedback);

        var root = chat(REFINE_PROMPT, user);
        var next = new SourcingState(current.query(),
                filtersFrom(root, current.filters()),
                rubricFrom(root, current.rubric()));
        return new Refinement(next, root.path("summary").asText("Updated the search."));
    }

    private CandidateEvaluation ground(CandidateEvaluation e, CandidateProfile p) {
        Map<String, Object> fields = mapper.convertValue(p, new TypeReference<Map<String, Object>>() {});
        var verified = new LinkedHashMap<String, Object>();
        e.citedFields().forEach((field, cited) -> {
            var actual = fields.get(field);
            if (actual == null) return;
            var haystack = String.valueOf(actual).toLowerCase();
            List<String> values = cited instanceof Collection<?> c
                    ? c.stream().map(x -> String.valueOf(x)).toList()
                    : List.of(String.valueOf(cited));
            if (!values.isEmpty() && values.stream().allMatch(v -> haystack.contains(v.toLowerCase()))) {
                verified.put(field, cited);
            }
        });
        return new CandidateEvaluation(e.id(), e.score(), e.explanation(), verified);
    }

    private JsonNode chat(String system, String user) {
        if (apiKey.isBlank()) throw new SourcingException("GROQ_API_KEY is not set on the server", 500);

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "temperature", 0.1,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                var res = client.post().uri("/chat/completions").body(body).retrieve().body(JsonNode.class);
                var content = res == null ? "" : res.path("choices").path(0).path("message").path("content").asText("");
                var parsed = mapper.readTree(content);
                if (parsed.isObject()) return parsed;
                log.warn("Groq returned non-object content");
            } catch (HttpClientErrorException.TooManyRequests e) {
                long wait = retryAfter(e);
                if (attempt == 0 && wait <= 8) {
                    pause(wait);
                } else {
                    throw new SourcingException("Groq rate limit hit. Try again in about " + wait + "s.", 429);
                }
            } catch (HttpClientErrorException.BadRequest | JsonProcessingException e) {
                log.warn("Bad completion from Groq: {}", e.getMessage());
            } catch (HttpClientErrorException.Unauthorized e) {
                throw new SourcingException("Groq rejected the API key", 500);
            } catch (RestClientException e) {
                throw new SourcingException("Groq request failed: " + e.getMessage(), 502);
            }
        }
        throw new SourcingException("The model kept returning malformed JSON. Try again.", 502);
    }

    private long retryAfter(HttpClientErrorException e) {
        try {
            return (long) Math.ceil(Double.parseDouble(e.getResponseHeaders().getFirst("retry-after")));
        } catch (RuntimeException ex) {
            return 10;
        }
    }

    private void pause(long seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private ObjectiveFilters filtersFrom(JsonNode root, ObjectiveFilters fallback) {
        var node = root.path("filters");
        return node.isObject() ? read(node, ObjectiveFilters.class) : fallback;
    }

    private List<String> rubricFrom(JsonNode root, List<String> fallback) {
        var node = root.path("rubric");
        return node.isArray() ? read(node, new TypeReference<List<String>>() {}) : fallback;
    }

    private <T> T read(JsonNode node, Class<T> type) {
        try {
            return mapper.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw malformed(e);
        }
    }

    private <T> T read(JsonNode node, TypeReference<T> type) {
        try {
            return mapper.convertValue(node, type);
        } catch (IllegalArgumentException e) {
            throw malformed(e);
        }
    }

    private SourcingException malformed(Exception e) {
        log.warn("Unexpected model output", e);
        return new SourcingException("The model returned an unexpected response. Try again or rephrase.", 502);
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String numbered(List<String> items) {
        if (items.isEmpty()) return "(none)";
        return IntStream.range(0, items.size())
                .mapToObj(i -> (i + 1) + ". " + items.get(i))
                .collect(Collectors.joining("\n"));
    }
}