package com.labguard.model;

public record WorkOrder(
        long id,
        String category,
        String riskLevel,
        String location,
        String suggestedAction,
        String owner,
        String status,
        String rectificationNote,
        String reviewNote
) {}
