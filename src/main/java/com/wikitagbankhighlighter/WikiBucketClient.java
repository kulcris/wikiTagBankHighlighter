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
    private static final String WIKI_BUCKET_QUERY_FORMAT =
            "https://oldschool.runescape.wiki/api.php?action=bucket&query=%s.limit(2000).run()&format=json";

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
        final String safe = escapeBucketString(normalizeForBucket(category));
        final String query = String.format("bucket('item_id').select('item_id.id').where('Category:%s')", safe);
        final String url = String.format(WIKI_BUCKET_QUERY_FORMAT, urlEncode(query));

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
                    // Older Gson compatibility
                    JsonParser parser = new JsonParser();
                    JsonObject root = parser.parse(json).getAsJsonObject();

                    JsonArray bucket = root.has("bucket") && root.get("bucket").isJsonArray()
                            ? root.getAsJsonArray("bucket")
                            : null;

                    if (bucket == null)
                    {
                        callback.accept(new int[0], null);
                        return;
                    }

                    Set<Integer> ids = new HashSet<>(1024);
                    for (JsonElement el : bucket)
                    {
                        collectInts(el, ids);
                    }

                    int[] out = ids.stream().mapToInt(i -> i).toArray();
                    callback.accept(out, null);
                }
                catch (Throwable t)
                {
                    callback.accept(new int[0], t);
                }
            }
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

    private static void collectInts(JsonElement el, Set<Integer> out)
    {
        if (el == null || el.isJsonNull())
        {
            return;
        }

        if (el.isJsonPrimitive())
        {
            String s = el.getAsString().trim();
            if (isDigits(s))
            {
                try
                {
                    int v = Integer.parseInt(s);
                    if (v > 0 && v < 50000)
                    {
                        out.add(v);
                    }
                }
                catch (NumberFormatException ignored)
                {
                }
            }
            return;
        }

        if (el.isJsonArray())
        {
            for (JsonElement a : el.getAsJsonArray())
            {
                collectInts(a, out);
            }
            return;
        }

        if (el.isJsonObject())
        {
            for (String k : el.getAsJsonObject().keySet())
            {
                collectInts(el.getAsJsonObject().get(k), out);
            }
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
