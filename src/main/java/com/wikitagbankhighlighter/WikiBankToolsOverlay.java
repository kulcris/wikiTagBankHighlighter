package com.wikitagbankhighlighter;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
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

        // Plugin Hub: avoid WidgetInfo/WidgetID, use ComponentID/InterfaceID
        Widget itemContainer = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
        if (itemContainer == null || itemContainer.isHidden())
        {
            return null; // bank not open
        }

        Widget[] items = itemContainer.getChildren();
        if (items == null)
        {
            return null;
        }

        // Container viewport (on-canvas)
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

            Rectangle r = toWidgetBounds(w);
            if (r == null)
            {
                continue;
            }

            // Don't draw outlines for rows that are currently scrolled out of view
            if (!viewport.intersects(r))
            {
                continue;
            }

            OverlayUtilEx.drawOutline(graphics, r, config.outlineColor(), config.outlineThickness());
        }

        return null;
    }

    private Rectangle toWidgetBounds(Widget child)
    {
        Rectangle bounds = child.getBounds();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
        {
            return null;
        }

        return bounds;
    }
}
