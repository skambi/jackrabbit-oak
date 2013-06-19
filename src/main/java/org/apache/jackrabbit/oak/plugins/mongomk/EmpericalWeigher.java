/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.jackrabbit.oak.plugins.mongomk;

import com.google.common.cache.Weigher;
import org.apache.jackrabbit.oak.plugins.mongomk.util.Utils;

/**
 * Determines the weight of object based on the memory taken by them. The memory esimates
 * are based on emperical data and not exact
 */
public class EmpericalWeigher implements Weigher<String, Object> {

    @Override
    public int weigh(String key, Object value) {
        int size = key.length() * 2;

        if (value instanceof Node) {
            size += ((Node) value).getMemory();
        } else if (value instanceof Node.Children) {
            size += ((Node.Children) value).getMemory();
        } else if (value instanceof MongoDocumentStore.CachedDocument) {
            size += Utils.estimateMemoryUsage(((MongoDocumentStore.CachedDocument) value).value);
        } else if (value != null) {
            throw new IllegalArgumentException("Cannot determine weight for object of type " + value.getClass());
        }
        return size;
    }
}