package com.labguard.model;

public record Hazard(
        String category,
        String riskLevel,
        String location,
        String evidence,
        Double confidence,
        String uncertainty,
        String suggestedAction,
        boolean needsHumanReview,
        boolean demoData
) {}
