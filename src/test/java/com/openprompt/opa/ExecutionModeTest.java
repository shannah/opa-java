package com.openprompt.opa;

import org.junit.Test;

import static org.junit.Assert.*;

public class ExecutionModeTest {

    @Test
    public void testFromValue() {
        assertEquals(ExecutionMode.INTERACTIVE, ExecutionMode.fromValue("interactive"));
        assertEquals(ExecutionMode.BATCH, ExecutionMode.fromValue("batch"));
        assertEquals(ExecutionMode.AUTONOMOUS, ExecutionMode.fromValue("autonomous"));
    }

    @Test
    public void testFromValueCaseInsensitive() {
        assertEquals(ExecutionMode.BATCH, ExecutionMode.fromValue("BATCH"));
        assertEquals(ExecutionMode.BATCH, ExecutionMode.fromValue("Batch"));
    }

    @Test
    public void testNullDefaultsToInteractive() {
        assertEquals(ExecutionMode.INTERACTIVE, ExecutionMode.fromValue(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidValueThrows() {
        ExecutionMode.fromValue("invalid");
    }

    @Test
    public void testGetValue() {
        assertEquals("interactive", ExecutionMode.INTERACTIVE.getValue());
        assertEquals("batch", ExecutionMode.BATCH.getValue());
        assertEquals("autonomous", ExecutionMode.AUTONOMOUS.getValue());
    }
}
