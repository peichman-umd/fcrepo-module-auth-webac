/**
 * Copyright 2015 DuraSpace, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.integration.auth.webac;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Peter Eichman
 * @since September 4, 2015
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("/spring-test/test-container.xml")
public class WebACRecipesIT {

    private static Logger logger = getLogger(WebACRecipesIT.class);

    protected static final int SERVER_PORT = Integer.parseInt(System.getProperty("fcrepo.dynamic.test.port", "8080"));

    protected static final String HOSTNAME = "localhost";

    protected static final String serverAddress = "http://" + HOSTNAME + ":" + SERVER_PORT + "/rest/";

    protected final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();

    protected static CloseableHttpClient client;

    public WebACRecipesIT() {
        connectionManager.setMaxTotal(Integer.MAX_VALUE);
        connectionManager.setDefaultMaxPerRoute(20);
        connectionManager.closeIdleConnections(3, TimeUnit.SECONDS);
        client = HttpClientBuilder.create().setConnectionManager(connectionManager).build();
    }

    @Before
    public void setUp() throws ClientProtocolException, IOException {
        final String credentials = "username:password";

        logger.debug("setting up ACLs and authorization rules");

        ingestAcl(credentials, "/acls/01/acl.ttl", "/acls/01/authorization.ttl");
        ingestAcl(credentials, "/acls/02/acl.ttl", "/acls/02/authorization.ttl");
        ingestAcl(credentials, "/acls/03/acl.ttl", "/acls/03/auth_open.ttl", "/acls/03/auth_restricted.ttl");
        ingestAcl(credentials, "/acls/04/acl.ttl", "/acls/04/auth1.ttl", "/acls/04/auth2.ttl");
        ingestAcl(credentials, "/acls/05/acl.ttl", "/acls/05/auth_open.ttl", "/acls/05/auth_restricted.ttl");

        logger.debug("setup complete");
    }

    /**
     * Convenience method to create an ACL with 0 or more authorization resources in the respository.
     *
     * @param credentials
     * @param aclResourcePath
     * @param authorizationResourcePaths
     * @return URI of the created ACL
     * @throws ClientProtocolException
     * @throws IOException
     */
    private String ingestAcl(final String credentials, final String aclResourcePath,
            final String... authorizationResourcePaths) throws ClientProtocolException, IOException {

        // create the ACL
        final HttpResponse aclResponse = ingestTurtleResource(credentials, aclResourcePath, serverAddress);
        System.err.println(aclResponse.getStatusLine());

        // get the URI to the newly created resource
        final String aclURI = aclResponse.getFirstHeader("Location").getValue();

        // add all the authorizations
        for (final String authorizationResourcePath : authorizationResourcePaths) {
            final HttpResponse authzResponse = ingestTurtleResource(credentials, authorizationResourcePath, aclURI);
            System.err.println(authzResponse.getStatusLine());
        }

        return aclURI;
    }

    /**
     * Convenience method to POST the contents of a Turtle file to the repository to create a new resource. Returns
     * the HTTP response from that request. Throws an IOException if the server responds with anything other than a
     * 201 Created response code.
     *
     * @param credentials
     * @param path
     * @param requestURI
     * @return HTTP response to the create request.
     * @throws ClientProtocolException
     * @throws IOException
     */
    private HttpResponse ingestTurtleResource(final String credentials, final String path, final String requestURI)
            throws ClientProtocolException, IOException {
        final HttpPut postRequest = new HttpPut(requestURI);

        final String message = "POST to " + requestURI + " to create " + path;
        logger.debug(message);

        // in test configuration we don't need real passwords
        final String encCreds = new String(Base64.encodeBase64(credentials.getBytes()));
        final String basic = "Basic " + encCreds;
        postRequest.setHeader("Authorization", basic);

        final InputStream file = this.getClass().getResourceAsStream(path);
        final InputStreamEntity fileEntity = new InputStreamEntity(file);
        postRequest.setEntity(fileEntity);
        postRequest.setHeader("Content-Type", "text/turtle;charset=UTF-8");

        // XXX: this is currently failing in the test repository with a
        // "java.lang.VerifyError: Bad type on operand stack"
        // see https://gist.github.com/peichman-umd/7f2eb8833ef8cd0cdfc1#gistcomment-1566271
        final HttpResponse response = client.execute(postRequest);
        Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());

        return response;
    }

    @Test
    public void test() {
    }

}
