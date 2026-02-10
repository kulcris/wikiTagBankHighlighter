package com.wikitagbankhighlighter;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class WikiBankToolsOverlay extends Overlay
{
    @Inject
    private Client client;

    @Inject
    private WikiBankToolsPlugin plugin;

    @Inject
    private WikiBankToolsConfig config;

    @Inject
    public WikiBankToolsOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.isHighlightEnabled())
        {
            return null;
        }

        Widget itemContainer = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
        if (itemContainer == null || itemContainer.isHidden())
        {
            return null;
        }

        Widget[] items = itemContainer.getChildren();
        if (items == null)
        {
            return null;
        }

        // Container viewport on-canvas
        Point cLoc = itemContainer.getCanvasLocation();
        if (cLoc == null)
        {
            return null;
        }

        int cX = cLoc.getX();
        int cY = cLoc.getY();
        int cW = itemContainer.getWidth();
        int cH = itemContainer.getHeight();
        if (cW <= 0 || cH <= 0)
        {
            return null;
        }

        Rectangle viewport = new Rectangle(cX, cY, cW, cH);

        // Scroll offsets (these change when you scroll the bank)
        int scrollX = itemContainer.getScrollX();
        int scrollY = itemContainer.getScrollY();

        for (Widget w : items)
        {
            if (w == null || w.isHidden())
            {
                continue;
            }

            int itemId = w.getItemId();
            if (itemId <= 0)
            {
                continue;
            }

            if (!plugin.shouldHighlight(itemId))
            {
                continue;
            }

            Rectangle r = toScrolledCanvasBounds(itemContainer, w, cX, cY, scrollX, scrollY);
            if (r == null)
            {
                continue;
            }

            // Skip anything not visible in the scrolled viewport
            if (!viewport.intersects(r))
            {
                continue;
            }

            OverlayUtilEx.drawOutline(graphics, r, config.outlineColor(), config.outlineThickness());
        }

        return null;
    }

    private Rectangle toScrolledCanvasBounds(Widget container, Widget child, int cX, int cY, int scrollX, int scrollY)
    {
        int w = child.getWidth();
        int h = child.getHeight();
        if (w <= 0 || h <= 0)
        {
            return null;
        }

        // Child position relative to the container's content, adjusted by scroll
        int x = cX + child.getRelativeX() - scrollX;
        int y = cY + child.getRelativeY() - scrollY;

        return new Rectangle(x, y, w, h);
    }
}
