package com.wikitagbankhighlighter;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class WikiBankToolsPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(WikiBankToolsPlugin.class);
        RuneLite.main(args);
    }
}
