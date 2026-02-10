package com.wikitagbankhighlighter;

import com.google.inject.AbstractModule;

public class WikiBankToolsModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(WikiBankToolsOverlay.class);
    }
}
