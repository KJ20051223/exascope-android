package io.github.exascope;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.Test;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

public class SearchContractTest {
    private SearchConfig config(String scope, String template, String domains) {
        return new SearchConfig("https://api.exa.ai", "test-key", scope, template, "publication", domains, "auto", 10);
    }
    @Test public void scopeIsInjectedOnEveryQuery() {
        SearchConfig c=config("学术论文",SearchConfig.DEFAULT_TEMPLATE,"");
        assertEquals("钠离子电池\n\n搜索范围：学术论文",c.effectiveQuery("  钠离子电池  "));
        assertTrue(c.effectiveQuery("机器学习").contains("学术论文"));
    }
    @Test public void emptyScopeSendsOnlyUserQuery() {
        assertEquals("transformer",config("","{scope}: {query}","").effectiveQuery("transformer"));
    }
    @Test public void missingPlaceholdersCannotDropQueryOrScope() {
        String q=config("论文","优先原始来源","").effectiveQuery("battery");
        assertTrue(q.contains("battery"));assertTrue(q.contains("论文"));assertTrue(q.contains("优先原始来源"));
    }
    @Test public void templateSubstitutionPreservesUnicodeAndLineBreaks() {
        assertEquals("范围=论文\n主题=检索增强",config("论文","范围={scope}\n主题={query}","").effectiveQuery("检索增强"));
    }
    @Test public void endpointIsNormalizedWithoutDuplicateSearch() {
        assertEquals("https://api.exa.ai/search",config("","","").searchUrl());
        assertEquals("https://example.com/v1/search",new SearchConfig("https://example.com/v1/search/","k","","","","","auto",10).searchUrl());
    }
    @Test public void rejectsUnsafeEndpoints() {
        for(String url:new String[]{"http://example.com","https://user:pass@example.com","https://example.com?k=x","https://example.com#key","file:///tmp"}) {
            assertThrows(IllegalArgumentException.class,()->new SearchConfig(url,"key","","","","","auto",10).validate());
        }
    }
    @Test public void domainsAreNormalizedAndDeduplicated() {
        assertEquals(Arrays.asList("arxiv.org","pubmed.ncbi.nlm.nih.gov","example.com/docs"),
                config("","","https://ARXIV.org/，arxiv.org pubmed.ncbi.nlm.nih.gov;example.com/docs").includeDomains());
    }
    @Test public void rejectsMalformedDomains() {
        assertThrows(IllegalArgumentException.class,()->config("","","https://user:pass@arxiv.org").validate());
    }
    @Test public void requestHasExpectedContractAndNoApiKeyInBody() throws Exception {
        JSONObject body=ExaClient.payload(config("论文",SearchConfig.DEFAULT_TEMPLATE,"arxiv.org"),"graph neural networks");
        assertEquals("publication",body.getString("category"));assertEquals("auto",body.getString("type"));
        assertEquals(10,body.getInt("numResults"));assertEquals("arxiv.org",body.getJSONArray("includeDomains").getString(0));
        assertTrue(body.getJSONObject("contents").getBoolean("highlights"));assertTrue(body.getBoolean("moderation"));
        assertTrue(body.getString("query").contains("论文"));assertFalse(body.toString().contains("test-key"));
    }
    @Test public void optionalCategoryAndDomainsAreOmitted() throws Exception {
        JSONObject body=ExaClient.payload(new SearchConfig("","key","","","","","lite",5),"hello");
        assertFalse(body.has("category"));assertFalse(body.has("includeDomains"));assertEquals("lite",body.getString("type"));
    }
    @Test public void emptyAndOversizedQueriesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->config("","","").effectiveQuery("  "));
        assertThrows(IllegalArgumentException.class,()->config("","","").effectiveQuery("x".repeat(2001)));
    }
    @Test public void validatesCountsAndHeaderInjection() {
        assertThrows(IllegalArgumentException.class,()->new SearchConfig("","k","","","","","auto",101).validate());
        assertThrows(IllegalArgumentException.class,()->new SearchConfig("","k\nAuthorization: bad","","","","","auto",10).validate());
    }
    @Test public void handlesExcerptFallbacksAndMissingMetadata() throws Exception {
        ExaClient.Response response=ExaClient.parse("{\"results\":[{\"url\":\"https://arxiv.org/abs/1\",\"title\":null,\"highlights\":[\"摘要一\",\"摘要二\"]},{\"url\":\"https://example.com\",\"summary\":\"fallback\"}],\"costDollars\":{\"total\":0.007}}");
        assertEquals(2,response.results.size());assertEquals("arxiv.org",response.results.get(0).title);
        assertEquals("摘要一\n摘要二",response.results.get(0).snippet);assertEquals("fallback",response.results.get(1).snippet);assertEquals(.007,response.cost,.00001);
    }
    @Test public void rejectsNonWebResultLinks() throws Exception {
        assertEquals(0,ExaClient.parse("{\"results\":[{\"url\":\"javascript:alert(1)\"},{\"url\":\"intent://x\"},{\"url\":\"https://key@example.com\"}]}").results.size());
    }
    @Test public void validEmptyResultsDifferFromInvalidResponse() throws Exception {
        assertTrue(ExaClient.parse("{\"results\":[]}").results.isEmpty());
        assertThrows(ExaClient.SearchException.class,()->ExaClient.parse("<html>Bad Gateway</html>"));
        assertThrows(ExaClient.SearchException.class,()->ExaClient.parse("{}"));
    }
    @Test public void errorsDoNotEchoProviderResponsesOrKeys() {
        assertTrue(ExaClient.errorMessage(401).contains("API Key"));
        assertTrue(ExaClient.errorMessage(402).contains("额度"));assertTrue(ExaClient.errorMessage(429).contains("频繁"));
        assertTrue(ExaClient.errorMessage(302).contains("重定向"));
    }
    @Test public void httpTransportSendsUnicodeJsonAndKeyHeader() throws Exception {
        MockWebServer server=new MockWebServer();
        server.enqueue(new MockResponse().setBody("{\"results\":[]}"));server.start();
        try {
            ExaClient.Response response=new ExaClient().execute(server.url("/search").url(),"private-test-key",ExaClient.payload(config("论文","",""),"中文主题").toString());
            RecordedRequest sent=server.takeRequest();String body=sent.getBody().readUtf8();
            assertTrue(response.results.isEmpty());assertEquals("private-test-key",sent.getHeader("x-api-key"));assertTrue(body.contains("中文主题"));assertFalse(body.contains("private-test-key"));
        } finally {server.shutdown();}
    }
    @Test public void transportNeverFollowsRedirectWithCredentials() throws Exception {
        MockWebServer server=new MockWebServer();server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location","/capture"));
        server.enqueue(new MockResponse().setBody("{\"results\":[]}"));server.start();
        try {
            assertThrows(ExaClient.SearchException.class,()->new ExaClient().execute(server.url("/search").url(),"secret","{}"));
            assertEquals(1,server.getRequestCount());
        }finally{server.shutdown();}
    }
    @Test public void cancelledClientCannotStartARequest() {
        ExaClient client=new ExaClient();client.cancel();
        assertThrows(ExaClient.SearchException.class,()->client.execute(new URL("http://127.0.0.1:1"),"secret","{}"));
    }
}
