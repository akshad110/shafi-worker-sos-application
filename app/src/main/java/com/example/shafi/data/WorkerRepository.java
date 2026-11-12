package com.example.shafi.data;

import android.content.Context;

import com.example.shafi.core.ApiException;
import com.example.shafi.core.SecureStore;
import com.example.shafi.core.SessionBus;
import com.example.shafi.data.local.AppDatabase;
import com.example.shafi.data.local.BlobEntity;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import retrofit2.Response;

public class WorkerRepository {
    private final ApiService api = ApiClient.service();
    private final AppDatabase database;
    private final Gson gson = new Gson();

    public WorkerRepository(Context context) {
        database = AppDatabase.get(context);
    }

    public Dtos.AuthResult register(String name, String email, String password, String role) throws IOException, ApiException {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", name);
        body.put("email", email);
        body.put("password", password);
        body.put("healthcareRole", role);
        body.put("consent", true);
        Dtos.AuthResult result = unwrap(api.register(body).execute());
        if (result.accessToken != null) store(result);
        return result;
    }

    public Dtos.AuthResult sendOtp(String mobile, String purpose) throws IOException, ApiException {
        Map<String, Object> body = new HashMap<>();
        body.put("mobile", mobile);
        body.put("purpose", purpose);
        return unwrap(api.sendOtp(body).execute());
    }

    public Dtos.AuthResult verify(String mobile, String code, String purpose) throws IOException, ApiException {
        Map<String, Object> body = new HashMap<>();
        body.put("mobile", mobile);
        body.put("code", code);
        body.put("purpose", purpose);
        Dtos.AuthResult result = unwrap(api.verifyOtp(body).execute());
        store(result);
        return result;
    }

    public Dtos.AuthResult login(String email, String password) throws IOException, ApiException {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        Dtos.AuthResult result = unwrap(api.login(body).execute());
        store(result);
        return result;
    }

    public void logout() {
        String refresh = SecureStore.refresh();
        SecureStore.clear();
        try {
            Map<String, String> body = new HashMap<>();
            body.put("refreshToken", refresh == null ? "" : refresh);
            api.logout(body).execute();
        } catch (Exception ignored) {
            // The local session is already cleared if the network call fails.
        }
    }

    public Dtos.Dashboard dashboard() throws IOException, ApiException {
        Dtos.Dashboard data = unwrap(api.dashboard().execute());
        cache("dashboard", data);
        if (data != null && data.greetingName != null) SecureStore.saveName(data.greetingName);
        return data;
    }

    public Dtos.Dashboard cachedDashboard() {
        return cached("dashboard", Dtos.Dashboard.class);
    }

    public Dtos.JobPage jobs(String query, String shiftType, boolean urgentOnly, String sort, int page) throws IOException, ApiException {
        Map<String, String> params = new HashMap<>();
        if (query != null && !query.isEmpty()) params.put("q", query);
        if (shiftType != null && !shiftType.isEmpty()) params.put("shiftType", shiftType);
        if (urgentOnly) params.put("urgent", "true");
        params.put("sort", sort == null ? "newest" : sort);
        params.put("page", String.valueOf(page));
        params.put("limit", "20");
        Dtos.JobPage data = unwrap(api.jobs(params).execute());
        if (page == 1 && data != null && data.items != null) cache("jobs", data.items);
        return data;
    }

    public List<Dtos.JobItem> cachedJobs() {
        Dtos.JobItem[] items = cached("jobs", Dtos.JobItem[].class);
        if (items == null) return java.util.Collections.emptyList();
        return java.util.Arrays.asList(items);
    }

    public Dtos.JobItem job(String id) throws IOException, ApiException {
        return unwrap(api.job(id).execute());
    }

    public Dtos.ApplicationItem apply(String jobId) throws IOException, ApiException {
        return apply(jobId, "");
    }

    public Dtos.ApplicationItem apply(String jobId, String note) throws IOException, ApiException {
        Map<String, Object> body = new HashMap<>();
        if (note != null && !note.trim().isEmpty()) body.put("note", note.trim());
        return unwrap(api.apply(jobId, body).execute());
    }

    public List<Dtos.ApplicationItem> applications() throws IOException, ApiException {
        List<Dtos.ApplicationItem> items = unwrap(api.applications().execute());
        cache("applications", items);
        return items;
    }

    public List<Dtos.ApplicationItem> cachedApplications() {
        Dtos.ApplicationItem[] items = cached("applications", Dtos.ApplicationItem[].class);
        if (items == null) return java.util.Collections.emptyList();
        return java.util.Arrays.asList(items);
    }

    public Dtos.ApplicationItem application(String id) throws IOException, ApiException {
        return unwrap(api.application(id).execute());
    }

    public Dtos.ApplicationItem withdraw(String id) throws IOException, ApiException {
        return unwrap(api.withdraw(id, new HashMap<>()).execute());
    }

    public Dtos.PlacementItem placement(String id) throws IOException, ApiException {
        return unwrap(api.placement(id).execute());
    }

    public Dtos.PlacementItem accept(String id) throws IOException, ApiException {
        return unwrap(api.accept(id, new HashMap<>()).execute());
    }

    public Dtos.PlacementItem decline(String id) throws IOException, ApiException {
        return unwrap(api.decline(id, new HashMap<>()).execute());
    }

    public List<Dtos.ShiftItem> shifts() throws IOException, ApiException {
        List<Dtos.ShiftItem> items = unwrap(api.shifts().execute());
        cache("shifts", items);
        return items;
    }

    public List<Dtos.ShiftItem> cachedShifts() {
        Dtos.ShiftItem[] items = cached("shifts", Dtos.ShiftItem[].class);
        if (items == null) return java.util.Collections.emptyList();
        return java.util.Arrays.asList(items);
    }

