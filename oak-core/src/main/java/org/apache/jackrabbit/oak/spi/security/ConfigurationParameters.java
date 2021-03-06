/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.spi.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ConfigurationParameters is a convenience class that allows typed access to configuration properties. It implements
 * the {@link Map} interface but is immutable.
 */
public final class ConfigurationParameters implements Map<String, Object> {

    /**
     * internal logger
     */
    private static final Logger log = LoggerFactory.getLogger(ConfigurationParameters.class);

    /**
     * An empty configuration parameters
     */
    public static final ConfigurationParameters EMPTY = new ConfigurationParameters();

    /**
     * internal map of the config parameters
     */
    private final Map<String, Object> options;

    /**
     * creates an empty config parameters instance.
     * Note: the constructor is private to avoid creation of empty maps.
     */
    private ConfigurationParameters() {
        this.options = Collections.emptyMap();
    }

    /**
     * Creates an config parameter instance.
     * Note: the constructor is private to avoid creation of empty maps.
     * @param options the source options.
     */
    private ConfigurationParameters(@Nonnull Map<String, ?> options) {
        this.options = Collections.unmodifiableMap(options);
    }

    /**
     * Creates a new configuration parameters instance by merging all {@code params} sequentially.
     * I.e. property define in subsequent arguments overwrite the ones before.
     *
     * @param params source parameters to merge
     * @return merged configuration parameters or {@link #EMPTY} if all source params were empty.
     */
    @Nonnull
    public static ConfigurationParameters of(@Nonnull ConfigurationParameters... params) {
        Map<String, Object> m = new HashMap<String, Object>();
        for (ConfigurationParameters cp : params) {
            m.putAll(cp.options);
        }
        return m.isEmpty() ? EMPTY : new ConfigurationParameters(m);
    }

    /**
     * Creates new a configuration parameters instance by copying the given properties.
     * @param properties source properties
     * @return configuration parameters or {@link #EMPTY} if the source properties were empty.
     */
    @Nonnull
    public static ConfigurationParameters of(@Nonnull Properties properties) {
        if (properties.isEmpty()) {
            return EMPTY;
        }
        Map<String, Object> options = new HashMap<String, Object>(properties.size());
        for (Object name : properties.keySet()) {
            final String key = name.toString();
            options.put(key, properties.get(key));
        }
        return new ConfigurationParameters(options);
    }

    /**
     * Creates new a configuration parameters instance by copying the given properties.
     * @param properties source properties
     * @return configuration parameters or {@link #EMPTY} if the source properties were empty.
     */
    @Nonnull
    public static ConfigurationParameters of(@Nonnull Dictionary<String, Object> properties) {
        if (properties.isEmpty()) {
            return EMPTY;
        }
        Map<String, Object> options = new HashMap<String, Object>(properties.size());
        for (Enumeration<String> keys = properties.keys(); keys.hasMoreElements();) {
            String key = keys.nextElement();
            options.put(key, properties.get(key));
        }
        return new ConfigurationParameters(options);
    }

    /**
     * Creates new a configuration parameters instance by copying the given map.
     * @param map source map
     * @return configuration parameters or {@link #EMPTY} if the source map was empty.
     */
    @Nonnull
    public static ConfigurationParameters of(@Nonnull Map<?, ?> map) {
        if (map.isEmpty()) {
            return EMPTY;
        }
        Map<String, Object> options = new HashMap<String, Object>(map.size());
        for (Map.Entry<?,?> e : map.entrySet()) {
            options.put(String.valueOf(e.getKey()), e.getValue());
        }
        return new ConfigurationParameters(options);
    }

    /**
     * Returns {@code true} if this instance contains a configuration entry with
     * the specified key irrespective of the defined value; {@code false} otherwise.
     *
     * @param key The key to be tested.
     * @return {@code true} if this instance contains a configuration entry with
     * the specified key irrespective of the defined value; {@code false} otherwise.
     */
    public boolean contains(@Nonnull String key) {
        return options.containsKey(key);
    }

