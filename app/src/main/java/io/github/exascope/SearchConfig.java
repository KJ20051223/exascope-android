package io.github.exascope;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class SearchConfig {
    public static final String DEFAULT_ENDPOINT = "https://api.exa.ai";
    public static final String DEFAULT_TEMPLATE = "{query}\n\n搜索范围：{scope}";
    public static final String[] PRESET_NAMES = {"全网", "文献 / 论文", "新闻报道", "技术文档", "自定义"};
    public static final String[] PRESET_SCOPES = {"", "学术文献、研究论文、预印本与期刊文章，优先原始研究来源", "相关新闻报道，优先可靠媒体与原始信息来源", "官方技术文档、开发者指南与开源项目说明", ""};
    public static final String[] PRESET_CATEGORIES = {"", "publication", "news", "", ""};
    public static final String[] CATEGORIES = {"", "publication", "news", "company", "personal site", "financial report", "people"};
    public static final String[] CATEGORY_LABELS = {"不限类别", "学术出版物 / 论文", "新闻", "公司", "个人网站", "财务报告", "人物"};

    public final String endpoint, apiKey, scope, template, category, domains, searchType;
    public final int resultCount;

    public SearchConfig(String endpoint, String apiKey, String scope, String template,
                        String category, String domains, String searchType, int resultCount) {
        this.endpoint = trim(endpoint);
        this.apiKey = trim(apiKey);
        this.scope = trim(scope);
        this.template = trim(template).isEmpty() ? DEFAULT_TEMPLATE : trim(template);
        this.category = trim(category);
        this.domains = trim(domains);
        this.searchType = trim(searchType).isEmpty() ? "auto" : trim(searchType);
        this.resultCount = resultCount;
    }

    public String effectiveQuery(String query) {
        String value = trim(query);
        if (value.isEmpty()) throw new IllegalArgumentException("请输入搜索内容");
        if (value.length() > 2000) throw new IllegalArgumentException("搜索内容不能超过 2000 个字符");
        if (scope.isEmpty()) return value;
        String resolved = template.replace("{query}", value).replace("{scope}", scope);
        if (!template.contains("{query}")) resolved = value + "\n\n" + resolved;
        if (!template.contains("{scope}")) resolved += "\n\n搜索范围：" + scope;
        return resolved;
    }

    public String searchUrl() {
        String value = endpoint.isEmpty() ? DEFAULT_ENDPOINT : endpoint;
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
            while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
            return value.endsWith("/search") ? value : value + "/search";
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("API 地址须为有效的 HTTPS 地址，不能包含账号、查询参数或片段");
        }
    }

    public List<String> includeDomains() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String item : domains.split("[,，;；\\s]+")) {
            if (item.trim().isEmpty()) continue;
            String host = item.toLowerCase(Locale.ROOT).replaceFirst("^https?://", "");
            while (host.endsWith("/")) host = host.substring(0, host.length() - 1);
            if (!host.matches("(?:\\*\\.)?[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?(?::[0-9]+)?(?:/[^?#\\s]*)?")
                    || !host.contains(".")) {
                throw new IllegalArgumentException("域名格式有误，请填写如 arxiv.org、pubmed.ncbi.nlm.nih.gov");
            }
            unique.add(host);
        }
        if (unique.size() > 30) throw new IllegalArgumentException("最多可限定 30 个域名");
        return new ArrayList<>(unique);
    }

    public void validate() {
        searchUrl();
        if (apiKey.length() > 2048 || apiKey.contains("\n") || apiKey.contains("\r")) {
            throw new IllegalArgumentException("API Key 格式有误");
        }
        if (scope.length() > 2000 || template.length() > 4000) throw new IllegalArgumentException("范围或模板过长");
        if (resultCount < 1 || resultCount > 100) throw new IllegalArgumentException("结果数量须为 1–100");
        if (!Arrays.asList("auto", "lite").contains(searchType)) throw new IllegalArgumentException("不支持的搜索模式");
        if (!Arrays.asList(CATEGORIES).contains(category)) throw new IllegalArgumentException("不支持的分类");
        includeDomains();
    }

    public String scopeLabel() { return scope.isEmpty() ? "全网搜索" : scope; }
    private static String trim(String text) { return text == null ? "" : text.trim(); }
}
