package com.rylinaux.plugman.util;

import core.com.rylinaux.plugman.util.reflection.ClassAccessor;
import core.com.rylinaux.plugman.util.reflection.FieldAccessor;
import core.com.rylinaux.plugman.util.reflection.MethodAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit test class to verify FieldAccessor and MethodAccessor functionality
 */
public class AccessorTest {

    @BeforeEach
    void setUp() {
        // Clear caches before each test to ensure clean state
        ClassAccessor.clearCache();
        FieldAccessor.clearCache();
        MethodAccessor.clearCache();
    }

    @Test
    @DisplayName("Test FieldAccessor functionality")
    void testFieldAccessor() throws Exception {
        System.out.println("[DEBUG_LOG] Testing FieldAccessor...");
        var instance = new TestClass();

        // Test getting private field value
        var value = FieldAccessor.getValue(TestClass.class, "privateField", instance);
        assertEquals("test_value", value, "Expected 'test_value'");

        // Test getting static field value
        var staticValue = FieldAccessor.getValue(TestClass.class, "staticField", null);
        assertEquals("static_value", staticValue, "Expected 'static_value'");

        // Test setting private field value
        FieldAccessor.setValue(TestClass.class, "privateField", instance, "new_value");
        var newValue = FieldAccessor.getValue(TestClass.class, "privateField", instance);
        assertEquals("new_value", newValue, "Expected 'new_value'");

        // Test non-existent field
        var nullValue = FieldAccessor.getValue(TestClass.class, "nonExistentField", instance);
        assertNull(nullValue, "Expected null for non-existent field");

        System.out.println("[DEBUG_LOG] FieldAccessor tests passed!");
    }

    @Test
    @DisplayName("Test MethodAccessor functionality")
    void testMethodAccessor() throws Exception {
        System.out.println("[DEBUG_LOG] Testing MethodAccessor...");
        var instance = new TestClass();

        // Test invoking private method with no parameters
        var result = MethodAccessor.invoke(TestClass.class, "getPrivateField", instance);
        assertEquals("test_value", result, "Expected 'test_value'");

        // Test invoking private method with parameters
        MethodAccessor.invoke(TestClass.class, "setPrivateField", instance, new Class<?>[]{String.class}, "method_test");
        var newValue = MethodAccessor.invoke(TestClass.class, "getPrivateField", instance);
        assertEquals("method_test", newValue, "Expected 'method_test'");

        // Test invoking static method
        var staticResult = MethodAccessor.invoke(TestClass.class, "getStaticField", null);
        assertEquals("static_value", staticResult, "Expected 'static_value'");

        // Test findMethodByName
        var method = MethodAccessor.findMethodByName(TestClass.class, "getPrivateField");
        assertNotNull(method, "Expected method to be found");
        assertEquals("getPrivateField", method.getName(), "Expected method name 'getPrivateField'");

        System.out.println("[DEBUG_LOG] MethodAccessor tests passed!");
    }

    @Test
    @DisplayName("Test caching functionality")
    void testCaching() throws Exception {
        System.out.println("[DEBUG_LOG] Testing caching functionality...");
        var instance1 = new TestClass();
        var instance2 = new TestClass();

        // Clear caches first
        FieldAccessor.clearCache();
        MethodAccessor.clearCache();

        // Test field caching
        var value1 = FieldAccessor.getValue(TestClass.class, "privateField", instance1);
        var value2 = FieldAccessor.getValue(TestClass.class, "privateField", instance2);
        assertTrue(FieldAccessor.getCacheSize() > 0, "Field cache should have entries");

        // Test method caching
        var result1 = MethodAccessor.invoke(TestClass.class, "getPrivateField", instance1);
        var result2 = MethodAccessor.invoke(TestClass.class, "getPrivateField", instance2);
        assertTrue(MethodAccessor.getCacheSize() > 0, "Method cache should have entries");

        System.out.println("[DEBUG_LOG] Field cache size: " + FieldAccessor.getCacheSize());
        System.out.println("[DEBUG_LOG] Method cache size: " + MethodAccessor.getCacheSize());
        System.out.println("[DEBUG_LOG] Caching tests passed!");
    }

