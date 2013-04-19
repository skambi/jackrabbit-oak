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

package org.apache.jackrabbit.oak.jcr.delegate;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.jcr.InvalidItemStateException;

import org.apache.jackrabbit.oak.api.Tree.Status;
import org.apache.jackrabbit.oak.api.TreeLocation;
import org.apache.jackrabbit.oak.commons.PathUtils;

/**
 * Abstract base class for {@link NodeDelegate} and {@link PropertyDelegate}
 */
public abstract class ItemDelegate {

    protected final SessionDelegate sessionDelegate;

    /** The underlying {@link org.apache.jackrabbit.oak.api.TreeLocation} of this item. */
    private final TreeLocation location;

    ItemDelegate(SessionDelegate sessionDelegate, TreeLocation location) {
        this.sessionDelegate = checkNotNull(sessionDelegate);
        this.location = checkNotNull(location);
    }

    /**
     * Get the name of this item
     * @return oak name of this item
     */
    @Nonnull
    public String getName() throws InvalidItemStateException {
        return PathUtils.getName(getPath());
    }

    /**
     * Get the path of this item
     * @return oak path of this item
     */
    @Nonnull
    public String getPath() throws InvalidItemStateException {
        return getLocation().getPath();  // never null
    }

    /**
     * Get the parent of this item or {@code null}.
     * @return  parent of this item or {@code null} for root or if the parent
     * is not accessible.
     */
    @CheckForNull
    public NodeDelegate getParent() throws InvalidItemStateException {
        return NodeDelegate.create(sessionDelegate, getLocation().getParent());
    }

    public void checkNotStale() throws InvalidItemStateException {
        if (isStale()) {
            throw new InvalidItemStateException("stale");
        }
    }

    /**
     * Determine whether this item is stale
     * @return  {@code true} iff stale
     */
    public boolean isStale() {
        return !location.exists();
    }

    /**
     * Get the status of this item.
     * @return  {@link Status} of this item or {@code null} if not available.
     */
    @CheckForNull
    public Status getStatus() {
        return location.getStatus();
    }

    @Override
    public String toString() {
        return toStringHelper(this).add("location", location).toString();
    }

    //------------------------------------------------------------< internal >---

    /**
     * The underlying {@link org.apache.jackrabbit.oak.api.TreeLocation} of this item.
     * @return  tree location of the underlying item
     * @throws InvalidItemStateException if the location points to a stale item
     */
    @Nonnull
    TreeLocation getLocation() throws InvalidItemStateException {
        if (!location.exists()) {
            throw new InvalidItemStateException("Item is stale");
        }
        return location;
    }

}