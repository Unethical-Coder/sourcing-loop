package com.sourcing.web;

import com.sourcing.SourcingException;
import com.sourcing.model.CandidateEvaluation;
import com.sourcing.model.ScoredCandidate;
import com.sourcing.model.SearchResult;
import com.sourcing.model.SourcingState;
import com.sourcing.service.DataService;
import com.sourcing.service.GroqLlmService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SearchController {

    private static final int MAX_SCORED = 15;

    record SearchRequest(String query) {}

    record RescoreRequest(SourcingState state) {}

    record RefineRequest(String feedback, SourcingState state, List<ScoredCandidate> candidates) {}

    private final DataService data;
    private final GroqLlmService llm;

    public SearchController(DataService data, GroqLlmService llm) {
        this.data = data;
        this.llm = llm;
    }

    @PostMapping("/search")
    public SearchResult search(@RequestBody SearchRequest req) {
        if (req.query() == null || req.query().isBlank()) {
            throw new SourcingException("Enter a search first", 400);
        }
        return run(llm.extract(req.query().strip()), null);
    }

    @PostMapping("/refine")
    public SearchResult refine(@RequestBody RefineRequest req) {
        if (req.state() == null || req.feedback() == null || req.feedback().isBlank()) {
            throw new SourcingException("Nothing to refine", 400);
        }
        var shown = req.candidates() == null ? List.<ScoredCandidate>of() : req.candidates();
        var refined = llm.refine(req.state(), req.feedback().strip(), shown);
        return run(refined.state(), refined.summary());
    }

    @PostMapping("/rescore")
    public SearchResult rescore(@RequestBody RescoreRequest req) {
        if (req.state() == null) throw new SourcingException("Missing search state", 400);
        return run(req.state(), "Re-ran the search with your edits.");
    }

    @ExceptionHandler(SourcingException.class)
    ResponseEntity<Map<String, String>> onError(SourcingException e) {
        return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage()));
    }

    private SearchResult run(SourcingState state, String note) {
        var matched = data.filter(state.filters());
        var batch = matched.stream().limit(MAX_SCORED).toList();

        if (batch.isEmpty()) {
            return new SearchResult(state, List.of(), 0, note, "No profiles pass these filters. Loosen one and try again.");
        }
        try {
            return new SearchResult(state, llm.score(batch, state.rubric()), matched.size(), note, null);
        } catch (SourcingException e) {
            var unscored = batch.stream()
                    .map(p -> new ScoredCandidate(p, CandidateEvaluation.unscored(p.id(), "Not scored: " + e.getMessage())))
                    .toList();
            return new SearchResult(state, unscored, matched.size(), note,
                    "Scoring failed, showing filter matches unranked. " + e.getMessage());
        }
    }
}