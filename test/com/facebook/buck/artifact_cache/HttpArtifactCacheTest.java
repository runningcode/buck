/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.artifact_cache;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.event.BuckEvent;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.event.DefaultBuckEventBus;
import com.facebook.buck.event.listener.ArtifactCacheTestUtils;
import com.facebook.buck.io.LazyPath;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.slb.HttpResponse;
import com.facebook.buck.slb.HttpService;
import com.facebook.buck.slb.OkHttpResponseWrapper;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.timing.IncrementingFakeClock;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

public class HttpArtifactCacheTest {

  private static final String SERVER = "http://localhost";

  private static final BuckEventBus BUCK_EVENT_BUS =
      new DefaultBuckEventBus(new IncrementingFakeClock(), new BuildId());
  private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");
  private static final ListeningExecutorService DIRECT_EXECUTOR_SERVICE =
      MoreExecutors.newDirectExecutorService();
  private static final String ERROR_TEXT_TEMPLATE =
      "{cache_name} encountered an error: {error_message}";

  private NetworkCacheArgs.Builder argsBuilder;

  private ResponseBody createResponseBody(
      ImmutableSet<RuleKey> ruleKeys,
      ImmutableMap<String, String> metadata,
      ByteSource source,
      String data)
      throws IOException {

    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(out)) {
      byte[] rawMetadata =
          HttpArtifactCacheBinaryProtocol.createMetadataHeader(ruleKeys, metadata, source);
      dataOut.writeInt(rawMetadata.length);
      dataOut.write(rawMetadata);
      dataOut.write(data.getBytes(Charsets.UTF_8));
      return ResponseBody.create(OCTET_STREAM, out.toByteArray());
    }
  }

  private static HttpArtifactCacheEvent.Finished.Builder createFinishedEventBuilder() {
    HttpArtifactCacheEvent.Started started =
        ArtifactCacheTestUtils.newFetchConfiguredStartedEvent(new RuleKey("1234"));
    return HttpArtifactCacheEvent.newFinishedEventBuilder(started);
  }

  /**
   * Helper for creating simple HttpService instances from lambda functions.
   *
   * <p>This interface exists to allow lambda syntax.
   *
   * <p>Usage: {@code withMakeRequest((path, request) -> ...)}.
   */
  private interface FakeHttpService {
    HttpResponse makeRequest(String path, Request.Builder request) throws IOException;
  }

  private static HttpService withMakeRequest(FakeHttpService body) {
    return new HttpService() {
      @Override
      public HttpResponse makeRequest(String path, Request.Builder request) throws IOException {
        return body.makeRequest(path, request);
      }

      @Override
      public void close() {}
    };
  }

  @Before
  public void setUp() {
    this.argsBuilder =
        NetworkCacheArgs.builder()
            .setCacheName("http")
            .setCacheMode(ArtifactCacheMode.http)
            .setRepository("some_repository")
            .setScheduleType("some_schedule")
            .setFetchClient(withMakeRequest((a, b) -> null))
            .setStoreClient(withMakeRequest((a, b) -> null))
            .setCacheReadMode(CacheReadMode.READWRITE)
            .setProjectFilesystem(new FakeProjectFilesystem())
            .setBuckEventBus(BUCK_EVENT_BUS)
            .setHttpWriteExecutorService(DIRECT_EXECUTOR_SERVICE)
            .setErrorTextTemplate(ERROR_TEXT_TEMPLATE)
            .setDistributedBuildModeEnabled(false);
  }

  @Test
  public void testFetchNotFound() throws Exception {
    final List<Response> responseList = new ArrayList<>();
    argsBuilder.setFetchClient(
        withMakeRequest(
            (path, requestBuilder) -> {
              Response response =
                  new Response.Builder()
                      .code(HttpURLConnection.HTTP_NOT_FOUND)
                      .body(
                          ResponseBody.create(
                              MediaType.parse("application/octet-stream"), "extraneous"))
                      .protocol(Protocol.HTTP_1_1)
                      .request(requestBuilder.url(SERVER + path).build())
                      .build();
              responseList.add(response);
              return new OkHttpResponseWrapper(response);
            }));
    HttpArtifactCache cache = new HttpArtifactCache(argsBuilder.build());
    CacheResult result =
        cache.fetch(
            new RuleKey("00000000000000000000000000000000"),
            LazyPath.ofInstance(Paths.get("output/file")));
    assertEquals(result.getType(), CacheResultType.MISS);
    assertTrue("response wasn't fully read!", responseList.get(0).body().source().exhausted());
    cache.close();
  }

  @Test
  public void testFetchOK() throws Exception {
    Path output = Paths.get("output/file");
    final String data = "test";
    final RuleKey ruleKey = new RuleKey("00000000000000000000000000000000");
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    final List<Response> responseList = new ArrayList<>();
    argsBuilder.setProjectFilesystem(filesystem);
    argsBuilder.setFetchClient(
        withMakeRequest(
            (path, requestBuilder) -> {
              Request request = requestBuilder.url(SERVER + path).build();
              Response response =
                  new Response.Builder()
                      .request(request)
                      .protocol(Protocol.HTTP_1_1)
                      .code(HttpURLConnection.HTTP_OK)
                      .body(
                          createResponseBody(
                              ImmutableSet.of(ruleKey),
                              ImmutableMap.of(),
                              ByteSource.wrap(data.getBytes(Charsets.UTF_8)),
                              data))
                      .build();
              responseList.add(response);
              return new OkHttpResponseWrapper(response);
            }));

    HttpArtifactCache cache = new HttpArtifactCache(argsBuilder.build());
    CacheResult result = cache.fetch(ruleKey, LazyPath.ofInstance(output));
    assertEquals(result.cacheError().orElse(""), CacheResultType.HIT, result.getType());
    assertEquals(Optional.of(data), filesystem.readFileIfItExists(output));
    assertEquals(result.artifactSizeBytes(), Optional.of(filesystem.getFileSize(output)));
    assertTrue("response wasn't fully read!", responseList.get(0).body().source().exhausted());
    cache.close();
  }

  @Test
  public void testFetchUrl() throws Exception {
    final RuleKey ruleKey = new RuleKey("00000000000000000000000000000000");
    final String expectedUri = "/artifacts/key/00000000000000000000000000000000";

    argsBuilder.setFetchClient(
        withMakeRequest(
            (path, requestBuilder) -> {
              Request request = requestBuilder.url(SERVER + path).build();
              assertEquals(expectedUri, request.url().encodedPath());
              return new OkHttpResponseWrapper(
                  new Response.Builder()
                      .request(request)
                      .protocol(Protocol.HTTP_1_1)
                      .code(HttpURLConnection.HTTP_OK)
                      .body(
                          createResponseBody(
                              ImmutableSet.of(ruleKey),
                              ImmutableMap.of(),
                              ByteSource.wrap(new byte[0]),
                              "data"))
                      .build());
            }));
    HttpArtifactCache cache = new HttpArtifactCache(argsBuilder.build());
    cache.fetch(ruleKey, LazyPath.ofInstance(Paths.get("output/file")));
    cache.close();
  }

  @Test
  public void testFetchBadChecksum() throws Exception {
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    final RuleKey ruleKey = new RuleKey("00000000000000000000000000000000");
    final List<Response> responseList = new ArrayList<>();
    argsBuilder.setFetchClient(
        withMakeRequest(
            (path, requestBuilder) -> {
              Request request = requestBuilder.url(SERVER + path).build();
              Response response =
                  new Response.Builder()
                      .request(request)
                      .protocol(Protocol.HTTP_1_1)
                      .code(HttpURLConnection.HTTP_OK)
                      .body(
                          createResponseBody(
                              ImmutableSet.of(ruleKey),
                              ImmutableMap.of(),
                              ByteSource.wrap(new byte[0]),
                              "data"))
                      .build();
              responseList.add(response);
              return new OkHttpResponseWrapper(response);
            }));
    HttpArtifactCache cache = new HttpArtifactCache(argsBuilder.build());
    Path output = Paths.get("output/file");
    CacheResult result = cache.fetch(ruleKey, LazyPath.ofInstance(output));
    assertEquals(CacheResultType.ERROR, result.getType());
    assertEquals(Optional.empty(), filesystem.readFileIfItExists(output));
    assertTrue("response wasn't fully read!", responseList.get(0).body().source().exhausted());
    cache.close();
  }

  @Test
  public void testFetchExtraPayload() throws Exception {
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    final RuleKey ruleKey = new RuleKey("00000000000000000000000000000000");
    final List<Response> responseList = new ArrayList<>();
    argsBuilder.setFetchClient(
        withMakeRequest(
            (path, requestBuilder) -> {
              Request request = requestBuilder.url(SERVER + path).build();
              Response response =
                  new Response.Builder()
                      .request(request)
                      .protocol(Protocol.HTTP_1_1)
                      .code(HttpURLConnection.HTTP_OK)
                      .body(
                          createResponseBody(
                              ImmutableSet.of(ruleKey),
                              ImmutableMap.of(),
                              ByteSource.wrap("more data than length".getBytes(Charsets.UTF_8)),
                              "small"))
                      .build();
              responseList.add(response);
              return new OkHttpResponseWrapper(response);
            }));
    HttpArtifactCache cache = new HttpArtifactCache(argsBuilder.build());
    Path output = Paths.get("output/file");
    CacheResult result = cache.fetch(ruleKey, LazyPath.ofInstance(output));
    assertEquals(CacheResultType.ERROR, result.getType());
    assertEquals(Optional.empty(), filesystem.readFileIfItExists(output));
    assertTrue("response wasn't fully read!", responseList.get(0).body().source().exhausted());
    cache.close();
  }

  @Test
  public void testFetchIOException() throws Exception {
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    argsBuilder.setFetchClient(
        withMakeRequest(
            ((path, requestBuilder) -> {
              throw new IOException();
            })));
    HttpArtifactCache cache = new HttpArtifactCache(argsBuilder.build());
    Path output = Paths.get("output/file");
    CacheResult result =
        cache.fetch(new RuleKey("00000000000000000000000000000000"), LazyPath.ofInstance(output));
    assertEquals(CacheResultType.ERROR, result.getType());
    assertEquals(Optional.empty(), filesystem.readFileIfItExists(output));
    cache.close();
  }

  @Test
  public void testStore() throws Exception {
    final RuleKey ruleKey = new RuleKey("00000000000000000000000000000000");
    final String data = "data";
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    Path output = Paths.get("output/file");
    filesystem.writeContentsToPath(data, output);
    final AtomicBoolean hasCalled = new AtomicBoolean(false);
    argsBuilder.setProjectFilesystem(filesystem);
    argsBuilder.setStoreClient(
        withMakeRequest(
            ((path, requestBuilder) -> {
              Request request = requestBuilder.url(SERVER).build();
              hasCalled.set(true);

              Buffer buf = new Buffer();
              request.body().writeTo(buf);
              byte[] actualData = buf.readByteArray();

              byte[] expectedData;
              try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                  DataOutputStream dataOut = new DataOutputStream(out)) {
                dataOut.write(
                    HttpArtifactCacheBinaryProtocol.createKeysHeader(ImmutableSet.of(ruleKey)));
                byte[] metadata =
                    HttpArtifactCacheBinaryProtocol.createMetadataHeader(
                        ImmutableSet.of(ruleKey),
                        ImmutableMap.of(),
                        ByteSource.wrap(data.getBytes(Charsets.UTF_8)));
                dataOut.writeInt(metadata.length);
                dataOut.write(metadata);
                dataOut.write(data.getBytes(Charsets.UTF_8));
                expectedData = out.toByteArray();
              }

              assertArrayEquals(expectedData, actualData);

              Response response =
                  new Response.Builder()
                      .body(createDummyBody())
                      .code(HttpURLConnection.HTTP_ACCEPTED)
                      .protocol(Protocol.HTTP_1_1)
                      .request(request)
                      .build();
              return new OkHttpResponseWrapper(response);
            })));
    HttpArtifactCache cache = new HttpArtifactCache(argsBuilder.build());
    cache.storeImpl(
        ArtifactInfo.builder().addRuleKeys(ruleKey).build(), output, createFinishedEventBuilder());
    assertTrue(hasCalled.get());
    cache.close();
  }

  @Test(expected = IOException.class)
  public void testStoreIOException() throws Exception {
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    Path output = Paths.get("output/file");
    filesystem.writeContentsToPath("data", output);
    argsBuilder.setStoreClient(
        withMakeRequest(
            ((path, requestBuilder) -> {
              throw new IOException();
            })));
    HttpArtifactCache cache = new HttpArtifactCache(argsBuilder.build());
    cache.storeImpl(
        ArtifactInfo.builder().addRuleKeys(new RuleKey("00000000000000000000000000000000")).build(),
        output,
        createFinishedEventBuilder());
    cache.close();
  }

  @Test
  public void testStoreMultipleKeys() throws Exception {
    final RuleKey ruleKey1 = new RuleKey("00000000000000000000000000000000");
    final RuleKey ruleKey2 = new RuleKey("11111111111111111111111111111111");
    final String data = "data";
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    Path output = Paths.get("output/file");
    filesystem.writeContentsToPath(data, output);
    final Set<RuleKey> stored = Sets.newHashSet();
    argsBuilder.setProjectFilesystem(filesystem);
    argsBuilder.setStoreClient(
        withMakeRequest(
            ((path, requestBuilder) -> {
              Request request = requestBuilder.url(SERVER).build();
              Buffer buf = new Buffer();
              request.body().writeTo(buf);
              try (DataInputStream in =
                  new DataInputStream(new ByteArrayInputStream(buf.readByteArray()))) {
                int keys = in.readInt();
                for (int i = 0; i < keys; i++) {
                  stored.add(new RuleKey(in.readUTF()));
                }
              }
              Response response =
                  new Response.Builder()
                      .body(createDummyBody())
                      .code(HttpURLConnection.HTTP_ACCEPTED)
                      .protocol(Protocol.HTTP_1_1)
                      .request(request)
                      .build();
              return new OkHttpResponseWrapper(response);
            })));
    HttpArtifactCache cache = new HttpArtifactCache(argsBuilder.build());
    cache.storeImpl(
        ArtifactInfo.builder().addRuleKeys(ruleKey1, ruleKey2).build(),
        output,
        createFinishedEventBuilder());
    assertThat(stored, Matchers.containsInAnyOrder(ruleKey1, ruleKey2));
    cache.close();
  }

  @Test
  public void testFetchWrongKey() throws Exception {
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    final RuleKey ruleKey = new RuleKey("00000000000000000000000000000000");
    final RuleKey otherRuleKey = new RuleKey("11111111111111111111111111111111");
    final String data = "data";

    argsBuilder.setFetchClient(
        withMakeRequest(
            ((path, requestBuilder) -> {
              Request request = requestBuilder.url(SERVER + path).build();
              Response response =
                  new Response.Builder()
                      .request(request)
                      .protocol(Protocol.HTTP_1_1)
                      .code(HttpURLConnection.HTTP_OK)
                      .body(
                          createResponseBody(
                              ImmutableSet.of(otherRuleKey),
                              ImmutableMap.of(),
                              ByteSource.wrap(data.getBytes(Charsets.UTF_8)),
                              data))
                      .build();
              return new OkHttpResponseWrapper(response);
            })));
    HttpArtifactCache cache = new HttpArtifactCache(argsBuilder.build());
    Path output = Paths.get("output/file");
    CacheResult result = cache.fetch(ruleKey, LazyPath.ofInstance(output));
    assertEquals(CacheResultType.ERROR, result.getType());
    assertEquals(Optional.empty(), filesystem.readFileIfItExists(output));
    cache.close();
  }

  @Test
  public void testFetchMetadata() throws Exception {
    Path output = Paths.get("output/file");
    final String data = "test";
    final RuleKey ruleKey = new RuleKey("00000000000000000000000000000000");
    final ImmutableMap<String, String> metadata = ImmutableMap.of("some", "metadata");
    argsBuilder.setFetchClient(
        withMakeRequest(
            ((path, requestBuilder) -> {
              Request request = requestBuilder.url(SERVER + path).build();
              Response response =
                  new Response.Builder()
                      .request(request)
                      .protocol(Protocol.HTTP_1_1)
                      .code(HttpURLConnection.HTTP_OK)
                      .body(
                          createResponseBody(
                              ImmutableSet.of(ruleKey),
                              metadata,
                              ByteSource.wrap(data.getBytes(Charsets.UTF_8)),
                              data))
                      .build();
              return new OkHttpResponseWrapper(response);
            })));
    HttpArtifactCache cache = new HttpArtifactCache(argsBuilder.build());
    CacheResult result = cache.fetch(ruleKey, LazyPath.ofInstance(output));
    assertEquals(CacheResultType.HIT, result.getType());
    assertEquals(metadata, result.getMetadata());
    cache.close();
  }

  @Test
  public void errorTextReplaced() throws InterruptedException {
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    final String cacheName = "http cache";
    final RuleKey ruleKey = new RuleKey("00000000000000000000000000000000");
    final RuleKey otherRuleKey = new RuleKey("11111111111111111111111111111111");
    final String data = "data";
    final AtomicBoolean consoleEventReceived = new AtomicBoolean(false);
    argsBuilder
        .setCacheName(cacheName)
        .setProjectFilesystem(filesystem)
        .setBuckEventBus(
            new DefaultBuckEventBus(new IncrementingFakeClock(), new BuildId()) {
              @Override
              public void post(BuckEvent event) {
                if (event instanceof ConsoleEvent) {
                  consoleEventReceived.set(true);
                  ConsoleEvent consoleEvent = (ConsoleEvent) event;
                  assertThat(consoleEvent.getMessage(), Matchers.containsString(cacheName));
                  assertThat(
                      consoleEvent.getMessage(), Matchers.containsString("incorrect key name"));
                }
              }
            })
        .setFetchClient(
            withMakeRequest(
                (path, requestBuilder) -> {
                  Request request = requestBuilder.url(SERVER + path).build();
                  Response response =
                      new Response.Builder()
                          .request(request)
                          .protocol(Protocol.HTTP_1_1)
                          .code(HttpURLConnection.HTTP_OK)
                          .body(
                              createResponseBody(
                                  ImmutableSet.of(otherRuleKey),
                                  ImmutableMap.of(),
                                  ByteSource.wrap(data.getBytes(Charsets.UTF_8)),
                                  data))
                          .build();
                  return new OkHttpResponseWrapper(response);
                }));
    HttpArtifactCache cache = new HttpArtifactCache(argsBuilder.build());
    Path output = Paths.get("output/file");
    CacheResult result = cache.fetch(ruleKey, LazyPath.ofInstance(output));
    assertEquals(CacheResultType.ERROR, result.getType());
    assertEquals(Optional.empty(), filesystem.readFileIfItExists(output));
    assertTrue(consoleEventReceived.get());
    cache.close();
  }

  private static ResponseBody createDummyBody() {
    return ResponseBody.create(MediaType.parse("text/plain"), "SUCCESS");
  }
}
