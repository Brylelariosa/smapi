package com.universalsmapi.core.diag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.junit.Test;

public class DiagnosticLogTest {
    @Test
    public void isBoundedAndCountsDroppedEntries() {
        DiagnosticLog log = new DiagnosticLog(3);
        for (int i = 0; i < 5; i++) {
            log.info("t", "msg " + i);
        }
        assertEquals(3, log.snapshot().size());
        assertEquals(2, log.droppedCount());
        assertEquals("msg 2", log.snapshot().get(0).message);
        assertTrue(log.render(true).contains("2 older entries dropped"));
    }

    @Test
    public void rendersExceptionChainsAndHidesTraceUnlessAsked() {
        DiagnosticLog log = new DiagnosticLog();
        log.trace("t", "noisy");
        log.error("probe", "open failed", new IOException("outer", new IllegalStateException("EACCES")));
        String text = log.render(false);
        assertFalse(text.contains("noisy"));
        assertTrue(text.contains("ERROR probe: open failed"));
        assertTrue(text.contains("java.io.IOException: outer"));
        assertTrue(text.contains("caused by: java.lang.IllegalStateException: EACCES"));
        assertTrue(log.render(true).contains("noisy"));
        assertTrue(log.hasErrors());
    }
}
