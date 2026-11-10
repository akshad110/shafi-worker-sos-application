package com.example.shafi.data;

import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;

public interface ApiService {
    @POST("auth/register")
    Call<Dtos.Envelope<Dtos.AuthResult>> register(@Body Map<String, Object> body);

    @POST("auth/send-otp")
    Call<Dtos.Envelope<Dtos.AuthResult>> sendOtp(@Body Map<String, Object> body);

    @POST("auth/verify-otp")
    Call<Dtos.Envelope<Dtos.AuthResult>> verifyOtp(@Body Map<String, Object> body);

    @POST("auth/login")
    Call<Dtos.Envelope<Dtos.AuthResult>> login(@Body Map<String, Object> body);

    @POST("auth/logout")
    Call<Dtos.Envelope<Object>> logout(@Body Map<String, String> body);

    @GET("workers/me/dashboard")
    Call<Dtos.Envelope<Dtos.Dashboard>> dashboard();

    @GET("workers/me/profile")
    Call<Dtos.Envelope<Dtos.ProfileResponse>> profile();

    @PATCH("workers/me/profile")
    Call<Dtos.Envelope<Dtos.ProfileResponse>> updateProfile(@Body Map<String, Object> body);

    @PATCH("workers/me/availability")
    Call<Dtos.Envelope<Object>> availability(@Body Map<String, Object> body);

    @PATCH("users/me/preferences")
    Call<Dtos.Envelope<Dtos.Preferences>> preferences(@Body Map<String, Object> body);

    @GET("jobs")
    Call<Dtos.Envelope<Dtos.JobPage>> jobs(@QueryMap Map<String, String> query);

    @GET("jobs/{id}")
    Call<Dtos.Envelope<Dtos.JobItem>> job(@Path("id") String id);

    @POST("jobs/{id}/applications")
    Call<Dtos.Envelope<Dtos.ApplicationItem>> apply(@Path("id") String id, @Body Map<String, Object> body);

    @GET("workers/me/applications")
    Call<Dtos.Envelope<java.util.List<Dtos.ApplicationItem>>> applications();

    @GET("applications/{id}")
    Call<Dtos.Envelope<Dtos.ApplicationItem>> application(@Path("id") String id);

    @POST("applications/{id}/withdraw")
    Call<Dtos.Envelope<Dtos.ApplicationItem>> withdraw(@Path("id") String id, @Body Map<String, Object> body);

    @GET("placements/{id}")
    Call<Dtos.Envelope<Dtos.PlacementItem>> placement(@Path("id") String id);

    @POST("placements/{id}/accept")
    Call<Dtos.Envelope<Dtos.PlacementItem>> accept(@Path("id") String id, @Body Map<String, Object> body);

    @POST("placements/{id}/decline")
    Call<Dtos.Envelope<Dtos.PlacementItem>> decline(@Path("id") String id, @Body Map<String, Object> body);

    @GET("workers/me/shifts")
    Call<Dtos.Envelope<java.util.List<Dtos.ShiftItem>>> shifts();

    @GET("shifts/{id}")
    Call<Dtos.Envelope<Dtos.ShiftItem>> shift(@Path("id") String id);

    @POST("shifts/{id}/check-in")
    Call<Dtos.Envelope<Dtos.ShiftItem>> checkIn(@Path("id") String id, @Body Map<String, Object> body);

    @POST("shifts/{id}/check-out")
    Call<Dtos.Envelope<Dtos.ShiftItem>> checkOut(@Path("id") String id, @Body Map<String, Object> body);

    @POST("shifts/{id}/report-incident")
    Call<Dtos.Envelope<Dtos.ShiftItem>> incident(@Path("id") String id, @Body Map<String, Object> body);

    @POST("shifts/{id}/feedback")
    Call<Dtos.Envelope<Dtos.ShiftItem>> feedback(@Path("id") String id, @Body Map<String, Object> body);

    @GET("notifications")
    Call<Dtos.Envelope<Dtos.NoticePage>> notifications();

    @PATCH("notifications/{id}/read")
    Call<Dtos.Envelope<Object>> markRead(@Path("id") String id, @Body Map<String, Object> body);

    @PATCH("notifications/read-all")
    Call<Dtos.Envelope<Object>> markAll(@Body Map<String, Object> body);

    @GET("workers/me/earnings")
    Call<Dtos.Envelope<Dtos.Earnings>> earnings();

    @GET("workers/me/documents")
    Call<Dtos.Envelope<java.util.List<Dtos.DocItem>>> documents();

    @Multipart
    @POST("workers/me/documents")
    Call<Dtos.Envelope<Dtos.DocItem>> upload(@Part("type") RequestBody type, @Part MultipartBody.Part file);

    @GET("workers/me/documents/{id}/url")
    Call<Dtos.Envelope<Dtos.FileLink>> documentUrl(@Path("id") String id);
}
