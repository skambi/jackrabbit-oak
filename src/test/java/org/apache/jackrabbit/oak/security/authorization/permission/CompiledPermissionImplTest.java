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
package org.apache.jackrabbit.oak.security.authorization.permission;

import java.security.Principal;
import java.security.acl.Group;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.jackrabbit.oak.AbstractSecurityTest;
import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.core.ImmutableRoot;
import org.apache.jackrabbit.oak.core.ImmutableTree;
import org.apache.jackrabbit.oak.core.TreeTypeProvider;
import org.apache.jackrabbit.oak.namepath.NamePathMapper;
import org.apache.jackrabbit.oak.security.SecurityProviderImpl;
import org.apache.jackrabbit.oak.security.authorization.restriction.RestrictionProviderImpl;
import org.apache.jackrabbit.oak.security.principal.PrincipalImpl;
import org.apache.jackrabbit.oak.security.privilege.PrivilegeBits;
import org.apache.jackrabbit.oak.security.privilege.PrivilegeBitsProvider;
import org.apache.jackrabbit.oak.security.privilege.PrivilegeConstants;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.authorization.AccessControlConfiguration;
import org.apache.jackrabbit.oak.spi.security.authorization.OpenAccessControlConfiguration;
import org.apache.jackrabbit.oak.spi.security.authorization.permission.ReadStatus;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.Restriction;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionProvider;
import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.jackrabbit.oak.util.NodeUtil;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.apache.jackrabbit.JcrConstants.JCR_PRIMARYTYPE;
import static org.apache.jackrabbit.JcrConstants.NT_UNSTRUCTURED;
import static org.junit.Assert.assertSame;

/**
 * CompiledPermissionImplTest... TODO
 */
@Ignore("work in progress")
public class CompiledPermissionImplTest extends AbstractSecurityTest implements PermissionConstants {

    private Principal userPrincipal;
    private Principal group1;
    private Principal group2;
    private Principal group3;

    private PrivilegeBitsProvider pbp;
    private RestrictionProvider rp;

    private String node1Path = "/nodeName1";
    private String node2Path = node1Path + "/nodeName2";

    private List<String> allPaths;
    private List<String> rootAndUsers;
    private List<String> nodePaths;

    @Before
    @Override
    public void before() throws Exception {
        super.before();

        userPrincipal = new PrincipalImpl("test");
        group1 = EveryonePrincipal.getInstance();
        group2 = new GroupImpl("group2");
        group3 = new GroupImpl("group3");

        pbp = new PrivilegeBitsProvider(root);
        rp = new RestrictionProviderImpl(NamePathMapper.DEFAULT);

        NodeUtil rootNode = new NodeUtil(root.getTree("/"));
        NodeUtil system = rootNode.getChild("jcr:system");
        NodeUtil perms = system.addChild(REP_PERMISSION_STORE, NT_REP_PERMISSION_STORE);
        perms.addChild(userPrincipal.getName(), NT_REP_PERMISSION_STORE);
        perms.addChild(group1.getName(), NT_REP_PERMISSION_STORE);
        perms.addChild(group2.getName(), NT_REP_PERMISSION_STORE);
        perms.addChild(group3.getName(), NT_REP_PERMISSION_STORE);
        NodeUtil testNode = rootNode.addChild("nodeName1", NT_UNSTRUCTURED);
        testNode.setString("propName1", "strValue");
        NodeUtil testNode2 = testNode.addChild("nodeName2", NT_UNSTRUCTURED);
        testNode2.setString("propName2", "strValue");
        root.commit();

        allPaths = ImmutableList.of("/", UserConstants.DEFAULT_USER_PATH, node1Path, node2Path);
        rootAndUsers = ImmutableList.of("/", UserConstants.DEFAULT_USER_PATH);
        nodePaths = ImmutableList.of(node1Path, node2Path);
    }

    @Override
    public void after() throws Exception {
        root.getTree(PERMISSIONS_STORE_PATH).remove();
        root.commit();

        super.after();
    }

    @Override
    protected SecurityProvider getSecurityProvider() {
        return new SecurityProviderImpl() {
            @Nonnull
            @Override
            public AccessControlConfiguration getAccessControlConfiguration() {
                return new OpenAccessControlConfiguration();
            }
        };
    }

