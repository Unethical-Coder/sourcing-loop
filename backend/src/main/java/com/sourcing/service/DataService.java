package com.sourcing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sourcing.model.CandidateProfile;
import com.sourcing.model.SourcingState.ObjectiveFilters;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
public class DataService {

    private final ObjectMapper mapper;
    private List<CandidateProfile> profiles = List.of();
    private Map<String, List<String>> vocabulary = Map.of();

    public DataService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    void load() throws IOException {
        try (var in = new ClassPathResource("profiles.json").getInputStream()) {
            var root = mapper.readTree(in);
            profiles = mapper.convertValue(
                    root.isArray() ? root : root.path("profiles"),
                    new TypeReference<List<CandidateProfile>>() {});
        }
        vocabulary = Map.of(
                "locations", distinct(CandidateProfile::location),
                "company_types", distinct(CandidateProfile::currentCompanyType),
                "skills", profiles.stream().flatMap(p -> p.skills().stream()).distinct().sorted().limit(250).toList());
    }

    public Map<String, List<String>> vocabulary() {
        return vocabulary;
    }

    public List<CandidateProfile> filter(ObjectiveFilters f) {
        var skills = f.skills().stream().map(DataService::term).toList();
        return profiles.stream()
                .filter(p -> f.minExperience() == null || p.yearsExperience() >= f.minExperience())
                .filter(p -> f.maxExperience() == null || p.yearsExperience() <= f.maxExperience())
                .filter(p -> f.locations().isEmpty() || anyContains(p.location(), f.locations()))
                .filter(p -> f.companyTypes().isEmpty() || anyContains(p.currentCompanyType(), f.companyTypes()))
                .filter(p -> hasAll(p, skills))
                .toList();
    }

    private List<String> distinct(Function<CandidateProfile, String> field) {
        return profiles.stream().map(field).filter(Objects::nonNull).distinct().sorted().toList();
    }

    private static Pattern term(String s) {
        return Pattern.compile("(?<![\\p{L}\\d])" + Pattern.quote(s.strip()) + "(?![\\p{L}\\d])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static boolean hasAll(CandidateProfile p, List<Pattern> terms) {
        if (terms.isEmpty()) return true;
        var hay = String.join(" | ", p.skills())
                + " | " + Objects.toString(p.currentTitle(), "")
                + " | " + Objects.toString(p.summary(), "");
        return terms.stream().allMatch(t -> t.matcher(hay).find());
    }

    private static boolean anyContains(String value, List<String> terms) {
        if (value == null) return false;
        var v = value.toLowerCase();
        return terms.stream().anyMatch(t -> v.contains(t.toLowerCase()));
    }
}