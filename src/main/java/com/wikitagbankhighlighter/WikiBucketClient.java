package com.wikitagbankhighlighter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WikiBucketClient
{
    private static final int PAGE_SIZE = 2000;
    private static final String CATEGORY_PREFIX = "Category:";
    private static final String WIKI_BUCKET_QUERY_FORMAT =
            "https://oldschool.runescape.wiki/api.php?action=bucket&query=%s&format=json";
    private static final String WIKI_SEARCH_URL_FORMAT =
            "https://oldschool.runescape.wiki/api.php?action=query&list=search&srnamespace=14&srlimit=1&format=json&formatversion=2&srsearch=%s";

    /**
     * Async: fetch item ids for a wiki category using OSRS Wiki bucket endpoint.
     *
     * callback.accept(ids, error)
     * - ids is never null (empty array on failure)
     * - error is null on success
     */
    public static void fetchCategoryItemIdsAsync(
            OkHttpClient http,
            String category,
            BiConsumer<int[], Throwable> callback
    )
    {
        final String normalized = normalizeForBucket(category);
        resolveCategoryNameAsync(http, normalized, (resolvedCategory, err) ->
        {
            if (err != null)
            {
                callback.accept(new int[0], err);
                return;
            }

            final String safe = escapeBucketString(resolvedCategory);
            final String baseQuery = String.format("bucket('item_id').select('item_id.id').where('%s%s')", CATEGORY_PREFIX, safe);
            fetchCategoryPageAsync(http, baseQuery, 0, new HashSet<>(1024), callback);
        });
    }

    public static void fetchCategoryItemIdsAsync(
            OkHttpClient http,
            List<String> categories,
            BiConsumer<int[], Throwable> callback
    )
    {
        List<String> normalized = normalizeCategories(categories);
        if (normalized.isEmpty())
        {
            callback.accept(new int[0], null);
            return;
        }

        if (normalized.size() == 1)
        {
            fetchCategoryItemIdsAsync(http, normalized.get(0), callback);
            return;
        }

        Set<Integer> merged = Collections.synchronizedSet(new HashSet<>(1024));
        AtomicInteger remaining = new AtomicInteger(normalized.size());
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        for (String category : normalized)
        {
            fetchCategoryItemIdsAsync(http, category, (ids, err) ->
            {
                if (err != null)
                {
                    firstError.compareAndSet(null, err);
                }

                for (int id : ids)
                {
                    if (id > 0)
                    {
                        merged.add(id);
                    }
                }

                if (remaining.decrementAndGet() == 0)
                {
                    int[] out = merged.stream().mapToInt(i -> i).toArray();
                    callback.accept(out, firstError.get());
                }
            });
        }
    }

    private static void fetchCategoryPageAsync(
            OkHttpClient http,
            String baseQuery,
            int offset,
            Set<Integer> out,
            BiConsumer<int[], Throwable> callback
    )
    {
        String pagedQuery = String.format("%s.limit(%d).offset(%d).run()", baseQuery, PAGE_SIZE, offset);
        String url = String.format(WIKI_BUCKET_QUERY_FORMAT, urlEncode(pagedQuery));
        Request req = new Request.Builder().url(url).build();

        http.newCall(req).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                callback.accept(new int[0], e);
            }

            @Override
            public void onResponse(Call call, Response resp) throws IOException
            {
                if (!resp.isSuccessful() || resp.body() == null)
                {
                    callback.accept(new int[0], new IOException("HTTP " + resp.code()));
                    return;
                }

                String json = resp.body().string();

                try
                {
                    JsonParser parser = new JsonParser();
                    JsonObject root = parser.parse(json).getAsJsonObject();
                    JsonArray bucket = root.has("bucket") && root.get("bucket").isJsonArray()
                            ? root.getAsJsonArray("bucket")
                            : null;

                    if (bucket == null || bucket.size() == 0)
                    {
                        callback.accept(out.stream().mapToInt(i -> i).toArray(), null);
                        return;
                    }

                    for (JsonElement el : bucket)
                    {
                        collectItemIds(el, out);
                    }

                    if (bucket.size() < PAGE_SIZE)
                    {
                        callback.accept(out.stream().mapToInt(i -> i).toArray(), null);
                        return;
                    }

                    fetchCategoryPageAsync(http, baseQuery, offset + PAGE_SIZE, out, callback);
                }
                catch (Throwable t)
                {
                    callback.accept(new int[0], t);
                }
            }
        });
    }

    private static void resolveCategoryNameAsync(
            OkHttpClient http,
            String category,
            BiConsumer<String, Throwable> callback
    )
    {
        if (category == null || category.isEmpty())
        {
            callback.accept("", null);
            return;
        }

        String query = String.format("\"%s\"", category);
        String url = String.format(WIKI_SEARCH_URL_FORMAT, urlEncode(query));
        Request req = new Request.Builder().url(url).build();

        http.newCall(req).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                callback.accept(category, null);
            }

            @Override
            public void onResponse(Call call, Response resp) throws IOException
            {
                if (!resp.isSuccessful() || resp.body() == null)
                {
                    callback.accept(category, null);
                    return;
                }

                try
                {
                    String json = resp.body().string();
                    JsonParser parser = new JsonParser();
                    JsonObject root = parser.parse(json).getAsJsonObject();
                    JsonObject queryObject = root.has("query") && root.get("query").isJsonObject()
                            ? root.getAsJsonObject("query")
                            : null;
                    JsonArray search = queryObject != null && queryObject.has("search") && queryObject.get("search").isJsonArray()
                            ? queryObject.getAsJsonArray("search")
                            : null;

                    if (search == null || search.size() == 0)
                    {
                        callback.accept(category, null);
                        return;
                    }

                    JsonElement first = search.get(0);
                    if (!first.isJsonObject())
                    {
                        callback.accept(category, null);
                        return;
                    }

                    JsonObject result = first.getAsJsonObject();
                    JsonElement titleElement = result.get("title");
                    if (titleElement == null || !titleElement.isJsonPrimitive())
                    {
                        callback.accept(category, null);
                        return;
                    }

                    String title = titleElement.getAsString();
                    if (title.regionMatches(true, 0, CATEGORY_PREFIX, 0, CATEGORY_PREFIX.length()))
                    {
                        callback.accept(title.substring(CATEGORY_PREFIX.length()), null);
                        return;
                    }

                    callback.accept(category, null);
                }
                catch (Throwable t)
                {
                    callback.accept(category, null);
                }
            }
        });
    }

    private static void collectItemIds(JsonElement el, Set<Integer> out)
    {
        if (el == null || !el.isJsonObject())
        {
            return;
        }

        JsonObject obj = el.getAsJsonObject();
        JsonElement idsElement = obj.get("item_id.id");
        if (idsElement == null || idsElement.isJsonNull())
        {
            return;
        }

        if (idsElement.isJsonArray())
        {
            for (JsonElement idElement : idsElement.getAsJsonArray())
            {
                addItemId(idElement, out);
            }
            return;
        }

        addItemId(idsElement, out);
    }

    private static void addItemId(JsonElement el, Set<Integer> out)
    {
        if (el == null || !el.isJsonPrimitive())
        {
            return;
        }

        String s = el.getAsString().trim();
        if (!isDigits(s))
        {
            return;
        }

        try
        {
            int v = Integer.parseInt(s);
            if (v > 0)
            {
                out.add(v);
            }
        }
        catch (NumberFormatException ignored)
        {
        }
    }

    private static boolean isDigits(String s)
    {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static String normalizeForBucket(String subject)
    {
        return (subject == null ? "" : subject.replace("_", " ").trim());
    }

    private static List<String> normalizeCategories(List<String> categories)
    {
        if (categories == null || categories.isEmpty())
        {
            return new ArrayList<>();
        }

        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String category : categories)
        {
            String normalized = normalizeForBucket(category);
            if (!normalized.isEmpty())
            {
                out.add(normalized);
            }
        }
        return new ArrayList<>(out);
    }

    private static String escapeBucketString(String s)
    {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String urlEncode(String s)
    {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