    public Dtos.ShiftItem shift(String id) throws IOException, ApiException {
        return unwrap(api.shift(id).execute());
    }

    public Dtos.ShiftItem checkIn(String id, Double lat, Double lng) throws IOException, ApiException {
        Map<String, Object> body = new HashMap<>();
        if (lat != null && lng != null) {
            body.put("lat", lat);
            body.put("lng", lng);
        }
        return unwrap(api.checkIn(id, body).execute());
    }

    public Dtos.ShiftItem checkOut(String id, Double lat, Double lng) throws IOException, ApiException {
        Map<String, Object> body = new HashMap<>();
        if (lat != null && lng != null) {
            body.put("lat", lat);
            body.put("lng", lng);
        }
        return unwrap(api.checkOut(id, body).execute());
    }

    public Dtos.ShiftItem incident(String id, String category, String description) throws IOException, ApiException {
        Map<String, Object> body = new HashMap<>();
        body.put("category", category);
        body.put("description", description);
        return unwrap(api.incident(id, body).execute());
    }

    public Dtos.ShiftItem feedback(String id, int rating, String comment) throws IOException, ApiException {
        Map<String, Object> body = new HashMap<>();
        body.put("rating", rating);
        body.put("comment", comment);
        return unwrap(api.feedback(id, body).execute());
    }

    public Dtos.NoticePage notifications() throws IOException, ApiException {
        return unwrap(api.notifications().execute());
    }

    public void markRead(String id) throws IOException, ApiException {
        unwrap(api.markRead(id, new HashMap<>()).execute());
    }

    public void markAll() throws IOException, ApiException {
        unwrap(api.markAll(new HashMap<>()).execute());
    }

    public Dtos.ReviewPage reviews() throws IOException, ApiException {
        return unwrap(api.reviews().execute());
    }

    public Dtos.ReviewPage addReview(String shiftId, int rating, String message) throws IOException, ApiException {
        Map<String, Object> body = new HashMap<>();
        body.put("shiftId", shiftId);
        body.put("rating", rating);
        body.put("message", message == null ? "" : message);
        return unwrap(api.addReview(body).execute());
    }

    public Dtos.Earnings earnings() throws IOException, ApiException {
        return unwrap(api.earnings().execute());
    }

    public Dtos.ProfileResponse profile() throws IOException, ApiException {
        Dtos.ProfileResponse data = unwrap(api.profile().execute());
        cache("profile", data);
        if (data != null && data.user != null) SecureStore.saveName(data.user.fullName);
        return data;
    }

    public Dtos.ProfileResponse cachedProfile() {
        return cached("profile", Dtos.ProfileResponse.class);
    }

    public Dtos.ProfileResponse updateProfile(Map<String, Object> body) throws IOException, ApiException {
        return unwrap(api.updateProfile(body).execute());
    }

    public void updateAvailability(Map<String, Object> body) throws IOException, ApiException {
        unwrap(api.availability(body).execute());
    }

    public Dtos.Preferences preferences(Map<String, Object> body) throws IOException, ApiException {
        return unwrap(api.preferences(body).execute());
    }

    public List<Dtos.DocItem> documents() throws IOException, ApiException {
        return unwrap(api.documents().execute());
    }

    public Dtos.DocItem upload(String type, String fileName, String mime, byte[] bytes) throws IOException, ApiException {
        RequestBody typeBody = RequestBody.create(type, MediaType.parse("text/plain"));
        RequestBody fileBody = RequestBody.create(bytes, MediaType.parse(mime));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", fileName, fileBody);
        return unwrap(api.upload(typeBody, part).execute());
    }

    public byte[] downloadDocument(String id) throws IOException, ApiException {
        Dtos.FileLink link = unwrap(api.documentUrl(id).execute());
        Request request = new Request.Builder().url(ApiClient.absolute(link.url)).build();
        try (okhttp3.Response response = ApiClient.http().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new ApiException("Could not open the document", "FILE", response.code());
            }
            return response.body().bytes();
        }
    }

    private void store(Dtos.AuthResult result) throws ApiException {
        if (result == null || result.user == null || !"WORKER".equals(result.user.role)) {
            throw new ApiException("This app is for healthcare workers. Use the hospital portal for organization accounts.", "WRONG_APP", 403);
        }
        SecureStore.saveSession(result.accessToken, result.refreshToken, result.user.fullName, result.user.role);
    }

    private <T> T unwrap(retrofit2.Response<Dtos.Envelope<T>> response) throws ApiException, IOException {
        if (response.isSuccessful() && response.body() != null && response.body().success) {
            return response.body().data;
        }
        String message = "Request failed";
        String code = "";
        if (response.errorBody() != null) {
            try {
                Dtos.Envelope<?> envelope = gson.fromJson(response.errorBody().charStream(), Dtos.Envelope.class);
                if (envelope != null && envelope.message != null) message = envelope.message;
                if (envelope != null && envelope.code != null) code = envelope.code;
            } catch (Exception ignored) {
                message = "Request failed";
            }
        }
        if (response.code() == 401 && response.raw().request().header("Authorization") != null) {
            SessionBus.expire();
        }
        throw new ApiException(message, code, response.code());
    }

    private void cache(String bucket, Object value) {
        try {
            BlobEntity entity = new BlobEntity();
            entity.bucket = bucket;
            entity.json = gson.toJson(value);
            database.blobs().put(entity);
        } catch (Exception ignored) {
            // Cache failure must not block the network result.
        }
    }

    private <T> T cached(String bucket, Class<T> type) {
        try {
            BlobEntity entity = database.blobs().get(bucket);
            if (entity == null || entity.json == null) return null;
            return gson.fromJson(entity.json, type);
        } catch (Exception ignored) {
            return null;
        }
    }
}
