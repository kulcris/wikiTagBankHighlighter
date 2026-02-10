package com.wikitagbankhighlighter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class WikiCategoryService
{
    private static final String API_BASE = "https://oldschool.runescape.wiki/api.php";

    private final OkHttpClient okHttpClient;

    private Duration defaultTtl = Duration.ofHours(24);

    private final HashMap<Integer, CacheEntry> cache = new HashMap<>();

    @Inject
    public WikiCategoryService(OkHttpClient okHttpClient)
    {
        this.okHttpClient = okHttpClient;
    }

    public void setDefaultTtl(Duration ttl)
    {
        this.defaultTtl = ttl == null ? Duration.ofHours(24) : ttl;
    }

    public void clearCache()
    {
        cache.clear();
    }

    public Set<String> getCategoriesCached(int itemId, Supplier<Set<String>> fetcher)
    {
        CacheEntry e = cache.get(itemId);
        if (e != null && !e.isExpired())
        {
            return e.categories;
        }

        try
        {
            Set<String> cats = fetcher.get();
            if (cats == null)
            {
                cats = Collections.emptySet();
            }
            cache.put(itemId, new CacheEntry(cats, Instant.now().plus(defaultTtl)));
            return cats;
        }
        catch (Exception ex)
        {
            // Don’t spam logs; just cache empty briefly
            log.debug("Wiki category fetch failed for itemId={}", itemId, ex);
            cache.put(itemId, new CacheEntry(Collections.emptySet(), Instant.now().plus(Duration.ofMinutes(30))));
            return Collections.emptySet();
        }
    }

    public Set<String> fetchCategoriesForTitle(String title) throws IOException
    {
        if (title == null || title.trim().isEmpty())
        {
            return Collections.emptySet();
        }

        // MediaWiki action=query&prop=categories&titles=<TITLE>&cllimit=max&format=json&formatversion=2
        HttpUrl url = HttpUrl.parse(API_BASE).newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("prop", "categories")
                .addQueryParameter("titles", title)
                .addQueryParameter("cllimit", "max")
                .addQueryParameter("format", "json")
                .addQueryParameter("formatversion", "2")
                .build();

        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "RuneLite Wiki Bank Tag Highlighter (unofficial)")
                .get()
                .build();

        try (Response resp = okHttpClient.newCall(req).execute())
        {
            if (!resp.isSuccessful() || resp.body() == null)
            {
                return Collections.emptySet();
            }

            String body = resp.body().string();
            JsonParser parser = new JsonParser();
            JsonObject root = parser.parse(body).getAsJsonObject();
            JsonObject query = root.has("query") ? root.getAsJsonObject("query") : null;
            if (query == null)
            {
                return Collections.emptySet();
            }

            JsonArray pages = query.has("pages") ? query.getAsJsonArray("pages") : null;
            if (pages == null || pages.size() == 0)
            {
                return Collections.emptySet();
            }

            JsonObject page0 = pages.get(0).getAsJsonObject();
            JsonArray categories = page0.has("categories") ? page0.getAsJsonArray("categories") : null;
            if (categories == null)
            {
                return Collections.emptySet();
            }

            Set<String> out = new HashSet<>();
            for (JsonElement el : categories)
            {
                JsonObject o = el.getAsJsonObject();
                if (o.has("title"))
                {
                    out.add(o.get("title").getAsString()); // e.g. "Category:Herbs"
                }
            }
            return out;
        }
    }

    private static class CacheEntry
    {
        private final Set<String> categories;
        private final Instant expiresAt;

        private CacheEntry(Set<String> categories, Instant expiresAt)
        {
            this.categories = categories;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired()
        {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
    }
}
