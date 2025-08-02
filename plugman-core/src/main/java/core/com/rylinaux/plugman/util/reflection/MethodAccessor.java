package core.com.rylinaux.plugman.util.reflection;

import lombok.experimental.UtilityClass;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A utility class for cached method access using reflection.
 * This class provides thread-safe caching of Method objects to improve performance
 * when repeatedly accessing the same methods.
 */
@UtilityClass
public class MethodAccessor {

    private static final Map<String, Method> methodCache = new ConcurrentHashMap<>();

    /**
     * Gets a method from the specified class with caching.
     * The method is automatically made accessible.
     *
     * @param clazz          The class containing the method
     * @param methodName     The name of the method
     * @param parameterTypes The parameter types of the method
     * @return The Method object, or null if not found
     */
    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        var key = buildMethodKey(clazz, methodName, parameterTypes);
        return methodCache.computeIfAbsent(key, k -> {
            try {
                var method = clazz.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException | NoSuchMethodError ignored) {
                return null;
            }
        });
    }

    /**
     * Invokes a method on the specified object.
     *
     * @param clazz          The class containing the method
     * @param methodName     The name of the method
     * @param instance       The object instance (null for static methods)
     * @param parameterTypes The parameter types of the method
     * @param args           The arguments to pass to the method
     * @return The method return value
     * @throws Exception If the method cannot be invoked
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Class<?> clazz, String methodName, Object instance, Class<?>[] parameterTypes, Object... args) throws Exception {
        var method = getMethod(clazz, methodName, parameterTypes);
        if (method == null) throw new IllegalArgumentException("Method '" + methodName + "' not found in class " + clazz.getName());
        return (T) method.invoke(instance, args);
    }

    /**
     * Invokes a method on the specified object using the object's class.
     *
     * @param methodName     The name of the method
     * @param instance       The object instance
     * @param parameterTypes The parameter types of the method
     * @param args           The arguments to pass to the method
     * @return The method return value
     * @throws Exception If the method cannot be invoked
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(String methodName, Object instance, Class<?>[] parameterTypes, Object... args) throws Exception {
        return invoke(instance.getClass(), methodName, instance, parameterTypes, args);
    }

    /**
     * Invokes a no-argument method on the specified object.
     *
     * @param clazz      The class containing the method
     * @param methodName The name of the method
     * @param instance   The object instance (null for static methods)
     * @return The method return value
     * @throws Exception If the method cannot be invoked
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Class<?> clazz, String methodName, Object instance) throws Exception {
        return invoke(clazz, methodName, instance, new Class<?>[0]);
    }

    /**
     * Invokes a no-argument method on the specified object using the object's class.
     *
     * @param methodName The name of the method
     * @param instance   The object instance
     * @return The method return value
     * @throws Exception If the method cannot be invoked
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(String methodName, Object instance) throws Exception {
        return invoke(instance.getClass(), methodName, instance, new Class<?>[0]);
    }

    /**
     * Finds a method by name in the specified class, searching through all declared methods.
     * This is useful when you don't know the exact parameter types.
     *
     * @param clazz      The class to search in
     * @param methodName The name of the method
     * @return The first Method found with the given name, or null if not found
     */
    public static Method findMethodByName(Class<?> clazz, String methodName) {
        var key = clazz.getName() + "." + methodName + ".byName";
        return methodCache.computeIfAbsent(key, k -> {
            try {
                var methods = clazz.getDeclaredMethods();
                for (var method : methods) {
                    if (!method.getName().equals(methodName)) continue;
                    method.setAccessible(true);
                    return method;
                }
                return null;
            } catch (Exception ignored) {
                return null;
            }
        });
    }

    /**
     * Builds a unique key for method caching based on class, method name, and parameter types.
     *
     * @param clazz          The class containing the method
     * @param methodName     The name of the method
     * @param parameterTypes The parameter types of the method
     * @return A unique string key for caching
     */
    private static String buildMethodKey(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        var keyBuilder = new StringBuilder();
        keyBuilder.append(clazz.getName()).append(".").append(methodName);

        if (parameterTypes.length == 0) keyBuilder.append("()");
        else {
            keyBuilder.append("(");
            for (var i = 0; i < parameterTypes.length; i++) {
                if (i > 0) keyBuilder.append(",");
                keyBuilder.append(parameterTypes[i].getName());
            }
            keyBuilder.append(")");
        }

        return keyBuilder.toString();
    }

    /**
     * Clears the method cache. This should be called sparingly as it will
     * remove all cached method references.
     */
    public static void clearCache() {
        methodCache.clear();
    }

    /**
     * Gets the current size of the method cache.
     *
     * @return The number of cached methods
     */
    public static int getCacheSize() {
        return methodCache.size();
    }
}