package com.wikitagbankhighlighter;

import java.awt.Color;
import net.runelite.client.config.*;

@ConfigGroup("wikiBankTools")
public interface WikiBankToolsConfig extends Config
{
    @Alpha
    @ConfigItem(
            keyName = "outlineColor",
            name = "Outline color",
            description = "Outline color for highlighted items."
    )
    default Color outlineColor()
    {
        return Color.YELLOW;
    }

    @Range(min = 1, max = 6)
    @ConfigItem(
            keyName = "outlineThickness",
            name = "Outline thickness",
            description = "Thickness of outline in pixels."
    )
    default int outlineThickness()
    {
        return 2;
    }
    @ConfigItem(
            keyName = "debugDrawGridBox",
            name = "Debug: draw grid box",
            description = "Draws a box around the detected bank item grid.",
            position = 10
    )
    default boolean debugDrawGridBox()
    {
        return false;
    }


}
