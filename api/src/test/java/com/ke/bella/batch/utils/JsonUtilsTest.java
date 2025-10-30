package com.ke.bella.batch.utils;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class JsonUtilsTest {

    @Test
    public void testToJson_SimpleObject() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "test");
        map.put("value", 42);

        String json = JsonUtils.toJson(map);

        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"test\""));
        assertTrue(json.contains("\"value\":42"));
    }

    @Test
    public void testFromJson_SimpleMap() {
        String json = "{\"name\":\"test\",\"value\":42}";

        @SuppressWarnings("unchecked")
        Map<String, Object> result = JsonUtils.fromJson(json, Map.class);

        assertNotNull(result);
        assertEquals("test", result.get("name"));
        assertEquals(42, result.get("value"));
    }

    @Test
    public void testFromJson_WithBigDecimal() {
        String json = "{\"price\":123.45}";

        @SuppressWarnings("unchecked")
        Map<String, Object> result = JsonUtils.fromJson(json, Map.class);

        assertNotNull(result);
        assertTrue(result.get("price") instanceof BigDecimal);
        assertEquals(new BigDecimal("123.45"), result.get("price"));
    }

    @Test
    public void testFromJson_WithSingleQuotes() {
        String json = "{'name':'test','value':42}";

        @SuppressWarnings("unchecked")
        Map<String, Object> result = JsonUtils.fromJson(json, Map.class);

        assertNotNull(result);
        assertEquals("test", result.get("name"));
        assertEquals(42, result.get("value"));
    }

    @Test
    public void testFromJson_IgnoreUnknownProperties() {
        String json = "{\"name\":\"test\",\"unknownField\":\"ignored\"}";

        TestPojo result = JsonUtils.fromJson(json, TestPojo.class);

        assertNotNull(result);
        assertEquals("test", result.name);
    }

    @Test
    public void testToJson_ExcludeNullValues() {
        TestPojo obj = new TestPojo();
        obj.name = "test";
        obj.value = null;

        String json = JsonUtils.toJson(obj);

        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"test\""));
        assertFalse(json.contains("value"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromJson_InvalidJson() {
        JsonUtils.fromJson("{invalid json", Map.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToJson_SerializationError() {
        Object obj = new Object() {
            public Object getValue() {
                throw new RuntimeException("Serialization error");
            }
        };
        JsonUtils.toJson(obj);
    }

    public static class TestPojo {
        public String name;
        public String value;
    }
}
