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
package org.apache.jackrabbit.oak.plugins.index.reference;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static java.lang.Double.POSITIVE_INFINITY;
import static javax.jcr.PropertyType.REFERENCE;
import static javax.jcr.PropertyType.WEAKREFERENCE;
import static org.apache.jackrabbit.oak.api.Type.STRING;
import static org.apache.jackrabbit.oak.commons.PathUtils.getName;
import static org.apache.jackrabbit.oak.commons.PathUtils.getParentPath;
import static org.apache.jackrabbit.oak.plugins.index.IndexConstants.INDEX_DEFINITIONS_NAME;
import static org.apache.jackrabbit.oak.plugins.index.reference.NodeReferenceConstants.NAME;
import static org.apache.jackrabbit.oak.plugins.index.reference.NodeReferenceConstants.REF_NAME;
import static org.apache.jackrabbit.oak.plugins.index.reference.NodeReferenceConstants.WEAK_REF_NAME;
import static org.apache.jackrabbit.oak.spi.query.Cursors.newPathCursor;

import java.util.ArrayList;

import org.apache.jackrabbit.oak.plugins.index.property.strategy.ContentMirrorStoreStrategy;
import org.apache.jackrabbit.oak.query.index.FilterImpl;
import org.apache.jackrabbit.oak.spi.query.Cursor;
import org.apache.jackrabbit.oak.spi.query.Filter;
import org.apache.jackrabbit.oak.spi.query.Filter.PropertyRestriction;
import org.apache.jackrabbit.oak.spi.query.QueryIndex;
import org.apache.jackrabbit.oak.spi.state.NodeState;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;

/**
 * Provides a QueryIndex that does lookups for node references based on a custom
 * index saved on hidden property names
 * 
 */
class ReferenceIndex implements QueryIndex {

    private static ContentMirrorStoreStrategy STORE = new ContentMirrorStoreStrategy();

    @Override
    public String getIndexName() {
        return NAME;
    }

    @Override
    public double getCost(Filter filter, NodeState root) {
        // TODO don't call getCost for such queries
        if (filter.getFullTextConstraint() != null) {
            // not an appropriate index for full-text search
            return POSITIVE_INFINITY;
        }
        for (PropertyRestriction pr : filter.getPropertyRestrictions()) {
            if (pr.propertyType == REFERENCE
                    || pr.propertyType == WEAKREFERENCE) {
                return 1;
            }
        }
        // not an appropriate index
        return POSITIVE_INFINITY;
    }

    @Override
    public Cursor query(Filter filter, NodeState root) {
        for (PropertyRestriction pr : filter.getPropertyRestrictions()) {
            if (pr.propertyType == REFERENCE) {
                String uuid = pr.first.getValue(STRING);
                String name = pr.propertyName;
                return lookup(root, uuid, name, REF_NAME);
            }
            if (pr.propertyType == WEAKREFERENCE) {
                String uuid = pr.first.getValue(STRING);
                String name = pr.propertyName;
                return lookup(root, uuid, name, WEAK_REF_NAME);
            }
        }
        return newPathCursor(new ArrayList<String>());
    }

    private static Cursor lookup(NodeState root, String uuid,
            final String name, String index) {
        NodeState indexRoot = root.getChildNode(INDEX_DEFINITIONS_NAME)
                .getChildNode(NAME);
        if (!indexRoot.exists()) {
            return newPathCursor(new ArrayList<String>());
        }
        Iterable<String> paths = STORE.query(new FilterImpl(), index + "("
                + uuid + ")", indexRoot, index, ImmutableSet.of(uuid));

        if (!"*".equals(name)) {
            paths = filter(paths, new Predicate<String>() {
                @Override
                public boolean apply(String path) {
                    return name.equals(getName(path));
                }
            });
        }
        paths = transform(paths, new Function<String, String>() {
            @Override
            public String apply(String path) {
                return getParentPath(path);
            }
        });
        return newPathCursor(paths);
    }

    @Override
    public String getPlan(Filter filter, NodeState root) {
        StringBuilder buff = new StringBuilder("reference");
        for (PropertyRestriction pr : filter.getPropertyRestrictions()) {
            if (pr.propertyType == REFERENCE) {
                buff.append(" PROPERTY([");
                buff.append(pr.propertyName);
                buff.append("], 'Reference') = ");
                buff.append(pr.first.getValue(STRING));
                return buff.toString();
            }
            if (pr.propertyType == WEAKREFERENCE) {
                buff.append(" PROPERTY([");
                buff.append(pr.propertyName);
                buff.append("], 'WeakReference') = ");
                buff.append(pr.first.getValue(STRING));
                return buff.toString();
            }
        }
        return buff.toString();
    }

}
