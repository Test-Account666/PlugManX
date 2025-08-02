package core.com.rylinaux.plugman.util.reflection;

import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A utility class for cached field access using reflection.
 * This class provides thread-safe caching of Field objects to improve performance
 * when repeatedly accessing the same fields.
 */
@UtilityClass
public class FieldAccessor {

    private static final Map<String, Field> fieldCache = new ConcurrentHashMap<>();
    private static final Map<String, String> firstFieldNameCache = new ConcurrentHashMap<>();

    /**
     * Gets a field from the specified class with caching.
     * The field is automatically made accessible.
     *
     * @param clazz     The class containing the field
     * @param fieldName The name of the field
     * @return The Field object, or null if not found
     */
    public static Field getField(Class<?> clazz, String fieldName) {
        if (clazz == null) return null;
        if (fieldName == null) return null;

        var key = clazz.getName() + "." + fieldName;
        return fieldCache.computeIfAbsent(key, k -> {
            try {
                var field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException | NoSuchFieldError exception) {
                return null;
            }
        });
    }

    /**
     * Gets the value of a field from the specified object.
     *
     * @param clazz     The class containing the field
     * @param fieldName The name of the field
     * @param instance  The object instance (null for static fields)
     * @return The field value
     * @throws IllegalAccessException If the field cannot be accessed
     */
    @SuppressWarnings("unchecked")
    public static <T> T getValue(Class<?> clazz, String fieldName, Object instance) throws IllegalAccessException {
        var field = getField(clazz, fieldName);
        if (field == null) return null;

        return (T) field.get(instance);
    }

    /**
     * Sets the value of a field in the specified object.
     *
     * @param clazz     The class containing the field
     * @param fieldName The name of the field
     * @param instance  The object instance (null for static fields)
     * @param value     The value to set
     * @throws IllegalAccessException If the field cannot be accessed
     */
    public static void setValue(Class<?> clazz, String fieldName, Object instance, Object value) throws IllegalAccessException {
        var field = getField(clazz, fieldName);
        if (field == null) throw new IllegalArgumentException("Field '" + fieldName + "' not found in class " + clazz.getName());
        field.set(instance, value);
    }

    /**
     * Gets the value of a field from the specified object using the object's class.
     *
     * @param fieldName The name of the field
     * @param instance  The object instance
     * @return The field value
     * @throws IllegalAccessException If the field cannot be accessed
     */
    @SuppressWarnings("unchecked")
    public static <T> T getValue(String fieldName, Object instance) throws IllegalAccessException {
        return getValue(instance.getClass(), fieldName, instance);
    }

    /**
     * Sets the value of a field in the specified object using the object's class.
     *
     * @param fieldName The name of the field
     * @param instance  The object instance
     * @param value     The value to set
     * @throws IllegalAccessException If the field cannot be accessed
     */
    public static void setValue(String fieldName, Object instance, Object value) throws IllegalAccessException {
        setValue(instance.getClass(), fieldName, instance, value);
    }

    public static String getFirstFieldName(Class<?> clazz, Class<?> type) {
        var key = clazz.getName() + ":" + type.getName();
        return firstFieldNameCache.computeIfAbsent(key, k -> {
            var fields = clazz.getDeclaredFields();

            for (var field : fields) {
                if (!ClassAccessor.assignableFrom(type, field.getType())) continue;

                return field.getName();
            }

            return null;
        });
    }

    /**
     * Clears the field cache. This should be called sparingly as it will
     * remove all cached field references.
     */
    public static void clearCache() {
        fieldCache.clear();
        firstFieldNameCache.clear();
    }

    /**
     * Gets the current size of the field cache.
     *
     * @return The number of cached fields and first field names
     */
    public static int getCacheSize() {
        return fieldCache.size() + firstFieldNameCache.size();
    }
}