    @Test
    public void testGetReadStatus() throws Exception {
        setupPermission(userPrincipal, "/", true, 0, PrivilegeConstants.JCR_READ);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(userPrincipal));
        assertReadStatus(ReadStatus.ALLOW_ALL, cp, allPaths);
    }

    @Test
    public void testGetReadStatus1() throws Exception {
        setupPermission(group1, node2Path, true, 0, PrivilegeConstants.JCR_READ);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(group1));

        assertReadStatus(ReadStatus.DENY_THIS, cp, ImmutableList.of("/", node1Path, UserConstants.DEFAULT_USER_PATH));
        assertReadStatus(ReadStatus.ALLOW_ALL, cp, Collections.singletonList(node2Path));
    }

    @Test
    public void testGetReadStatus2() throws Exception {
        setupPermission(userPrincipal, "/", true, 0, PrivilegeConstants.JCR_READ);
        setupPermission(group1, "/", false, 0, PrivilegeConstants.JCR_READ, Collections.<Restriction>emptySet());

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(userPrincipal));
        assertReadStatus(ReadStatus.ALLOW_ALL, cp, allPaths);
    }

    @Test
    public void testGetReadStatus3() throws Exception {
        setupPermission(group1, "/", true, 0, PrivilegeConstants.JCR_READ);
        setupPermission(group2, "/", false, 1, PrivilegeConstants.JCR_READ);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(group1, group2));
        assertReadStatus(ReadStatus.DENY_ALL, cp, allPaths);
    }

    @Test
    public void testGetReadStatus4() throws Exception {
        setupPermission(group1, "/", true, 0, PrivilegeConstants.JCR_READ);
        setupPermission(group2, node2Path, true, 1, PrivilegeConstants.JCR_READ);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(group1, group2));
        assertReadStatus(ReadStatus.ALLOW_ALL, cp, allPaths);
    }

    @Test
    public void testGetReadStatus5() throws Exception {
        setupPermission(userPrincipal, "/", true, 0, PrivilegeConstants.JCR_READ);
        setupPermission(group2, node1Path, false, 1, PrivilegeConstants.JCR_READ);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(userPrincipal, group2));
        assertReadStatus(ReadStatus.ALLOW_ALL, cp, allPaths);
    }

    @Test
    public void testGetReadStatus6() throws Exception {
        setupPermission(group2, "/", true, 0, PrivilegeConstants.JCR_READ);
        setupPermission(userPrincipal, node1Path, false, 0, PrivilegeConstants.JCR_READ);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(userPrincipal, group2));

        assertReadStatus(ReadStatus.ALLOW_THIS, cp, rootAndUsers);
        assertReadStatus(ReadStatus.DENY_ALL, cp, nodePaths);
    }

    @Test
    public void testGetReadStatus7() throws Exception {
        setupPermission(group2, "/", true, 0, PrivilegeConstants.REP_READ_PROPERTIES);
        setupPermission(userPrincipal, node1Path, true, 0, PrivilegeConstants.REP_READ_NODES);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(userPrincipal, group2));

        assertReadStatus(ReadStatus.ALLOW_PROPERTIES, cp, rootAndUsers);
        assertReadStatus(ReadStatus.ALLOW_ALL, cp, nodePaths);
    }

    @Test
    public void testGetReadStatus8() throws Exception {
        setupPermission(userPrincipal, "/", true, 0, PrivilegeConstants.REP_READ_PROPERTIES);
        setupPermission(group2, node1Path, true, 0, PrivilegeConstants.REP_READ_NODES);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(userPrincipal, group2));

        // TODO
        assertReadStatus(ReadStatus.DENY_THIS, ReadStatus.ALLOW_THIS, cp, rootAndUsers);
        assertReadStatus(ReadStatus.ALLOW_ALL, ReadStatus.ALLOW_THIS, cp, nodePaths);
    }

    @Test
    public void testGetReadStatus9() throws Exception {
        setupPermission(group2, "/", true, 0, PrivilegeConstants.REP_READ_PROPERTIES);
        setupPermission(group1, node1Path, true, 0, PrivilegeConstants.REP_READ_NODES);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(group1, group2));

        assertReadStatus(ReadStatus.ALLOW_PROPERTIES, cp, rootAndUsers);
        assertReadStatus(ReadStatus.ALLOW_ALL, cp, nodePaths);
    }

    @Test
    public void testGetReadStatus10() throws Exception {
        setupPermission(group2, "/", false, 0, PrivilegeConstants.JCR_READ);
        setupPermission(group1, node1Path, true, 0, PrivilegeConstants.REP_READ_NODES);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(group1, group2));

        assertReadStatus(ReadStatus.DENY_THIS, cp, rootAndUsers);
        assertReadStatus(ReadStatus.ALLOW_NODES, cp, nodePaths);
    }

    @Test
    public void testGetReadStatus11() throws Exception {
        setupPermission(group2, "/", false, 0, PrivilegeConstants.JCR_READ);
        setupPermission(group2, node1Path, false, 0, PrivilegeConstants.JCR_READ);
        setupPermission(group1, node2Path, true, 0, PrivilegeConstants.REP_READ_NODES);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(group1, group2));

        List<String> treePaths = ImmutableList.of("/", UserConstants.DEFAULT_USER_PATH, node1Path);
        assertReadStatus(ReadStatus.DENY_THIS, cp, treePaths);
        assertReadStatus(ReadStatus.ALLOW_NODES, cp, Collections.singletonList(node2Path));
    }

    @Test
    public void testGetReadStatus12() throws Exception {
        setupPermission(group1, "/", true, 0, PrivilegeConstants.JCR_READ);
        setupPermission(group1, node1Path, false, 0, PrivilegeConstants.REP_READ_PROPERTIES);
        setupPermission(group1, node2Path, true, 0, PrivilegeConstants.REP_READ_NODES);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(group1));

        assertReadStatus(ReadStatus.ALLOW_THIS, cp, rootAndUsers);
        assertReadStatus(ReadStatus.ALLOW_NODES, cp, nodePaths);
    }

    @Test
    public void testGetReadStatus13() throws Exception {
        setupPermission(group1, "/", true, 0, PrivilegeConstants.JCR_READ);
        setupPermission(group1, node1Path, false, 0, PrivilegeConstants.REP_READ_PROPERTIES);
        setupPermission(group1, node2Path, true, 0, PrivilegeConstants.JCR_READ);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(group1));

        assertReadStatus(ReadStatus.ALLOW_THIS, cp, rootAndUsers);
        assertReadStatus(ReadStatus.ALLOW_NODES, cp, Collections.singletonList(node1Path));
        assertReadStatus(ReadStatus.ALLOW_ALL, cp, nodePaths);
    }

    @Test
    public void testGetReadStatus14() throws Exception {
        setupPermission(group1, "/", true, 0, PrivilegeConstants.REP_READ_NODES);
        setupPermission(group1, node1Path, false, 0, PrivilegeConstants.REP_READ_PROPERTIES);
        setupPermission(group1, node2Path, true, 0, PrivilegeConstants.REP_READ_PROPERTIES);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(group1));

        assertReadStatus(ReadStatus.ALLOW_NODES, cp, rootAndUsers);
        assertReadStatus(ReadStatus.ALLOW_NODES, cp, Collections.singletonList(node1Path));
        assertReadStatus(ReadStatus.ALLOW_ALL, cp, nodePaths);
    }

    @Test
    public void testGetReadStatus15() throws Exception {
        setupPermission(group1, "/", true, 0, PrivilegeConstants.REP_READ_NODES);
        setupPermission(group1, node1Path, false, 0, PrivilegeConstants.JCR_READ);
        setupPermission(group1, node2Path, true, 0, PrivilegeConstants.REP_READ_PROPERTIES);

        CompiledPermissionImpl cp = createPermissions(ImmutableSet.of(group1));

        assertReadStatus(ReadStatus.ALLOW_NODES, cp, rootAndUsers);
        assertReadStatus(ReadStatus.DENY_THIS, cp, Collections.singletonList(node1Path));
        assertReadStatus(ReadStatus.ALLOW_PROPERTIES, cp, nodePaths);
    }

    // TODO: tests with restrictions
    // TODO: complex tests with entries for paths outside of the tested hierarchy
    // TODO: tests for isGranted
    // TODO: tests for hasPrivilege/getPrivileges
    // TODO: tests for path base evaluation

    private CompiledPermissionImpl createPermissions(Set<Principal> principals) {
        ImmutableTree permissionsTree = new ImmutableRoot(root, TreeTypeProvider.EMPTY).getTree(PERMISSIONS_STORE_PATH);
        return new CompiledPermissionImpl(principals, permissionsTree, pbp, rp);
    }

    private void setupPermission(Principal principal, String path, boolean isAllow,
                                 int index, String privilegeName) throws CommitFailedException {
        setupPermission(principal, path, isAllow, index, privilegeName, Collections.<Restriction>emptySet());
    }

    private void setupPermission(Principal principal, String path, boolean isAllow,
                                 int index, String privilegeName, Set<Restriction> restrictions) throws CommitFailedException {
        PrivilegeBits pb = pbp.getBits(privilegeName);
        String name = ((isAllow) ? PREFIX_ALLOW : PREFIX_DENY) + "-" + Objects.hashCode(path, principal, index, pb, isAllow, restrictions);
        Tree principalRoot = root.getTree(PERMISSIONS_STORE_PATH + '/' + principal.getName());
        Tree entry = principalRoot.addChild(name);
        entry.setProperty(JCR_PRIMARYTYPE, NT_REP_PERMISSIONS);
        entry.setProperty(REP_ACCESS_CONTROLLED_PATH, path);
        entry.setProperty(REP_INDEX, index);
        entry.setProperty(pb.asPropertyState(REP_PRIVILEGE_BITS));
        for (Restriction restriction : restrictions) {
            entry.setProperty(restriction.getProperty());
        }
        root.commit();
    }

    private void assertReadStatus(ReadStatus expectedTrees,
                                  CompiledPermissions cp,
                                  List<String> treePaths) {
        assertReadStatus(expectedTrees, expectedTrees, cp, treePaths);
    }

    private void assertReadStatus(ReadStatus expectedTrees,
                                  ReadStatus expectedProperties,
                                  CompiledPermissions cp,
                                  List<String> treePaths) {
        for (String path : treePaths) {
            Tree node = root.getTree(path);
            assertSame("Tree " + path, expectedTrees, cp.getReadStatus(node, null));
            assertSame("Property jcr:primaryType " + path, expectedProperties, cp.getReadStatus(node, node.getProperty(JCR_PRIMARYTYPE)));
        }
    }

    private class GroupImpl implements Group {

        private final String name;

        private GroupImpl(String name) {
            this.name = name;
        }

        @Override
        public boolean addMember(Principal principal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeMember(Principal principal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isMember(Principal principal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Enumeration<? extends Principal> members() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getName() {
            return name;
        }
    }
}