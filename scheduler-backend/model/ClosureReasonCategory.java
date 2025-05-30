package com.usbank.corp.dcr.api.model;

public enum ClosureReasonCategory {
    NOT_RELEVANT("Not relevant to my needs"),
    TOO_FREQUENT("Too many notifications"),
    NOT_HELPFUL("Not helpful"),
    PREFER_OTHER_CHANNEL("Prefer other communication channel"),
    OTHER("Other");
    
    private final String description;
    
    ClosureReasonCategory(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}