package com.sourcing.model;

import java.util.Map;

public record CandidateEvaluation(String id, Integer score, String explanation, Map<String, Object> citedFields) {

    public CandidateEvaluation {
        score = score == null ? null : Math.max(0, Math.min(100, score));
        explanation = explanation == null ? "" : explanation;
        citedFields = citedFields == null ? Map.of() : citedFields;
    }

    public static CandidateEvaluation unscored(String id, String reason) {
        return new CandidateEvaluation(id, null, reason, Map.of());
    }
}