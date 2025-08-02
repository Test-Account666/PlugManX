package core.com.rylinaux.plugman.services;

/*
 * #%L
 * PlugMan
 * %%
 * Copyright (C) 2010 - 2014 PlugMan
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import core.com.rylinaux.plugman.file.messaging.MessageFormatter;
import core.com.rylinaux.plugman.plugins.PluginManager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Type-safe service registry for accessing core PlugMan services.
 * This replaces static access patterns with a centralized, injectable service locator.
 *
 * @author rylinaux
 */
public class ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    /**
     * Register a service instance.
     *
     * @param serviceClass the service class/interface
     * @param instance     the service instance
     * @param <T>          the service type
     */
    public <T> void register(Class<T> serviceClass, T instance) {
        if (serviceClass == null) throw new IllegalArgumentException("Service class cannot be null");
        if (instance == null) throw new IllegalArgumentException("Service instance cannot be null");
        services.put(serviceClass, instance);
    }

    /**
     * Get a service instance.
     *
     * @param serviceClass the service class/interface
     * @param <T>          the service type
     * @return the service instance
     * @throws IllegalStateException if the service is not registered
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> serviceClass) {
        var service = services.get(serviceClass);
        if (service == null) throw new IllegalStateException("Service not registered: " + serviceClass.getName());
        return (T) service;
    }

    /**
     * Get a service instance optionally.
     *
     * @param serviceClass the service class/interface
     * @param <T>          the service type
     * @return optional containing the service instance, or empty if not registered
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOptional(Class<T> serviceClass) {
        var service = services.get(serviceClass);
        return Optional.ofNullable((T) service);
    }

    /**
     * Check if a service is registered.
     *
     * @param serviceClass the service class/interface
     * @return true if registered, false otherwise
     */
    public boolean isRegistered(Class<?> serviceClass) {
        return services.containsKey(serviceClass);
    }

    /**
     * Unregister a service.
     *
     * @param serviceClass the service class/interface
     * @param <T>          the service type
     * @return the previously registered instance, or null if not registered
     */
    @SuppressWarnings("unchecked")
    public <T> T unregister(Class<T> serviceClass) {
        return (T) services.remove(serviceClass);
    }

    /**
     * Clear all registered services.
     */
    public void clear() {
        services.clear();
    }

    /**
     * Get the number of registered services.
     *
     * @return the number of registered services
     */
    public int size() {
        return services.size();
    }

    // Convenience methods for commonly used services

    /**
     * Get the PluginManager service.
     *
     * @return the PluginManager instance
     * @throws IllegalStateException if PluginManager is not registered
     */
    public PluginManager getPluginManager() {
        return get(PluginManager.class);
    }

    /**
     * Get the MessageFormatter service.
     *
     * @return the MessageFormatter instance
     * @throws IllegalStateException if MessageFormatter is not registered
     */
    public MessageFormatter getMessageFormatter() {
        return get(MessageFormatter.class);
    }
}