package com.openprompt.opa;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class JsonTest {

    @Test
    public void testParseString() {
        assertEquals("hello", Json.parse("\"hello\""));
    }

    @Test
    public void testParseStringWithEscapes() {
        assertEquals("line1\nline2", Json.parse("\"line1\\nline2\""));
        assertEquals("tab\there", Json.parse("\"tab\\there\""));
        assertEquals("quote\"here", Json.parse("\"quote\\\"here\""));
    }

    @Test
    public void testParseNumber() {
        assertEquals(42, Json.parse("42"));
        assertEquals(-7, Json.parse("-7"));
        assertEquals(3.14, (double) Json.parse("3.14"), 0.001);
    }

    @Test
    public void testParseBoolean() {
        assertEquals(Boolean.TRUE, Json.parse("true"));
        assertEquals(Boolean.FALSE, Json.parse("false"));
    }

    @Test
    public void testParseNull() {
        assertNull(Json.parse("null"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParseObject() {
        Map<String, Object> result = (Map<String, Object>) Json.parse("{\"name\": \"test\", \"count\": 5}");
        assertEquals("test", result.get("name"));
        assertEquals(5, result.get("count"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParseArray() {
        List<Object> result = (List<Object>) Json.parse("[1, 2, 3]");
        assertEquals(Arrays.asList(1, 2, 3), result);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParseNested() {
        String json = "{\"messages\": [{\"role\": \"user\", \"content\": \"hello\"}]}";
        Map<String, Object> result = (Map<String, Object>) Json.parse(json);
        List<Object> messages = (List<Object>) result.get("messages");
        assertEquals(1, messages.size());
        Map<String, Object> msg = (Map<String, Object>) messages.get(0);
        assertEquals("user", msg.get("role"));
        assertEquals("hello", msg.get("content"));
    }

    @Test
    public void testWriteString() {
        assertEquals("\"hello\"", Json.write("hello"));
        assertEquals("\"line\\n\"", Json.write("line\n"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRoundTrip() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("name", "test");
        original.put("count", 42);
        original.put("active", true);
        original.put("tags", Arrays.asList("a", "b"));

        String json = Json.write(original);
        Map<String, Object> parsed = (Map<String, Object>) Json.parse(json);
        assertEquals("test", parsed.get("name"));
        assertEquals(42, parsed.get("count"));
        assertEquals(Boolean.TRUE, parsed.get("active"));
        assertEquals(Arrays.asList("a", "b"), parsed.get("tags"));
    }

    @Test
    public void testWriteEmptyCollections() {
        assertEquals("{}", Json.write(new LinkedHashMap<>()));
        assertEquals("[]", Json.write(new ArrayList<>()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParseEmptyObject() {
        Map<String, Object> result = (Map<String, Object>) Json.parse("{}");
        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParseEmptyArray() {
        List<Object> result = (List<Object>) Json.parse("[]");
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseUnicodeEscape() {
        assertEquals("\u00E9", Json.parse("\"\\u00E9\""));
    }
}
