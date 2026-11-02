package io.github.exascope;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExaClient {
    public static final class Result {
        public final String title, url, snippet, author, date;
        public Result(String title, String url, String snippet, String author, String date) {
            this.title = title; this.url = url; this.snippet = snippet; this.author = author; this.date = date;
        }
    }
    public static final class Response {
        public final List<Result> results;
        public final String requestId;
        public final double cost;
        Response(List<Result> results, String requestId, double cost) {
            this.results = Collections.unmodifiableList(results); this.requestId = requestId; this.cost = cost;
        }
    }
    public static final class SearchException extends IOException {
        public SearchException(String message) { super(message); }
    }
    private volatile HttpURLConnection activeConnection;
    private volatile boolean cancelled;

    public static JSONObject payload(SearchConfig config, String query) throws Exception {
        config.validate();
        JSONObject body = new JSONObject();
        body.put("query", config.effectiveQuery(query));
        body.put("type", config.searchType);
        body.put("numResults", config.resultCount);
        body.put("moderation", true);
        body.put("contents", new JSONObject().put("highlights", true));
        if (!config.category.isEmpty()) body.put("category", config.category);
        List<String> domains = config.includeDomains();
        if (!domains.isEmpty()) body.put("includeDomains", new JSONArray(domains));
        return body;
    }

    public Response search(SearchConfig config, String query) throws Exception {
        if (config.apiKey.isEmpty()) throw new SearchException("请先在设置中填写 Exa API Key");
        return execute(new URL(config.searchUrl()), config.apiKey, payload(config, query).toString());
    }

    Response execute(URL endpoint, String apiKey, String body) throws Exception {
        if (cancelled) throw new SearchException("搜索已取消");
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        activeConnection = connection;
        try {
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(45000);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("x-api-key", apiKey);
            connection.setDoOutput(true);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            if (cancelled) throw new SearchException("搜索已取消");
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            if (cancelled) throw new SearchException("搜索已取消");
            if (status < 200 || status >= 300) throw new SearchException(errorMessage(status));
            try (InputStream input = connection.getInputStream()) { return parse(readLimited(input)); }
        } finally {
            activeConnection = null;
            connection.disconnect();
        }
    }

    public void cancel() {
        cancelled = true;
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
    }

    static String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int size;
        while ((size = input.read(buffer)) != -1) {
            if (output.size() + size > 5 * 1024 * 1024) throw new SearchException("结果过大，请减少结果数量后重试");
            output.write(buffer, 0, size);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    public static Response parse(String json) throws Exception {
        JSONObject body;
        try { body = new JSONObject(json); }
        catch (Exception error) { throw new SearchException("服务返回了无法识别的数据，请检查 API 地址"); }
        JSONArray rows = body.optJSONArray("results");
        if (rows == null) throw new SearchException("服务未返回搜索结果，请检查 API 兼容性");
        List<Result> results = new ArrayList<>();
        for (int i = 0; i < Math.min(rows.length(), 100); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String url = string(row, "url");
            if (!isWebUrl(url)) continue;
            String title = string(row, "title");
            if (title.isEmpty()) title = URI.create(url).getHost();
            List<String> excerpts = new ArrayList<>();
            JSONArray highlights = row.optJSONArray("highlights");
            if (highlights != null) for (int j = 0; j < Math.min(3, highlights.length()); j++) {
                if (highlights.opt(j) instanceof String && !highlights.optString(j).trim().isEmpty()) excerpts.add(highlights.optString(j));
            }
            String snippet = String.join("\n", excerpts);
            if (snippet.isEmpty()) snippet = string(row, "summary");
            if (snippet.isEmpty()) snippet = string(row, "text");
            if (snippet.length() > 3000) snippet = snippet.substring(0, 3000) + "…";
            results.add(new Result(title, url, snippet, string(row, "author"), string(row, "publishedDate")));
        }
        JSONObject cost = body.optJSONObject("costDollars");
        return new Response(results, string(body, "requestId"), cost == null ? -1 : cost.optDouble("total", -1));
    }

    private static String string(JSONObject object, String key) {
        Object value = object.opt(key);
        return value instanceof String ? ((String) value).trim() : "";
    }
    public static boolean isWebUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException error) { return false; }
    }
    static String errorMessage(int code) {
        return switch (code) {
            case 400, 422 -> "搜索参数不被服务接受，请检查分类、域名或 API 兼容性（" + code + "）";
            case 401, 403 -> "API Key 无效或没有权限，请在设置中检查（" + code + "）";
            case 402 -> "Exa 账户额度不足，请检查账户余额（402）";
            case 404 -> "找不到搜索接口，请检查 API 地址（404）";
            case 408, 504 -> "搜索服务超时，请稍后重试（" + code + "）";
            case 429 -> "请求过于频繁或达到额度上限，请稍后重试（429）";
            default -> code >= 300 && code < 400 ? "接口发生重定向，已停止发送 Key，请填写最终 HTTPS 地址"
                    : "搜索服务暂时不可用，请稍后重试（" + code + "）";
        };
    }
}
