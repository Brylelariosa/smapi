package com.universalsmapi.core.diag;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Bounded, thread-safe, structured event log. Every probe and failure in the pipeline writes here so a user can
 * export one text file that says exactly what was tried and what the OS answered.
 */
public final class DiagnosticLog {
    public enum Level { TRACE, INFO, WARN, ERROR }

    /** One log line. */
    public static final class Entry {
        public final long timeMillis;
        public final Level level;
        public final String tag;
        public final String message;
        /** Condensed exception chain, or null. */
        public final String throwable;

        Entry(long timeMillis, Level level, String tag, String message, String throwable) {
            this.timeMillis = timeMillis;
            this.level = level;
            this.tag = tag;
            this.message = message;
            this.throwable = throwable;
        }
    }

    private final int maxEntries;
    private final List<Entry> entries = new ArrayList<>();
    private int dropped;

    public DiagnosticLog() {
        this(5000);
    }

    public DiagnosticLog(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public synchronized void log(Level level, String tag, String message, Throwable t) {
        if (entries.size() >= maxEntries) {
            entries.remove(0);
            dropped++;
        }
        entries.add(new Entry(System.currentTimeMillis(), level, tag, message, t == null ? null : describe(t)));
    }

    public void trace(String tag, String message) {
        log(Level.TRACE, tag, message, null);
    }

    public void info(String tag, String message) {
        log(Level.INFO, tag, message, null);
    }

    public void warn(String tag, String message) {
        log(Level.WARN, tag, message, null);
    }

    public void warn(String tag, String message, Throwable t) {
        log(Level.WARN, tag, message, t);
    }

    public void error(String tag, String message, Throwable t) {
        log(Level.ERROR, tag, message, t);
    }

    public synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    public synchronized int droppedCount() {
        return dropped;
    }

    public synchronized boolean hasErrors() {
        for (Entry e : entries) {
            if (e.level == Level.ERROR) {
                return true;
            }
        }
        return false;
    }

    /** Render as text, one line per event (exception chains are indented under their line). */
    public String render(boolean includeTrace) {
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        StringBuilder sb = new StringBuilder();
        List<Entry> snap = snapshot();
        if (droppedCount() > 0) {
            sb.append("(").append(droppedCount()).append(" older entries dropped)\n");
        }
        for (Entry e : snap) {
            if (e.level == Level.TRACE && !includeTrace) {
                continue;
            }
            sb.append(fmt.format(new Date(e.timeMillis))).append(' ')
                    .append(pad(e.level.name(), 5)).append(' ')
                    .append(e.tag).append(": ").append(e.message).append('\n');
            if (e.throwable != null) {
                for (String line : e.throwable.split("\n")) {
                    sb.append("      ").append(line).append('\n');
                }
            }
        }
        return sb.toString();
    }

    /** Exception class, message and a few frames, following up to three causes. */
    public static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 3) {
            if (depth > 0) {
                sb.append("caused by: ");
            }
            sb.append(cur.getClass().getName());
            if (cur.getMessage() != null) {
                sb.append(": ").append(cur.getMessage());
            }
            sb.append('\n');
            StackTraceElement[] st = cur.getStackTrace();
            for (int i = 0; i < Math.min(6, st.length); i++) {
                sb.append("  at ").append(st[i]).append('\n');
            }
            cur = cur.getCause() == cur ? null : cur.getCause();
            depth++;
        }
        return sb.toString().trim();
    }

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
