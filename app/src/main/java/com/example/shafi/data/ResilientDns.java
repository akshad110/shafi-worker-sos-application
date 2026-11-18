package com.example.shafi.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Uses the phone DNS first, then public DNS by IP when the phone cannot resolve the API host. */
final class ResilientDns implements Dns {
    private final OkHttpClient lookupClient;

    ResilientDns() {
        lookupClient = new OkHttpClient.Builder()
                .dns(hostname -> {
                    if ("cloudflare-dns.com".equals(hostname)) {
                        return Arrays.asList(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"));
                    }
                    if ("dns.google".equals(hostname)) {
                        return Arrays.asList(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4"));
                    }
                    throw new UnknownHostException(hostname);
                })
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        if (hostname == null || hostname.isEmpty()) {
            throw new UnknownHostException("hostname == null");
        }
        try {
            List<InetAddress> system = ipv4First(Dns.SYSTEM.lookup(hostname));
            if (!system.isEmpty()) return system;
        } catch (UnknownHostException ignored) {
            // The phone DNS returned no address. Ask a public resolver next.
        }
        List<InetAddress> resolved = query("https://cloudflare-dns.com/dns-query", hostname, 0);
        if (resolved.isEmpty()) resolved = query("https://dns.google/resolve", hostname, 0);
        if (resolved.isEmpty()) {
            throw new UnknownHostException("Unable to resolve host \"" + hostname + "\": No address associated with hostname");
        }
        return resolved;
    }

    private List<InetAddress> query(String endpoint, String hostname, int depth) {
        if (depth > 3) return Collections.emptyList();
        HttpUrl url = HttpUrl.parse(endpoint);
        if (url == null) return Collections.emptyList();
        url = url.newBuilder()
                .addQueryParameter("name", hostname)
                .addQueryParameter("type", "A")
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .get()
                .build();
        try (Response response = lookupClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
            JSONObject json = new JSONObject(response.body().string());
            JSONArray answers = json.optJSONArray("Answer");
            if (answers == null) return Collections.emptyList();
            List<InetAddress> addresses = new ArrayList<>();
            String cname = null;
            for (int i = 0; i < answers.length(); i++) {
                JSONObject answer = answers.optJSONObject(i);
                if (answer == null) continue;
                int type = answer.optInt("type");
                String data = answer.optString("data", "");
                if (type == 1 && !data.isEmpty()) {
                    addresses.add(InetAddress.getByName(data));
                } else if (type == 5 && !data.isEmpty()) {
                    cname = data.endsWith(".") ? data.substring(0, data.length() - 1) : data;
                }
            }
            if (!addresses.isEmpty()) return addresses;
            if (cname != null) return query(endpoint, cname, depth + 1);
            return Collections.emptyList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private static List<InetAddress> ipv4First(List<InetAddress> addresses) {
        List<InetAddress> ipv4 = new ArrayList<>();
        List<InetAddress> rest = new ArrayList<>();
        for (InetAddress address : addresses) {
            if (address instanceof Inet4Address) ipv4.add(address);
            else rest.add(address);
        }
        ipv4.addAll(rest);
        return ipv4;
    }
}