    /**
     * Returns the value of the configuration entry with the given {@code key}
     * applying the following rules:
     *
     * <ul>
     *     <li>If this instance doesn't contain a configuration entry with that
     *     key the specified {@code defaultValue} will be returned.</li>
     *     <li>If {@code defaultValue} is {@code null} the original value will
     *     be returned.</li>
     *     <li>If the configured value is {@code null} this method will always
     *     return {@code null}.</li>
     *     <li>If neither {@code defaultValue} nor the configured value is
     *     {@code null} an attempt is made to convert the configured value to
     *     match the type of the default value.</li>
     * </ul>
     *
     * @param key The name of the configuration option.
     * @param defaultValue The default value to return if no such entry exists
     * or to use for conversion.
     * @param targetClass The target class
     * @return The original or converted configuration value or {@code null}.
     */
    @CheckForNull
    public <T> T getConfigValue(@Nonnull String key, @Nullable T defaultValue,
                                @Nullable Class<T> targetClass) {
        if (options.containsKey(key)) {
            return convert(options.get(key), defaultValue, targetClass);
        } else {
            return defaultValue;
        }
    }

    /**
     * Returns the value of the configuration entry with the given {@code key}
     * applying the following rules:
     *
     * <ul>
     *     <li>If this instance doesn't contain a configuration entry with that
     *     key, or if the entry is {@code null}, the specified {@code defaultValue} will be returned.</li>
     *     <li>If the configured value is not {@code null} an attempt is made to convert the configured value to
     *     match the type of the default value.</li>
     * </ul>
     *
     * @param key The name of the configuration option.
     * @param defaultValue The default value to return if no such entry exists
     * or to use for conversion.
     * @return The original or converted configuration value or {@code null}.
     */
    @Nonnull
    public <T> T getConfigValue(@Nonnull String key, @Nonnull T defaultValue) {
        Object property = options.get(key);
        if (property == null) {
            return defaultValue;
        } else {
            T value = convert(property, defaultValue, null);
            return (value == null) ? defaultValue : value;
        }
    }

    //--------------------------------------------------------< private >---
    @SuppressWarnings("unchecked")
    @Nullable
    private static <T> T convert(@Nullable Object configProperty, @Nullable T defaultValue, @Nullable Class<T> targetClass) {
        if (configProperty == null) {
            return null;
        }
        String str = configProperty.toString();
        Class clazz = targetClass;
        if (clazz == null) {
            clazz = (defaultValue == null)
                    ? configProperty.getClass()
                    : defaultValue.getClass();
        }
        try {
            if (clazz.isAssignableFrom(configProperty.getClass())) {
                return (T) configProperty;
            } else if (clazz == String.class) {
                return (T) str;
            } else if (clazz == Integer.class || clazz == int.class) {
                return (T) Integer.valueOf(str);
            } else if (clazz == Long.class || clazz == long.class) {
                return (T) Long.valueOf(str);
            } else if (clazz == Float.class || clazz == float.class) {
                return (T) Float.valueOf(str);
            } else if (clazz == Double.class || clazz == double.class) {
                return (T) Double.valueOf(str);
            } else if (clazz == Boolean.class || clazz == boolean.class) {
                return (T) Boolean.valueOf(str);
            } else {
                // unsupported target type
                log.warn("Unsupported target type {} for value {}", clazz.getName(), str);
                throw new IllegalArgumentException("Cannot convert config entry " + str + " to " + clazz.getName());
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid value {}; cannot be parsed into {}", str, clazz.getName());
            throw new IllegalArgumentException("Cannot convert config entry " + str + " to " + clazz.getName(), e);
        }
    }
    //-----------------------------------------------------------------------------------< Map interface delegation >---

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return options.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return options.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(Object key) {
        return options.containsKey(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(Object value) {
        return options.containsValue(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object get(Object key) {
        return options.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object put(String key, Object value) {
        // we rely on the immutability of the delegated map to throw the correct exceptions.
        return options.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object remove(Object key) {
        // we rely on the immutability of the delegated map to throw the correct exceptions.
        return options.remove(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putAll(Map<? extends String, ?> m) {
        // we rely on the immutability of the delegated map to throw the correct exceptions.
        options.putAll(m);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        // we rely on the immutability of the delegated map to throw the correct exceptions.
        options.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> keySet() {
        return options.keySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Object> values() {
        return options.values();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Entry<String,Object>> entrySet() {
        return options.entrySet();
    }
}