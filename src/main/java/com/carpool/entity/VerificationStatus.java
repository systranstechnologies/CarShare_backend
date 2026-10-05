package com.carpool.entity;

public enum VerificationStatus {
    NOT_STARTED,
    INITIATED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    // Legacy values retained for existing database rows and older clients.
    UNVERIFIED,
    PENDING,
    PENDING_VERIFICATION,
    VERIFIED,
    IN_REVIEW;

    public boolean isApproved() {
        return this == APPROVED || this == VERIFIED;
    }

    public boolean hasBeenInitiated() {
        return this != NOT_STARTED && this != UNVERIFIED;
    }

    public VerificationStatus canonical() {
        return switch (this) {
            case UNVERIFIED, PENDING, PENDING_VERIFICATION -> NOT_STARTED;
            case VERIFIED -> APPROVED;
            case IN_REVIEW -> UNDER_REVIEW;
            default -> this;
        };
    }
}