    @Test
    @DisplayName("Test ClassAccessor functionality")
    void testClassAccessor() throws Exception {
        System.out.println("[DEBUG_LOG] Testing ClassAccessor...");

        // Test getting existing class
        var stringClass = ClassAccessor.getClass("java.lang.String");
        assertNotNull(stringClass, "Expected String class to be found");
        assertEquals(String.class, stringClass, "Expected String.class");

        // Test getting non-existent class
        var nonExistentClass = ClassAccessor.getClass("com.nonexistent.Class");
        assertNull(nonExistentClass, "Expected null for non-existent class");

        // Test class existence checking
        assertTrue(ClassAccessor.classExists("java.lang.String"), "Expected String class to exist");
        assertFalse(ClassAccessor.classExists("com.nonexistent.Class"), "Expected non-existent class to not exist");

        // Test isAssignableFrom with class names
        assertTrue(ClassAccessor.assignableFrom("java.lang.Object", "java.lang.String"),
                "Expected String to be assignable from Object");
        assertFalse(ClassAccessor.assignableFrom("java.lang.String", "java.lang.Integer"),
                "Expected Integer to not be assignable from String");

        // Test isAssignableFrom with mixed class/string
        assertTrue(ClassAccessor.assignableFrom(Object.class, "java.lang.String"),
                "Expected String to be assignable from Object class");
        assertTrue(ClassAccessor.assignableFrom("java.lang.Object", String.class),
                "Expected String class to be assignable from Object");

        // Test newInstance with default constructor
        var stringBuilder = ClassAccessor.<StringBuilder>newInstance("java.lang.StringBuilder");
        assertNotNull(stringBuilder, "Expected StringBuilder instance to be created");
        assertInstanceOf(StringBuilder.class, stringBuilder, "Expected StringBuilder instance");

        // Test newInstance with parameterized constructor
        var stringWithParam = ClassAccessor.<String>newInstance("java.lang.String",
                new Class<?>[]{String.class}, "test");
        assertNotNull(stringWithParam, "Expected String instance to be created");
        assertEquals("test", stringWithParam, "Expected 'test' string");

        // Test utility methods
        assertEquals("String", ClassAccessor.getSimpleName("java.lang.String"), "Expected 'String' as simple name");
        assertEquals("java.lang", ClassAccessor.getPackageName("java.lang.String"), "Expected 'java.lang' as package name");

        System.out.println("[DEBUG_LOG] ClassAccessor tests passed!");
    }

    @Test
    @DisplayName("Test ClassAccessor caching")
    void testClassAccessorCaching() throws Exception {
        System.out.println("[DEBUG_LOG] Testing ClassAccessor caching...");

        // Clear cache first
        ClassAccessor.clearCache();
        assertEquals(0, ClassAccessor.getCacheSize(), "Expected empty cache");

        // Load some classes
        ClassAccessor.getClass("java.lang.String");
        ClassAccessor.getClass("java.lang.Integer");
        ClassAccessor.getClass("java.util.List");

        assertTrue(ClassAccessor.getCacheSize() >= 3, "Expected at least 3 cached classes");

        // Test that subsequent calls use cache (same reference)
        var class1 = ClassAccessor.getClass("java.lang.String");
        var class2 = ClassAccessor.getClass("java.lang.String");
        assertSame(class1, class2, "Expected same class reference from cache");

        System.out.println("[DEBUG_LOG] Class cache size: " + ClassAccessor.getCacheSize());
        System.out.println("[DEBUG_LOG] ClassAccessor caching tests passed!");
    }

    @Test
    @DisplayName("Test FieldAccessor getFirstFieldName caching")
    void testGetFirstFieldNameCaching() throws Exception {
        System.out.println("[DEBUG_LOG] Testing getFirstFieldName caching...");

        // Clear cache first
        FieldAccessor.clearCache();
        var initialCacheSize = FieldAccessor.getCacheSize();

        // Test finding first String field
        var firstStringField = FieldAccessor.getFirstFieldName(TestClass.class, String.class);
        assertEquals("staticField", firstStringField, "Expected 'staticField' as first String field");
        assertTrue(FieldAccessor.getCacheSize() > initialCacheSize, "Cache should have grown");

        // Test caching - second call should use cache
        var cacheAfterFirst = FieldAccessor.getCacheSize();
        var cachedStringField = FieldAccessor.getFirstFieldName(TestClass.class, String.class);
        assertEquals("staticField", cachedStringField, "Expected same result from cache");
        assertEquals(cacheAfterFirst, FieldAccessor.getCacheSize(), "Cache size should remain same for cached call");

        // Test non-existent field type
        var nonExistentField = FieldAccessor.getFirstFieldName(TestClass.class, Double.class);
        assertNull(nonExistentField, "Expected null for non-existent field type");

        System.out.println("[DEBUG_LOG] getFirstFieldName caching tests passed!");
    }

    // Test class for reflection testing
    private static class TestClass {
        private static String staticField = "static_value";
        private String privateField = "test_value";

        private static String getStaticField() {
            return staticField;
        }

        private String getPrivateField() {
            return privateField;
        }

        private void setPrivateField(String value) {
            privateField = value;
        }
    }
}