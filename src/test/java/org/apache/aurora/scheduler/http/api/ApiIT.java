/**
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
package org.apache.aurora.scheduler.http.api;

import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Bytes;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.sun.jersey.api.client.ClientResponse;

import org.apache.aurora.gen.Response;
import org.apache.aurora.scheduler.http.AbstractJettyTest;
import org.apache.aurora.scheduler.thrift.aop.AnnotatedAuroraAdmin;
import org.apache.thrift.protocol.TJSONProtocol;
import org.junit.Before;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import static javax.ws.rs.core.HttpHeaders.ETAG;
import static javax.ws.rs.core.HttpHeaders.IF_NONE_MATCH;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

public class ApiIT extends AbstractJettyTest {
  private static final String JSON_FIXTURE = "[1,\"getRoleSummary\",1,0,{}]";
  private AnnotatedAuroraAdmin thrift;

  @Before
  public void setUp() {
    thrift = createMock(AnnotatedAuroraAdmin.class);
  }

  @Override
  protected Module getChildServletModule() {
    return Modules.combine(
        new ApiModule(),
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(AnnotatedAuroraAdmin.class).toInstance(thrift);
          }
        });
  }

  @Test
  public void testGzipFilterApplied() throws Exception {
    expect(thrift.getRoleSummary()).andReturn(new Response());

    replayAndStart();

    ClientResponse response = getRequestBuilder(ApiModule.API_PATH)
        .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
        .type("application/x-thrift")
        .post(ClientResponse.class, JSON_FIXTURE);

    assertEquals(SC_OK, response.getStatus());
    assertEquals("gzip", response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING));
  }

  @Test
  public void testThriftJsonAccepted() throws Exception {
    expect(thrift.getRoleSummary()).andReturn(new Response());

    replayAndStart();

    ClientResponse response = getPlainRequestBuilder(ApiModule.API_PATH)
        .type("application/vnd.apache.thrift.json")
        .accept("application/vnd.apache.thrift.json")
        .post(ClientResponse.class, JSON_FIXTURE);

    assertEquals(SC_OK, response.getStatus());
    assertEquals(
        "application/vnd.apache.thrift.json",
        response.getHeaders().getFirst(CONTENT_TYPE));
  }

  @Test
  public void testUnknownContentTypeRejected() throws Exception {
    replayAndStart();

    ClientResponse response = getRequestBuilder(ApiModule.API_PATH)
        .type(MediaType.TEXT_HTML_TYPE)
        .post(ClientResponse.class, JSON_FIXTURE);

    assertEquals(SC_UNSUPPORTED_MEDIA_TYPE, response.getStatus());
  }

  @Test
  public void testBinaryContentTypeAccepted() throws Exception {
    expect(thrift.getRoleSummary()).andReturn(new Response());

    replayAndStart();

    // This fixture represents a 'getRoleSummary' call encoded as binary thrift.
    List<Integer> fixture = ImmutableList.<Integer>builder()
        .addAll(ImmutableList.of(-128, 1, 0, 1, 0, 0, 0, 14, 103))
        .addAll(ImmutableList.of(101, 116, 82, 111, 108, 101, 83, 117, 109))
        .addAll(ImmutableList.of(109, 97, 114, 121, 0, 0, 0, 1, 0))
        .addAll(ImmutableList.of(0, 0, 0, 0, 0))
        .build();

    // Note the array has to be exactly 27 bytes long.
    byte[] rawBytes = Arrays.copyOf(Bytes.toArray(fixture), 27);

    ClientResponse response = getPlainRequestBuilder(ApiModule.API_PATH)
        .type("application/vnd.apache.thrift.binary")
        .accept("application/vnd.apache.thrift.binary")
        .post(ClientResponse.class, rawBytes);

    assertEquals(SC_OK, response.getStatus());
    assertEquals(
        "application/vnd.apache.thrift.binary",
        response.getHeaders().getFirst(CONTENT_TYPE));
  }

  @Test
  public void testETagOnResponse() throws Exception {
    expect(thrift.getRoleSummary()).andReturn(new Response());
    String exepectedResult = "[1,\"getRoleSummary\",2,0,{\"0\":{\"rec\":{}}}]";
    String expectedEtag = Long.toString(Hashing.goodFastHash(64).hashBytes(exepectedResult.getBytes(UTF_8)).asLong());
    expectedEtag = "\"" + expectedEtag + "\"";

    replayAndStart();

    ClientResponse response = getPlainRequestBuilder(ApiModule.API_PATH)
        .type("application/vnd.apache.thrift.json")
        .accept("application/vnd.apache.thrift.json")
        .post(ClientResponse.class, JSON_FIXTURE);

    assertEquals(exepectedResult, response.getEntity(String.class));
    assertEquals(SC_OK, response.getStatus());
    assertEquals(expectedEtag, response.getHeaders().getFirst(ETAG));
  }

  @Test
  public void testAcceptIfNoneMatchHeader() throws Exception {
    expect(thrift.getRoleSummary()).andReturn(new Response());
    expect(thrift.getRoleSummary()).andReturn(new Response());

    replayAndStart();

    ClientResponse response = getPlainRequestBuilder(ApiModule.API_PATH)
        .type("application/vnd.apache.thrift.json")
        .accept("application/vnd.apache.thrift.json")
        .post(ClientResponse.class, JSON_FIXTURE);
    assertEquals(SC_OK, response.getStatus());

    String etag = response.getHeaders().getFirst(ETAG);

    response = getPlainRequestBuilder(ApiModule.API_PATH)
        .type("application/vnd.apache.thrift.json")
        .accept("application/vnd.apache.thrift.json")
        .header(IF_NONE_MATCH, etag)
        .post(ClientResponse.class, JSON_FIXTURE);

    assertEquals(SC_NOT_MODIFIED, response.getStatus());
    assertEquals(etag, response.getHeaders().getFirst(ETAG));
  }

  @Test
  public void testAcceptIfNoneMatchHeaderWithGzip() throws Exception {
    // The Gzip handler in jetty modifies etag and if-none-match headers

    expect(thrift.getRoleSummary()).andReturn(new Response());
    expect(thrift.getRoleSummary()).andReturn(new Response());

    replayAndStart();

    ClientResponse response = getPlainRequestBuilder(ApiModule.API_PATH)
        .type("application/vnd.apache.thrift.json")
        .accept("application/vnd.apache.thrift.json")
        .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
        .post(ClientResponse.class, JSON_FIXTURE);
    assertEquals(SC_OK, response.getStatus());

    String etag = response.getHeaders().getFirst(ETAG);

    response = getPlainRequestBuilder(ApiModule.API_PATH)
        .type("application/vnd.apache.thrift.json")
        .accept("application/vnd.apache.thrift.json")
        .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
        .header(IF_NONE_MATCH, etag)
        .post(ClientResponse.class, JSON_FIXTURE);

    assertEquals(SC_NOT_MODIFIED, response.getStatus());
    assertEquals(etag, response.getHeaders().getFirst(ETAG));


  }
}
