package com.wikitagbankhighlighter;

import lombok.experimental.UtilityClass;

@UtilityClass
public class WikiTagUtil
{
    public static String normalizeCategory(String userInput)
    {
        String s = userInput == null ? "" : userInput.trim();
        if (s.isEmpty())
        {
            return s;
        }

        // OSRS wiki categories come back like "Category:Herbs"
        if (!s.regionMatches(true, 0, "Category:", 0, "Category:".length()))
        {
            s = "Category:" + s;
        }

        // Keep canonical "Category:" casing
        if (!s.startsWith("Category:"))
        {
            s = "Category:" + s.substring("Category:".length());
        }

        return s;
    }
}

