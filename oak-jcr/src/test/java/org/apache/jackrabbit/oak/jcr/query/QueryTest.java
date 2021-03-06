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
package org.apache.jackrabbit.oak.jcr.query;

import static com.google.common.collect.Sets.newHashSet;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.oak.jcr.AbstractRepositoryTest;
import org.apache.jackrabbit.oak.jcr.NodeStoreFixture;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests the query feature.
 */
public class QueryTest extends AbstractRepositoryTest {

    public QueryTest(NodeStoreFixture fixture) {
        super(fixture);
    }
    
    @Test
    public void unicode() throws Exception {
        Session session = getAdminSession();
        Node content = session.getRootNode().addNode("test");
        String[][] list = {
                {"three", "\u00e4\u00f6\u00fc"}, 
                {"two", "123456789"}, 
                {"one", "\u3360\u3361\u3362\u3363\u3364\u3365\u3366\u3367\u3368\u3369"}, 
        };
        for (String[] pair : list) {
            content.addNode(pair[0]).setProperty("prop", 
                    "propValue testSearch " + pair[1] + " data");
        }
        session.save();
        for (String[] pair : list) {
            String query = "//*[jcr:contains(., '" + pair[1] + "')]";
            QueryResult r = session.getWorkspace().
                    getQueryManager().createQuery(
                    query, "xpath").execute();
            NodeIterator it = r.getNodes();
            assertTrue(it.hasNext());
            String path = it.nextNode().getPath();
            assertEquals("/test/" + pair[0], path);
            assertFalse(it.hasNext());
        }        
    }
    
    @Test
    @Ignore("OAK-1215")
    public void anyChildNodeProperty() throws Exception {
        Session session = getAdminSession();
        Node content = session.getRootNode().addNode("test");
        content.addNode("one").addNode("child").setProperty("prop", "hello");
        content.addNode("two").addNode("child").setProperty("prop", "hi");
        session.save();
        String query = "//*[*/@prop = 'hello']";
        QueryResult r = session.getWorkspace().getQueryManager().createQuery(
                query, "xpath").execute();
        NodeIterator it = r.getNodes();
        assertTrue(it.hasNext());
        String path = it.nextNode().getPath();
        assertEquals("/test/one", path);
        assertFalse(it.hasNext());
        
        query = "//*[*/*/@prop = 'hello']";
        r = session.getWorkspace().getQueryManager().createQuery(
                query, "xpath").execute();
        it = r.getNodes();
        assertTrue(it.hasNext());
        path = it.nextNode().getPath();
        assertEquals("/test", path);
        assertFalse(it.hasNext());

    }
    
    @Test
    public void relativeNotExistsProperty() throws Exception {
        Session session = getAdminSession();
        Node content = session.getRootNode().addNode("test");
        content.addNode("one").addNode("child").setProperty("prop", "hello");
        content.addNode("two").addNode("child");
        session.save();
        String query = "//*[not(child/@prop)]";
        QueryResult r = session.getWorkspace().getQueryManager().createQuery(
                query, "xpath").execute();
        NodeIterator it = r.getNodes();
        assertTrue(it.hasNext());
        String path = it.nextNode().getPath();
        assertEquals("/test/two", path);
        assertFalse(it.hasNext());
    }

