package com.wikitagbankhighlighter;

import com.google.common.base.MoreObjects;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

import static net.runelite.client.plugins.banktags.BankTagsPlugin.CONFIG_GROUP;
import static net.runelite.client.plugins.banktags.BankTagsPlugin.TAG_ICON_PREFIX;
import static net.runelite.client.plugins.banktags.BankTagsPlugin.TAG_TABS_CONFIG;

@Slf4j
@PluginDescriptor(
        name = "Wiki Tag Bank Highlighter",
        description = "Highlight bank items or create Bank Tag Tabs from OSRS Wiki categories."
)
@PluginDependency(BankTagsPlugin.class)
public class WikiBankToolsPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private WikiBankToolsConfig config;
    @Inject private ConfigManager configManager;
    @Inject private TagManager tagManager;
    @Inject private ClientThread clientThread;
    @Inject private OkHttpClient httpClient;
    @Inject private Gson gson;

    @Inject private OverlayManager overlayManager;
    @Inject private WikiBankToolsOverlay overlay;

    @Inject private net.runelite.client.ui.ClientToolbar clientToolbar;

    private NavigationButton navButton;
    private WikiBankToolsPanel panel;

    private final ExecutorService exec = Executors.newSingleThreadExecutor(r ->
    {
        Thread t = new Thread(r, "wiki-bank-tools");
        t.setDaemon(true);
        return t;
    });

    private volatile int[] highlightIdsSortedDistinct = new int[0];
    private volatile boolean highlightEnabled = false;

    @Provides
    WikiBankToolsConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(WikiBankToolsConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);

        panel = new WikiBankToolsPanel(this);

        navButton = NavigationButton.builder()
                .tooltip("Wiki Bank Tools")
                .icon(ImageUtil.loadImageResource(getClass(), "/wiki_bank_tools_icon.png"))
                .priority(6)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown()
    {
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        panel = null;

        overlayManager.remove(overlay);

        highlightEnabled = false;
        highlightIdsSortedDistinct = new int[0];

        exec.shutdownNow();
    }

    // Called by the side panel
    public void highlightCategory(String categoryInput)
    {
        final String category = normalizeCategoryInput(categoryInput);
        if (category.isEmpty())
        {
            postChat("Category is empty.");
            return;
        }

        postChat("Fetching IDs for category '" + category + "'...");
        setPanelBusy(true, "Fetching…");

        WikiBucketClient.fetchCategoryItemIdsAsync(httpClient, category, (ids, err) ->
        {
            final int[] sortedDistinct = java.util.Arrays.stream(ids).distinct().sorted().toArray();

            clientThread.invokeLater(() ->
            {
                if (err != null)
                {
                    postChat("Fetch failed for '" + category + "': " + err.getMessage());
                }

                highlightIdsSortedDistinct = sortedDistinct;
                highlightEnabled = true;

                if (sortedDistinct.length == 0)
                {
                    postChat("No items found for '" + category + "'.");
                }
                else
                {
                    postChat("Highlighting " + sortedDistinct.length + " items for '" + category + "'.");
                }
            });

            setPanelBusy(false, "");
        });
    }

    // Called by the side panel
    public void createBankTagTab(String categoryInput)
    {
        final String category = normalizeCategoryInput(categoryInput);
        if (category.isEmpty())
        {
            postChat("Category is empty.");
            return;
        }

        postChat("Generating Bank Tag Tab for '" + category + "'...");
        setPanelBusy(true, "Fetching…");

        WikiBucketClient.fetchCategoryItemIdsAsync(httpClient, category, (ids, err) ->
        {
            final int[] distinct = java.util.Arrays.stream(ids).distinct().toArray();

            clientThread.invokeLater(() ->
            {
                if (err != null)
                {
                    postChat("Fetch failed for '" + category + "': " + err.getMessage());
                }

                if (distinct.length == 0)
                {
                    postChat("No items found for '" + category + "'.");
                    return;
                }

                for (int itemId : distinct)
                {
                    tagManager.addTag(itemId, category, false);
                }

                createTabIfMissing(category, distinct[0]);
                postChat("Added tag/tab '" + category + "' to " + distinct.length + " items.");
            });

            setPanelBusy(false, "");
        });
    }


    // Called by the side panel
    public void clearHighlight()
    {
        highlightEnabled = false;
        highlightIdsSortedDistinct = new int[0];
        postChat("Highlight cleared.");
    }

    public boolean isHighlightEnabled()
    {
        return highlightEnabled;
    }

    public boolean shouldHighlight(int itemId)
    {
        if (!highlightEnabled || itemId <= 0)
        {
            return false;
        }
        return Arrays.binarySearch(highlightIdsSortedDistinct, itemId) >= 0;
    }

    private void createTabIfMissing(String tag, int iconItemId)
    {
        List<String> tabs = new ArrayList<>(getAllTabs());

        final String standardized = Text.standardize(tag);
        boolean exists = tabs.stream().map(Text::standardize).anyMatch(t -> t.equals(standardized));

        if (!exists)
        {
            tabs.add(standardized);
            configManager.setConfiguration(CONFIG_GROUP, TAG_TABS_CONFIG, Text.toCSV(tabs));
        }

        configManager.setConfiguration(CONFIG_GROUP, TAG_ICON_PREFIX + standardized, iconItemId);
    }

    private List<String> getAllTabs()
    {
        return Text.fromCSV(MoreObjects.firstNonNull(configManager.getConfiguration(CONFIG_GROUP, TAG_TABS_CONFIG), ""));
    }

    private void postChat(String msg)
    {
        final String full = "Wiki Bank Tools: " + msg;
        clientThread.invokeLater(() ->
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", full, "")
        );
    }

    private void setPanelBusy(boolean busy, String status)
    {
        if (panel == null)
        {
            return;
        }

        javax.swing.SwingUtilities.invokeLater(() -> panel.setBusy(busy, status));
    }

    private static String normalizeCategoryInput(String in)
    {
        if (in == null)
        {
            return "";
        }
        String s = in.trim();
        if (s.regionMatches(true, 0, "Category:", 0, "Category:".length()))
        {
            s = s.substring("Category:".length()).trim();
        }
        return s;
    }
}
