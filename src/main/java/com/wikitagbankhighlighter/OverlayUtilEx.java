package com.wikitagbankhighlighter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public final class OverlayUtilEx
{
    private OverlayUtilEx() {}

    public static void drawOutline(Graphics2D g, Rectangle r, Color color, int thickness)
    {
        Color old = g.getColor();
        java.awt.Stroke oldStroke = g.getStroke();

        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(1, thickness)));
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);

        g.setStroke(oldStroke);
        g.setColor(old);
    }
}
