package com.sourcing.model;

import java.util.List;

public record CandidateProfile(
        String id,
        String name,
        String currentTitle,
        double yearsExperience,
        String location,
        String currentCompany,
        String currentCompanyType,
        List<String> skills,
        List<Object> pastCompanies,
        Object education,
        String summary) {

    public CandidateProfile {
        skills = skills == null ? List.of() : skills;
        pastCompanies = pastCompanies == null ? List.of() : pastCompanies;
    }
}