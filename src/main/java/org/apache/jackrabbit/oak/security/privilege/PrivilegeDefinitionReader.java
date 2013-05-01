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
package org.apache.jackrabbit.oak.security.privilege;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.jackrabbit.oak.api.Root;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeDefinition;
import org.apache.jackrabbit.oak.util.TreeUtil;

/**
 * Reads privilege definitions from the repository content without applying
 * any validation.
 */
class PrivilegeDefinitionReader implements PrivilegeConstants {

    private final Tree privilegesTree;

    PrivilegeDefinitionReader(@Nonnull Root root) {
        this.privilegesTree = root.getTreeOrNull(PRIVILEGES_PATH);
    }

    /**
     * Read all registered privilege definitions from the content.
     *
     * @return All privilege definitions stored in the content.
     */
    @Nonnull
    Map<String, PrivilegeDefinition> readDefinitions() {
        if (privilegesTree == null) {
            return Collections.emptyMap();
        } else {
            Map<String, PrivilegeDefinition> definitions = new HashMap<String, PrivilegeDefinition>();
            for (Tree child : privilegesTree.getChildren()) {
                if (isPrivilegeDefinition(child)) {
                    PrivilegeDefinition def = readDefinition(child);
                    definitions.put(def.getName(), def);
                }
            }
            return definitions;
        }
    }

    /**
     * Retrieve the privilege definition with the specified {@code privilegeName}.
     *
     * @param privilegeName The name of a registered privilege definition.
     * @return The privilege definition with the specified name or {@code null}
     *         if the name doesn't refer to a registered privilege.
     */
    @CheckForNull
    PrivilegeDefinition readDefinition(String privilegeName) {
        if (privilegesTree == null) {
            return null;
        } else {
            Tree definitionTree = privilegesTree.getChildOrNull(privilegeName);
            return (isPrivilegeDefinition(definitionTree)) ? readDefinition(definitionTree) : null;
        }
    }

    /**
     * @param definitionTree
     * @return
     */
    @Nonnull
    static PrivilegeDefinition readDefinition(@Nonnull Tree definitionTree) {
        String name = definitionTree.getName();
        boolean isAbstract = TreeUtil.getBoolean(definitionTree, REP_IS_ABSTRACT);
        String[] declAggrNames = TreeUtil.getStrings(definitionTree, REP_AGGREGATES);

        return new PrivilegeDefinitionImpl(name, isAbstract, declAggrNames);
    }

    private static boolean isPrivilegeDefinition(@Nullable Tree tree) {
        return tree != null && NT_REP_PRIVILEGE.equals(TreeUtil.getPrimaryTypeName(tree));
    }
}