package com.purplehillsbooks.md2latex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PreprocessorTest {

    @Test
    void extractsFrontMatterAndStripsIt() {
        MarkdownLoader.Result r =
                MarkdownLoader.process(
                        """
                ---
                sidebar_position: 3
                title: What is a Good Action?
                ---
                # Heading
                """);
        assertEquals(3, r.sidebarPosition());
        assertEquals("What is a Good Action?", r.title());
        assertTrue(r.body().startsWith("# Heading"));
    }

    @Test
    void toleratesIndentedFrontMatterKeys() {
        // The docs in this repo indent their front matter, which is not
        // strictly valid YAML but is common in hand-written files.
        MarkdownLoader.Result r =
                MarkdownLoader.process(
                        """
                ---
                  sidebar_position: 7
                ---
                text
                """);
        assertEquals(7, r.sidebarPosition());
    }

    @Test
    void missingPositionSortsLast() {
        MarkdownLoader.Result r = MarkdownLoader.process("no front matter here");
        assertEquals(Integer.MAX_VALUE, r.sidebarPosition());
        assertEquals("no front matter here\n", r.body());
    }

    @Test
    void rewritesAdmonitionWithTitle() {
        MarkdownLoader.Result r =
                MarkdownLoader.process(
                        """
                :::tip[Key Takeaway]

                Body text.

                :::
                """);
        assertTrue(r.body().contains("data-adm=\"begin\""));
        assertTrue(r.body().contains("data-kind=\"tip\""));
        assertTrue(r.body().contains("data-title=\"Key Takeaway\""));
        assertTrue(r.body().contains("data-adm=\"end\""));
        assertTrue(r.body().contains("Body text."));
    }

    @Test
    void rewritesAdmonitionWithoutTitle() {
        MarkdownLoader.Result r = MarkdownLoader.process(":::note\nhi\n:::\n");
        assertTrue(r.body().contains("data-kind=\"note\""));
        assertFalse(r.body().contains("data-title"));
    }

    @Test
    void leavesColonsInsideCodeFencesAlone() {
        MarkdownLoader.Result r =
                MarkdownLoader.process(
                        """
                ```
                :::tip
                :::
                ```
                """);
        assertFalse(r.body().contains("data-adm"));
        assertTrue(r.body().contains(":::tip"));
    }

    @Test
    void closesUnterminatedAdmonition() {
        MarkdownLoader.Result r = MarkdownLoader.process(":::warning\ndangling\n");
        assertTrue(r.body().contains("data-adm=\"begin\""));
        assertTrue(r.body().contains("data-adm=\"end\""));
    }
}
