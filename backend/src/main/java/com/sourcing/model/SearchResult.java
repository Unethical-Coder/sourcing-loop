package com.sourcing.model;

import java.util.List;

public record SearchResult(
        SourcingState state,
        List<ScoredCandidate> candidates,
        int totalMatched,
        String note,
        String warning) {}