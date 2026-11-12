package com.example.shafi.data;

import android.os.Handler;
import android.os.Looper;

import com.example.shafi.BuildConfig;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.core.SecureStore;
import com.example.shafi.core.SessionBus;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    private static ApiService service;
    private static OkHttpClient http;
    private static final Gson GSON = new GsonBuilder().create();

    private ApiClient() {
    }

    public static synchronized ApiService service() {
        if (service == null) {
            OkHttpClient refreshClient = new OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .build();
            http = new OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .callTimeout(18, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        String path = request.url().encodedPath();
                        String token = SecureStore.access();
                        if (token != null && !path.contains("/auth/")) {
                            request = request.newBuilder().header("Authorization", "Bearer " + token).build();
                        }
                        return chain.proceed(request);
                    })
                    .authenticator(new RefreshAuthenticator(refreshClient))
                    .build();
            service = new Retrofit.Builder()
                    .baseUrl(BuildConfig.API_BASE_URL)
                    .client(http)
                    .addConverterFactory(GsonConverterFactory.create(GSON))
                    .build()
                    .create(ApiService.class);
        }
        return service;
    }

    public static OkHttpClient http() {
        service();
        return http;
    }

    public static void warm() {
        AppExecutors.IO.execute(() -> {
            try {
                service();
                String base = BuildConfig.API_BASE_URL;
                int marker = base.indexOf("/api/v1/");
                String origin = marker > 0 ? base.substring(0, marker) : base;
                Request request = new Request.Builder().url(origin + "/health").get().build();
                try (Response response = http.newCall(request).execute()) {
                    response.close();
                }
            } catch (Exception ignored) {
                // A cold host can wake in the background. The screens still load on their own.
            }
        });
    }

    public static String absolute(String url) {
        if (url == null) return "";
        if (url.startsWith("http")) return url;
        String base = BuildConfig.API_BASE_URL;
        int marker = base.indexOf("/api/v1/");
        String origin = marker > 0 ? base.substring(0, marker) : base;
        return origin + url;
    }

    private static final class RefreshAuthenticator implements Authenticator {
        private final OkHttpClient refreshClient;

        private RefreshAuthenticator(OkHttpClient refreshClient) {
            this.refreshClient = refreshClient;
        }

        @Override
        public Request authenticate(Route route, Response response) {
            if (count(response) >= 2) return null;
            String refresh = SecureStore.refresh();
            if (refresh == null) return null;
            synchronized (RefreshAuthenticator.class) {
                String latest = SecureStore.access();
                String header = response.request().header("Authorization");
                if (latest != null && header != null && !header.endsWith(latest)) {
                    return response.request().newBuilder().header("Authorization", "Bearer " + latest).build();
                }
                RequestBody body = RequestBody.create(
                        "{\"refreshToken\":\"" + refresh + "\"}",
                        MediaType.parse("application/json"));
                Request refreshRequest = new Request.Builder()
                        .url(BuildConfig.API_BASE_URL + "auth/refresh")
                        .post(body)
                        .build();
                try (Response refreshed = refreshClient.newCall(refreshRequest).execute()) {
                    if (!refreshed.isSuccessful() || refreshed.body() == null) {
                        SecureStore.clear();
                        new Handler(Looper.getMainLooper()).post(() -> SessionBus.expired.setValue(true));
                        return null;
                    }
                    Dtos.Envelope<?> envelope = GSON.fromJson(refreshed.body().string(), Dtos.Envelope.class);
                    if (envelope == null || envelope.data == null) return null;
                    Dtos.AuthResult tokens = GSON.fromJson(GSON.toJson(envelope.data), Dtos.AuthResult.class);
                    if (tokens == null || tokens.accessToken == null) return null;
                    String name = tokens.user != null && tokens.user.fullName != null ? tokens.user.fullName : SecureStore.name();
                    String role = tokens.user != null && tokens.user.role != null ? tokens.user.role : SecureStore.role();
                    SecureStore.saveSession(tokens.accessToken, tokens.refreshToken, name, role);
                    return response.request().newBuilder()
                            .header("Authorization", "Bearer " + tokens.accessToken)
                            .build();
                } catch (IOException error) {
                    return null;
                }
            }
        }

        private int count(Response response) {
            int count = 1;
            while ((response = response.priorResponse()) != null) count++;
            return count;
        }
    }
}
