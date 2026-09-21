package com.sourcing.model;

import java.util.List;

public record SourcingState(String query, ObjectiveFilters filters, List<String> rubric) {

    public SourcingState {
        filters = filters == null ? ObjectiveFilters.empty() : filters;
        rubric = clean(rubric);
    }

    public record ObjectiveFilters(
            List<String> skills,
            Double minExperience,
            Double maxExperience,
            List<String> locations,
            List<String> companyTypes) {

        public ObjectiveFilters {
            skills = clean(skills);
            locations = clean(locations);
            companyTypes = clean(companyTypes);
        }

        public static ObjectiveFilters empty() {
            return new ObjectiveFilters(null, null, null, null, null);
        }
    }

    private static List<String> clean(List<String> in) {
        if (in == null) return List.of();
        return in.stream().filter(s -> s != null && !s.isBlank()).map(String::strip).toList();
    }
}