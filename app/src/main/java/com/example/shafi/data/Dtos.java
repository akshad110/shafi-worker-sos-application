package com.example.shafi.data;

import java.util.List;

public final class Dtos {
    private Dtos() {
    }

    public static class Envelope<T> {
        public boolean success;
        public String message;
        public String code;
        public T data;
    }

    public static class AuthResult {
        public String accessToken;
        public String refreshToken;
        public String debugOtp;
        public String userId;
        public String mobile;
        public UserAccount user;
    }

    public static class UserAccount {
        public String id;
        public String fullName;
        public String mobile;
        public String email;
        public String role;
        public String status;
        public boolean mobileVerified;
    }

    public static class JobItem {
        public String id;
        public String hospitalName;
        public boolean hospitalVerified;
        public String title;
        public String healthcareRole;
        public String department;
        public String city;
        public String address;
        public String shiftDate;
        public String startTime;
        public String endTime;
        public String shiftType;
        public long compensationMinor;
        public String currency;
        public String paymentUnit;
        public int vacancies;
        public int filledCount;
        public boolean urgent;
        public String deadline;
        public String status;
        public Double distanceKm;
        public Double latitude;
        public Double longitude;
        public String description;
        public List<String> qualifications;
        public List<String> skills;
        public List<String> certifications;
        public Integer experienceYears;
        public String reportingInstructions;
        public boolean geofenceEnabled;
        public boolean hasApplied;
        public String applicationId;
        public boolean eligible;
        public String eligibilityReason;
        public String supervisorName;
        public String supervisorPhone;
    }

    public static class JobPage {
        public List<JobItem> items;
        public int page;
        public int total;
        public boolean hasMore;
    }

    public static class Counts {
        public int total;
        public int underReview;
        public int shortlisted;
        public int offered;
        public int confirmed;
    }

    public static class NoticeData {
        public String screen;
        public String entityId;
    }

    public static class ActivityItem {
        public String id;
        public String type;
        public String title;
        public String body;
        public boolean read;
        public String createdAt;
        public NoticeData data;
    }

    public static class DocItem {
        public String id;
        public String type;
        public String fileName;
        public String status;
        public String expiresAt;
        public String rejectionReason;
    }

    public static class PaymentInfo {
        public String id;
        public String status;
        public long amountMinor;
        public String currency;
        public String paidAt;
        public String reference;
        public String title;
        public String hospitalName;
        public String shiftDate;
    }

    public static class CheckPoint {
        public String at;
        public Double lat;
        public Double lng;
    }

    public static class ShiftItem {
        public String id;
        public String status;
        public String startAt;
        public String endAt;
        public CheckPoint checkIn;
        public CheckPoint checkOut;
        public Integer feedbackRating;
        public String feedbackComment;
        public PaymentInfo payment;
        public JobItem job;
    }

    public static class Dashboard {
        public String greetingName;
        public String verificationStatus;
        public int completion;
        public String healthcareRole;
        public String city;
        public boolean canApply;
        public List<String> missing;
        public List<DocItem> expiringDocuments;
        public Counts counts;
        public ShiftItem upcomingShift;
        public List<JobItem> recommendedJobs;
        public List<ActivityItem> recentActivity;
    }

    public static class HistoryItem {
        public String status;
        public String at;
        public String note;
    }

    public static class ApplicationItem {
        public String id;
        public String reference;
        public String status;
        public String createdAt;
        public String updatedAt;
        public List<HistoryItem> history;
        public JobItem job;
        public String placementId;
    }

    public static class PlacementItem {
        public String id;
        public String status;
        public String expiresAt;
        public long compensationMinor;
        public String currency;
        public String instructions;
        public JobItem job;
    }

    public static class WorkerProfile {
        public String healthcareRole;
        public String specialization;
        public List<String> qualifications;
        public List<String> skills;
        public Integer experienceYears;
        public String address;
        public String city;
        public String emergencyContactName;
        public String emergencyContactPhone;
        public List<String> preferredDepartments;
        public List<String> preferredShifts;
        public List<String> preferredLocations;
        public List<String> unavailableDates;
        public boolean onboardingSubmitted;
        public String verificationStatus;
    }

    public static class Preferences {
        public boolean jobs = true;
        public boolean offers = true;
        public boolean shifts = true;
        public boolean payments = true;
    }

    public static class ProfileResponse {
        public UserAccount user;
        public WorkerProfile profile;
        public int completion;
        public boolean canApply;
        public List<String> missing;
        public Preferences preferences;
    }

    public static class Earnings {
        public String currency;
        public long totalPaidMinor;
        public long totalPendingMinor;
        public List<PaymentInfo> items;
    }

    public static class NoticePage {
        public int unread;
        public List<ActivityItem> items;
    }

    public static class FileLink {
        public String url;
        public String expiresAt;
    }
}
