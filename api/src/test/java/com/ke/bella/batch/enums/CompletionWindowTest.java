package com.ke.bella.batch.enums;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CompletionWindowTest {

    @Test
    public void testToSeconds_Minutes() {
        assertEquals(60, CompletionWindow.toSeconds("1m"));
        assertEquals(300, CompletionWindow.toSeconds("5m"));
        assertEquals(1800, CompletionWindow.toSeconds("30m"));
    }

    @Test
    public void testToSeconds_Hours() {
        assertEquals(3600, CompletionWindow.toSeconds("1h"));
        assertEquals(7200, CompletionWindow.toSeconds("2h"));
        assertEquals(86400, CompletionWindow.toSeconds("24h"));
    }

    @Test
    public void testToSeconds_Days() {
        assertEquals(86400, CompletionWindow.toSeconds("1d"));
        assertEquals(172800, CompletionWindow.toSeconds("2d"));
        assertEquals(604800, CompletionWindow.toSeconds("7d"));
    }
}
