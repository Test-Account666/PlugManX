package core.com.rylinaux.plugman.util.reflection;

import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A utility class for cached class access using reflection.
 * This class provides thread-safe caching of Class objects to improve performance
 * when repeatedly accessing the same classes.
 */
@UtilityClass
public class ClassAccessor {

    private static final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();

    /**
     * Gets a class by name with caching.
     * This is equivalent to Class.forName() but with caching for better performance.
     *
     * @param className The fully qualified name of the class
     * @return The Class object, or null if not found
     */
    public static Class<?> getClass(String className) {
        return classCache.computeIfAbsent(className, k -> {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException | NoClassDefFoundError exception) {
                return null;
            }
        });
    }

    /**
     * Gets a class by name with caching and custom class loader.
     *
     * @param className   The fully qualified name of the class
     * @param classLoader The class loader to use
     * @return The Class object, or null if not found
     */
    public static Class<?> getClass(String className, ClassLoader classLoader) {
        var key = className + "@" + classLoader.hashCode();
        return classCache.computeIfAbsent(key, k -> {
            try {
                return Class.forName(className, true, classLoader);
            } catch (ClassNotFoundException | NoClassDefFoundError exception) {
                return null;
            }
        });
    }

    /**
     * Checks if a class exists without throwing exceptions.
     *
     * @param className The fully qualified name of the class
     * @return true if the class exists, false otherwise
     */
    public static boolean classExists(String className) {
        return getClass(className) != null;
    }

    /**
     * Checks if a class exists with a custom class loader.
     *
     * @param className   The fully qualified name of the class
     * @param classLoader The class loader to use
     * @return true if the class exists, false otherwise
     */
    public static boolean classExists(String className, ClassLoader classLoader) {
        return getClass(className, classLoader) != null;
    }

    /**
     * Checks if one class is assignable from another with caching.
     *
     * @param superClassName The name of the super class or interface
     * @param subClassName   The name of the sub class
     * @return true if subClass is assignable from superClass, false otherwise
     */
    public static boolean assignableFrom(String superClassName, String subClassName) {
        var superClass = getClass(superClassName);
        var subClass = getClass(subClassName);

        return assignableFrom(superClass, subClass);
    }

    public static boolean assignableFrom(Class<?> superClass, Class<?> subClass) {
        if (superClass == null || subClass == null) return false;

        return superClass.isAssignableFrom(subClass);
    }

    /**
     * Checks if a class is assignable from another class.
     *
     * @param superClass   The super class or interface
     * @param subClassName The name of the sub class
     * @return true if subClass is assignable from superClass, false otherwise
     */
    public static boolean assignableFrom(Class<?> superClass, String subClassName) {
        var subClass = getClass(subClassName);

        return assignableFrom(superClass, subClass);
    }

    /**
     * Checks if a class name is assignable from another class.
     *
     * @param superClassName The name of the super class or interface
     * @param subClass       The sub class
     * @return true if subClass is assignable from superClass, false otherwise
     */
    public static boolean assignableFrom(String superClassName, Class<?> subClass) {
        var superClass = getClass(superClassName);

        return assignableFrom(superClass, subClass);
    }

    /**
     * Creates a new instance of the specified class using the default constructor.
     *
     * @param className The fully qualified name of the class
     * @return A new instance of the class, or null if creation failed
     */
    @SuppressWarnings("unchecked")
    public static <T> T newInstance(String className) {
        var clazz = getClass(className);
        if (clazz == null) return null;

        try {
            return (T) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * Creates a new instance of the specified class using a constructor with parameters.
     *
     * @param className      The fully qualified name of the class
     * @param parameterTypes The parameter types for the constructor
     * @param args           The arguments to pass to the constructor
     * @return A new instance of the class, or null if creation failed
     */
    @SuppressWarnings("unchecked")
    public static <T> T newInstance(String className, Class<?>[] parameterTypes, Object... args) {
        var clazz = getClass(className);
        if (clazz == null) return null;

        try {
            var constructor = clazz.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return (T) constructor.newInstance(args);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * Gets the simple name of a class by its fully qualified name.
     *
     * @param className The fully qualified name of the class
     * @return The simple name of the class, or null if class not found
     */
    public static String getSimpleName(String className) {
        var clazz = getClass(className);
        return clazz != null? clazz.getSimpleName() : null;
    }

    /**
     * Gets the package name of a class by its fully qualified name.
     *
     * @param className The fully qualified name of the class
     * @return The package name of the class, or null if class not found
     */
    public static String getPackageName(String className) {
        var clazz = getClass(className);
        return clazz != null? clazz.getPackage().getName() : null;
    }

    /**
     * Clears the class cache. This should be called sparingly as it will
     * remove all cached class references.
     */
    public static void clearCache() {
        classCache.clear();
    }

    /**
     * Gets the current size of the class cache.
     *
     * @return The number of cached classes
     */
    public static int getCacheSize() {
        return classCache.size();
    }
}