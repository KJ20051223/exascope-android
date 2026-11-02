package io.github.exascope;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Retained across Activity recreation; it never holds an Android Context. */
final class SearchSession {
    interface Observer { void changed(); }
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private Observer observer;
    private ExaClient client;
    private long generation;
    volatile boolean busy;
    volatile String error = "", query = "", sentQuery = "";
    volatile ExaClient.Response response;
    SearchConfig draft;
    boolean settingsOpen;
    long startedAt;
    long elapsedMs;

    synchronized void attach(Observer value) { observer = value; }
    synchronized void detach() { observer = null; }
    private synchronized void notifyChanged() { if (observer != null) observer.changed(); }

    synchronized void search(SearchConfig config, String text) {
        cancel();
        long request = ++generation;
        query = text.trim(); sentQuery = config.effectiveQuery(query);
        response = null; error = ""; busy = true; startedAt = System.currentTimeMillis();
        ExaClient requestClient = new ExaClient();
        client = requestClient;
        notifyChanged();
        executor.execute(() -> {
            ExaClient.Response result = null;
            String failure = "";
            try { result = requestClient.search(config, text); }
            catch (SocketTimeoutException ex) { failure = "连接或搜索超时，请检查网络后重试"; }
            catch (UnknownHostException ex) { failure = "无法连接搜索服务，请检查网络和 API 地址"; }
            catch (ExaClient.SearchException ex) { failure = ex.getMessage(); }
            catch (Exception ex) { failure = "搜索失败，请检查网络、Key 与 API 地址后重试"; }
            synchronized (SearchSession.this) {
                if (request != generation) return;
                busy = false; client = null; response = result; error = failure;
                elapsedMs = System.currentTimeMillis() - startedAt;
                notifyChanged();
            }
        });
    }
    synchronized void cancel() {
        generation++;
        ExaClient previous = client; client = null;
        if (previous != null) executor.execute(previous::cancel);
        if (busy) { busy = false; error = "搜索已取消"; notifyChanged(); }
    }
    synchronized void close() {
        detach(); cancel(); executor.shutdown();
    }
}
