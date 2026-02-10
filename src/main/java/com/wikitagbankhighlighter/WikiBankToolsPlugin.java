package com.wikitagbankhighlighter;

import com.google.common.base.MoreObjects;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

import static net.runelite.client.plugins.banktags.BankTagsPlugin.CONFIG_GROUP;
import static net.runelite.client.plugins.banktags.BankTagsPlugin.TAG_ICON_PREFIX;
import static net.runelite.client.plugins.banktags.BankTagsPlugin.TAG_TABS_CONFIG;

@Slf4j
@PluginDescriptor(
        name = "Wiki Tag Bank Highlighter",
        description = "Highlight bank items or create Bank Tag tabs from OSRS Wiki categories."
)
@PluginDependency(BankTagsPlugin.class)
public class WikiBankToolsPlugin extends Plugin
{
    private static final String ICON_PATH = "/wiki_bank_tools_icon.png";

    @Inject private Client client;
    @Inject private ClientThread clientThread;

    @Inject private WikiBankToolsConfig config;
    @Inject private ConfigManager configManager;

    @Inject private OkHttpClient httpClient;

    @Inject private TagManager tagManager;
    @Inject private ItemManager itemManager;

    @Inject private OverlayManager overlayManager;
    @Inject private WikiBankToolsOverlay overlay;

    @Inject private ClientToolbar clientToolbar;

    private NavigationButton navButton;
    private WikiBankToolsPanel panel;

    // Highlight state
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

        BufferedImage icon = loadIcon(ICON_PATH);
        if (icon == null)
        {
            // Fallback: 1x1 transparent so older builders that require an icon won't crash
            icon = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        navButton = NavigationButton.builder()
                .tooltip("Wiki Bank Tools")
                .icon(icon)
                .priority(6)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown()
    {
        highlightEnabled = false;
        highlightIdsSortedDistinct = new int[0];

        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }

        panel = null;

        overlayManager.remove(overlay);
    }

    /**
     * Side panel action: fetch IDs for category and enable highlighting.
     */
    public void highlightCategory(String categoryInput)
    {
        final String category = normalizeCategoryInput(categoryInput);
        if (category.isEmpty())
        {
            postChat("Category is empty.");
            return;
        }

        setPanelBusy(true, "Fetching…");
        postChat("Fetching IDs for category '" + category + "'...");

        WikiBucketClient.fetchCategoryItemIdsAsync(httpClient, category, (ids, err) ->
        {
            final int[] sortedDistinct = Arrays.stream(ids).distinct().sorted().toArray();

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

    /**
     * Side panel action: fetch IDs for category and create a Bank Tag tab + apply tags.
     */
    public void createBankTagTab(String categoryInput)
    {
        final String category = normalizeCategoryInput(categoryInput);
        if (category.isEmpty())
        {
            postChat("Category is empty.");
            return;
        }

        setPanelBusy(true, "Fetching…");
        postChat("Generating Bank Tag Tab for '" + category + "'...");

        WikiBucketClient.fetchCategoryItemIdsAsync(httpClient, category, (ids, err) ->
        {
            final int[] distinct = Arrays.stream(ids).distinct().toArray();

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

    /**
     * Side panel action: clear highlights.
     */
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

    /**
     * Used by overlay. Canonicalizes bank item IDs (noted/unnoted) before matching.
     */
    public boolean shouldHighlight(int itemId)
    {
        if (!highlightEnabled || itemId <= 0)
        {
            return false;
        }

        int canonical = itemManager.canonicalize(itemId);
        return Arrays.binarySearch(highlightIdsSortedDistinct, canonical) >= 0;
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
        return Text.fromCSV(MoreObjects.firstNonNull(
                configManager.getConfiguration(CONFIG_GROUP, TAG_TABS_CONFIG),
                ""
        ));
    }

    /**
     * Must be called on client thread, so always marshal via clientThread.
     */
    private void postChat(String msg)
    {
        final String full = "Wiki Bank Tools: " + msg;
        clientThread.invokeLater(() ->
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", full, "")
        );
    }

    /**
     * UI updates must be on Swing EDT.
     */
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

    /**
     * PluginHub-safe resource loading:
     * Use getResourceAsStream (jar-safe) rather than getResource (URL changes between IDE/jar).
     */
    private static BufferedImage loadIcon(String path)
    {
        try (InputStream in = WikiBankToolsPlugin.class.getResourceAsStream(path))
        {
            if (in == null)
            {
                return null;
            }
            return ImageIO.read(in);
        }
        catch (IOException e)
        {
            return null;
        }
    }
}