    @Test
    public void doubleQuote() throws RepositoryException {
        Session session = getAdminSession();
        Node hello = session.getRootNode().addNode("hello");
        hello.setProperty("x", 1);
        Node world = hello.addNode("world");
        world.setProperty("x", 2);
        session.save();
        QueryManager qm = session.getWorkspace().getQueryManager();
        Query q;
        q = qm.createQuery(
                "SELECT * FROM [nt:base] AS s WHERE ISDESCENDANTNODE(s,[/hello])", 
                Query.JCR_SQL2);
        assertEquals("/hello/world", getPaths(q));
        q = qm.createQuery(
                "SELECT * FROM [nt:base] AS s WHERE ISDESCENDANTNODE(s,\"/hello\")", 
                Query.JCR_SQL2);
        assertEquals("/hello/world", getPaths(q));
        try {
            q = qm.createQuery(
                    "SELECT * FROM [nt:base] AS s WHERE ISDESCENDANTNODE(s,[\"/hello\"])", 
                    Query.JCR_SQL2);
            getPaths(q);
            fail();
        } catch (InvalidQueryException e) {
            // expected: absolute path
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void or() throws RepositoryException {
        Session session = getAdminSession();
        Node hello = session.getRootNode().addNode("hello");
        hello.setProperty("x", 1);
        Node world = hello.addNode("world");
        world.setProperty("x", 2);
        session.save();
        QueryManager qm = session.getWorkspace().getQueryManager();
        Query q;
        
        q = qm.createQuery("select a.[jcr:path] from [nt:base] as a " + 
                    "inner join [nt:base] as b " +
                    "on ischildnode(a, b) " + 
                    "where a.x = 1 or a.x = 2 or b.x = 3 or b.x = 4", Query.JCR_SQL2);
        assertEquals("/hello", getPaths(q));

        q = qm.createQuery("//hello[@x=1]/*[@x=2]", Query.XPATH);
        assertEquals("/hello/world", getPaths(q));

    }

    @SuppressWarnings("deprecation")
    @Test
    public void encodedPath() throws RepositoryException {
        Session session = getAdminSession();
        session.getRootNode().addNode("hello").addNode("world");
        session.save();
        QueryManager qm = session.getWorkspace().getQueryManager();
        Query q;
        
        q = qm.createQuery("/jcr:root/hel_x006c_o/*", Query.XPATH);
        assertEquals("/hello/world", getPaths(q));
        
        q = qm.createQuery("//hel_x006c_o", Query.XPATH);
        assertEquals("/hello", getPaths(q));
        
        q = qm.createQuery("//element(hel_x006c_o, nt:base)", Query.XPATH);
        assertEquals("/hello", getPaths(q));

    }
    
    private static String getPaths(Query q) throws RepositoryException {
        QueryResult r = q.execute();
        RowIterator it = r.getRows();
        StringBuilder buff = new StringBuilder();
        if (it.hasNext()) {
            Row row = it.nextRow();
            if (buff.length() > 0) {
                buff.append(", ");
            }
            buff.append(row.getPath());
        }
        return buff.toString();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void simple() throws RepositoryException {
        Session session = getAdminSession();
        Node hello = session.getRootNode().addNode("hello");
        hello.setProperty("id",  "1");
        hello.setProperty("text",  "hello_world");
        session.save();
        Node hello2 = session.getRootNode().addNode("hello2");
        hello2.setProperty("id",  "2");
        hello2.setProperty("text",  "hello world");
        session.save();

        ValueFactory vf = session.getValueFactory();

        QueryManager qm = session.getWorkspace().getQueryManager();

        // SQL-2

        Query q = qm.createQuery("select text from [nt:base] where id = $id", Query.JCR_SQL2);
        q.bindValue("id", vf.createValue("1"));
        QueryResult r = q.execute();
        RowIterator it = r.getRows();
        assertTrue(it.hasNext());
        Row row = it.nextRow();
        assertEquals("hello_world", row.getValue("text").getString());
        String[] columns = r.getColumnNames();
        assertEquals(1, columns.length);
        assertEquals("text", columns[0]);
        assertFalse(it.hasNext());

        r = q.execute();
        NodeIterator nodeIt = r.getNodes();
        assertTrue(nodeIt.hasNext());
        Node n = nodeIt.nextNode();
        assertEquals("hello_world", n.getProperty("text").getString());
        assertFalse(it.hasNext());

        // SQL

        q = qm.createQuery("select text from [nt:base] where text like 'hello\\_world' escape '\\'", Query.SQL);
        r = q.execute();
        columns = r.getColumnNames();
        assertEquals(3, columns.length);
        assertEquals("text", columns[0]);
        assertEquals("jcr:path", columns[1]);
        assertEquals("jcr:score", columns[2]);
        nodeIt = r.getNodes();
        assertTrue(nodeIt.hasNext());
        n = nodeIt.nextNode();
        assertEquals("hello_world", n.getProperty("text").getString());
        assertFalse(nodeIt.hasNext());

        // XPath

        q = qm.createQuery("//*[@id=1]", Query.XPATH);
        r = q.execute();
        assertEquals(
                newHashSet("jcr:path", "jcr:score", "jcr:primaryType"),
                newHashSet(r.getColumnNames()));
    }

    @Test
    public void skip() throws RepositoryException {
        Session session = getAdminSession();
        Node hello1 = session.getRootNode().addNode("hello1");
        hello1.setProperty("id",  "1");
        hello1.setProperty("data",  "x");
        session.save();
        Node hello3 = hello1.addNode("hello3");
        hello3.setProperty("id",  "3");
        hello3.setProperty("data",  "z");
        session.save();
        Node hello2 = hello3.addNode("hello2");
        hello2.setProperty("id",  "2");
        hello2.setProperty("data",  "y");
        session.save();
        ValueFactory vf = session.getValueFactory();
        QueryManager qm = session.getWorkspace().getQueryManager();
        Query q = qm.createQuery("select id from [nt:base] where data >= $data order by id", Query.JCR_SQL2);
        q.bindValue("data", vf.createValue("x"));
        for (int i = -1; i < 5; i++) {
            QueryResult r = q.execute();
            RowIterator it = r.getRows();
            assertEquals(3, r.getRows().getSize());
            assertEquals(3, r.getNodes().getSize());
            Row row;
            try {
                it.skip(i);
                assertTrue(i >= 0 && i <= 3);
            } catch (IllegalArgumentException e) {
                assertEquals(-1, i);
            } catch (NoSuchElementException e) {
                assertTrue(i >= 2);
            }
            if (i <= 0) {
                assertTrue(it.hasNext());
                row = it.nextRow();
                assertEquals("1", row.getValue("id").getString());
            }
            if (i <= 1) {
                assertTrue(it.hasNext());
                row = it.nextRow();
                assertEquals("2", row.getValue("id").getString());
            }
            if (i <= 2) {
                assertTrue(it.hasNext());
                row = it.nextRow();
                assertEquals("3", row.getValue("id").getString());
            }
            assertFalse(it.hasNext());
        }
    }
    
    @Test
    public void limit() throws RepositoryException {
        Session session = getAdminSession();
        Node hello1 = session.getRootNode().addNode("hello1");
        hello1.setProperty("id",  "1");
        hello1.setProperty("data",  "x");
        session.save();
        Node hello3 = session.getRootNode().addNode("hello3");
        hello3.setProperty("id",  "3");
        hello3.setProperty("data",  "z");
        session.save();
        Node hello2 = session.getRootNode().addNode("hello2");
        hello2.setProperty("id",  "2");
        hello2.setProperty("data",  "y");
        session.save();
        ValueFactory vf = session.getValueFactory();
        QueryManager qm = session.getWorkspace().getQueryManager();
        Query q = qm.createQuery("select id from [nt:base] where data >= $data order by id", Query.JCR_SQL2);
        q.bindValue("data", vf.createValue("x"));
        for (int limit = 0; limit < 5; limit++) {
            q.setLimit(limit);
            for (int offset = 0; offset < 3; offset++) {
                q.setOffset(offset);
                QueryResult r = q.execute();
                RowIterator it = r.getRows();
                int l = Math.min(Math.max(0, 3 - offset), limit);
                assertEquals(l, r.getRows().getSize());
                assertEquals(l, r.getNodes().getSize());
                Row row;
                
                for (int x = offset + 1, i = 0; i < limit && x < 4; i++, x++) {
                    assertTrue(it.hasNext());
                    row = it.nextRow();
                    assertEquals("" + x, row.getValue("id").getString());
                }
                assertFalse(it.hasNext());
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void nodeTypeConstraint() throws Exception {
        Session session = getAdminSession();
        Node root = session.getRootNode();
        Node folder1 = root.addNode("folder1", "nt:folder");
        Node folder2 = root.addNode("folder2", "nt:folder");
        JcrUtils.putFile(folder1, "file", "text/plain",
                new ByteArrayInputStream("foo bar".getBytes("UTF-8")));
        folder2.addNode("folder3", "nt:folder");
        session.save();

        QueryManager qm = session.getWorkspace().getQueryManager();
        Query q = qm.createQuery("//element(*, nt:folder)", Query.XPATH);
        Set<String> paths = new HashSet<String>();
        for (RowIterator it = q.execute().getRows(); it.hasNext();) {
            paths.add(it.nextRow().getPath());
        }
        assertEquals(new HashSet<String>(Arrays.asList("/folder1", "/folder2", "/folder2/folder3")),
                paths);
    }
    
    @Test
    public void noLiterals() throws RepositoryException {
        Session session = getAdminSession();
        ValueFactory vf = session.getValueFactory();
        QueryManager qm = session.getWorkspace().getQueryManager();
        
        // insecure
        try {
            Query q = qm.createQuery(
                    "select text from [nt:base] where password = 'x'", 
                    Query.JCR_SQL2 + "-noLiterals");
            q.execute();
            fail();
        } catch (InvalidQueryException e) {
            assertTrue(e.toString(), e.toString().indexOf(
                    "literals of this type not allowed") > 0);
        }

        // secure
        Query q = qm.createQuery(
                "select text from [nt:base] where password = $p", 
                Query.JCR_SQL2 + "-noLiterals");
        q.bindValue("p", vf.createValue("x"));
        q.execute();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void fnNameEncoding() throws Exception {
        Session session = getAdminSession();
        session.getRootNode().addNode("123456_test_name");
        session.save();

        QueryManager qm = session.getWorkspace().getQueryManager();
        Query q;

        q = qm.createQuery("//*[jcr:like(fn:name(), '%123456%')]", Query.XPATH);
        assertEquals("/123456_test_name", getPaths(q));

        q = qm.createQuery("//*[fn:name() = '123456_test_name']", Query.XPATH);
        assertEquals("", getPaths(q));
    }

    /**
     * OAK-1093
     */
    @Test
    public void getValuesOnMvp() throws RepositoryException {
        Session session = getAdminSession();
        Node hello = session.getRootNode().addNode("hello");
        hello.setProperty("id", "1");
        hello.setProperty("properties", new String[] { "p1", "p2" });
        session.save();

        QueryManager qm = session.getWorkspace().getQueryManager();
        Query q = qm.createQuery("select properties from [nt:base] where id = 1",
                Query.JCR_SQL2);

        QueryResult r = q.execute();
        RowIterator it = r.getRows();
        assertTrue(it.hasNext());
        Row row = it.nextRow();
        assertEquals("p1 p2", row.getValues()[0].getString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void xpathEscapeTest() throws RepositoryException {
        Session writer = createAdminSession();
        Session reader = createAdminSession();
        try {
            Node rootNode = writer.getRootNode();
            Node node = rootNode.addNode("test", "nt:unstructured");
            node.addNode(".tokens");
            writer.save();

            QueryManager qm = reader.getWorkspace().getQueryManager();
            Query q = qm.createQuery("/jcr:root//*[_x002e_tokens/@jcr:primaryType]", Query.XPATH);
            NodeIterator res = q.execute().getNodes();
            assertEquals(1, res.getSize());
        } finally {
            if (reader != null) {
                reader.logout();
            }
            if (writer != null) {
                writer.logout();
            }
        }
    }

    @Test
    public void testOak1096() throws RepositoryException {
        Session writer = createAdminSession();
        Session reader = createAdminSession();
        try {
            Node rootNode = writer.getRootNode();
            Node node = rootNode.addNode("test", "nt:unstructured");
            node.setProperty("text", "find me");
            writer.save();

            QueryManager qm = reader.getWorkspace().getQueryManager();
            Query q = qm.createQuery("select * from 'nt:base' where contains(*, 'find me')", Query.JCR_SQL2);
            NodeIterator res = q.execute().getNodes();
            assertEquals("False amount of hits", 1, res.getSize());
        } finally {
            if (reader != null) {
                reader.logout();
            }
            if (writer != null) {
                writer.logout();
            }
        }
    }
    
    @Test
    public void testOak1128() throws RepositoryException {
        Session session = createAdminSession();
        Node p = session.getRootNode().addNode("etc");
        p.addNode("p1");
        Node r = p.addNode("p2").addNode("r", "nt:unstructured");
        r.setProperty("nt:resourceType", "test");
        session.save();
        Query q = session.getWorkspace().getQueryManager().createQuery(
                "/jcr:root/etc//*["+
                        "(@jcr:primaryType = 'a'  or @jcr:primaryType = 'b') "+
                        "or @nt:resourceType = 'test']", "xpath");
        QueryResult qr = q.execute();
        NodeIterator ni = qr.getNodes();
        Node n = ni.nextNode();
        assertEquals("/etc/p2/r", n.getPath());
    }

    @Test
    public void testOak1171() throws RepositoryException {
        Session session = createAdminSession();
        Node p = session.getRootNode().addNode("etc");
        p.addNode("p1").setProperty("title", "test");
        p.addNode("p2").setProperty("title", 1);
        session.save();

        Query q = session.getWorkspace().getQueryManager()
                .createQuery("//*[@title = 'test']", "xpath");
        QueryResult qr = q.execute();

        NodeIterator ni = qr.getNodes();
        assertTrue(ni.hasNext());
        Node n = ni.nextNode();
        assertEquals("/etc/p1", n.getPath());
        assertFalse(ni.hasNext());
    }

    @Test
    public void testOak1354() throws Exception {
        Session session = createAdminSession();
        NodeTypeManager manager = session.getWorkspace().getNodeTypeManager();

        if (!manager.hasNodeType("mymixinOak1354")) {
            StringBuilder defs = new StringBuilder();
            defs.append("[mymixinOak1354]\n");
            defs.append("  mixin");
            Reader cndReader = new InputStreamReader(new ByteArrayInputStream(defs.toString().getBytes()));
            CndImporter.registerNodeTypes(cndReader, session);
        }
        Node p = session.getRootNode().addNode("one");
        p.addMixin("mymixinOak1354");
        session.save();

        Query q = session.getWorkspace().getQueryManager()
                .createQuery("SELECT * FROM [mymixinOak1354]", Query.JCR_SQL2);
        QueryResult qr = q.execute();

        NodeIterator ni = qr.getNodes();
        assertTrue(ni.hasNext());
        Node n = ni.nextNode();
        assertEquals("/one", n.getPath());
        assertFalse(ni.hasNext());
    }

}
