/*
 * Copyright 2011-2021 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.http.client.impl;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import io.gatling.http.client.HttpClient;
import io.gatling.http.client.HttpClientConfig;
import io.gatling.http.client.HttpListener;
import io.gatling.http.client.Request;
import io.gatling.http.client.body.is.InputStreamRequestBody;
import io.gatling.http.client.impl.br.BrotliDecoder;
import io.gatling.http.client.pool.ChannelPool;
import io.gatling.http.client.pool.ChannelPoolKey;
import io.gatling.http.client.pool.RemoteKey;
import io.gatling.http.client.proxy.ProxyServer;
import io.gatling.http.client.proxy.SockProxyServer;
import io.gatling.http.client.realm.DigestRealm;
import io.gatling.http.client.realm.Realm;
import io.gatling.http.client.ssl.Tls;
import io.gatling.http.client.uri.Uri;
import io.gatling.http.client.util.Pair;
import io.gatling.netty.util.Transports;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http2.*;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.AsciiString;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.*;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

public class DefaultHttpClient implements HttpClient {

  private static final AsciiString BR = new AsciiString("br");

  static {
    InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHttpClient.class);

  private static final String PINNED_HANDLER = "pinned";
  private static final String PROXY_HANDLER = "proxy";
  private static final String SSL_HANDLER = "ssl";
  public static final String HTTP_CLIENT_CODEC = "http";
  private static final String HTTP2_HANDLER = "http2";
  private static final String INFLATER_HANDLER = "inflater";
  private static final String CHUNKED_WRITER_HANDLER = "chunked-writer";
  private static final String DIGEST_AUTH_HANDLER = "digest";
  private static final String WS_OBJECT_AGGREGATOR = "ws-object-aggregator";
  private static final String WS_COMPRESSION = "ws-compression";
  private static final String WS_FRAME_AGGREGATOR = "ws-frame-aggregator";
  private static final String APP_WS_HANDLER = "app-ws";
  private static final String ALPN_HANDLER = "alpn";
  private static final String APP_HTTP2_HANDLER = "app-http2";

  public static final String APP_HTTP_HANDLER = "app-http";

  private HttpClientCodec newHttpClientCodec() {
    return new HttpClientCodec(
      4096,
      Integer.MAX_VALUE,
      8192,
      false,
      false,
      128);
  }

  private HttpContentDecompressor newHttpContentDecompressor() {
    return new HttpContentDecompressor() {

      @Override
      protected EmbeddedChannel newContentDecoder(String contentEncoding) throws Exception {
        if (Brotli4jLoader.isAvailable() && BR.contentEqualsIgnoreCase(contentEncoding)) {
          return new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
            ctx.channel().config(), new BrotliDecoder());
        } else {
          return super.newContentDecoder(contentEncoding);
        }
      }

      @Override
      protected String getTargetContentEncoding(String contentEncoding) {
        return contentEncoding;
      }
    };
  }

  private class EventLoopResources {

    private final Bootstrap http1Bootstrap;
    private final Bootstrap http2Bootstrap;
    private final Bootstrap wsBootstrap;
    private final ChannelPool channelPool;

    private void addHttpHandlers(Channel channel) {
      channel.pipeline()
        .addLast(HTTP_CLIENT_CODEC, newHttpClientCodec())
        .addLast(INFLATER_HANDLER, newHttpContentDecompressor())
        .addLast(CHUNKED_WRITER_HANDLER, new ChunkedWriteHandler())
        .addLast(APP_HTTP_HANDLER, new HttpAppHandler(DefaultHttpClient.this, channelPool, config));

      if (config.getAdditionalChannelInitializer() != null) {
        config.getAdditionalChannelInitializer().accept(channel);
      }
    }

    private void addWsHandlers(Channel channel) {
      channel.pipeline()
        .addLast(HTTP_CLIENT_CODEC, newHttpClientCodec())
        .addLast(WS_OBJECT_AGGREGATOR, new HttpObjectAggregator(Integer.MAX_VALUE))
        .addLast(WS_COMPRESSION, AllowClientNoContextWebSocketClientCompressionHandler.INSTANCE)
        .addLast(WS_FRAME_AGGREGATOR, new WebSocketFrameAggregator(Integer.MAX_VALUE))
        .addLast(APP_WS_HANDLER, new WebSocketHandler(config));

      if (config.getAdditionalChannelInitializer() != null) {
        config.getAdditionalChannelInitializer().accept(channel);
      }
    }

    private EventLoopResources(EventLoop eventLoop) {
      channelPool = new ChannelPool();
      long channelPoolIdleCleanerPeriod = config.getChannelPoolIdleCleanerPeriod();
      long idleTimeoutNanos = config.getChannelPoolIdleTimeout() * 1_000_000;
      eventLoop.scheduleWithFixedDelay(
        () -> channelPool.closeIdleChannels(idleTimeoutNanos),
        channelPoolIdleCleanerPeriod,
        channelPoolIdleCleanerPeriod,
        TimeUnit.MILLISECONDS);

      http1Bootstrap = new Bootstrap()
        .channelFactory(Transports.newSocketChannelFactory(config.isUseNativeTransport()))
        .group(eventLoop)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.getConnectTimeout())
        .option(ChannelOption.SO_REUSEADDR, config.isSoReuseAddress())
        .option(ChannelOption.TCP_NODELAY, config.isTcpNoDelay())
        .option(ChannelOption.SO_KEEPALIVE, config.isSoKeepAlive())
        .resolver(NoopAddressResolverGroup.INSTANCE)
        .handler(new ChannelInitializer<Channel>() {
          @Override
          protected void initChannel(Channel channel) {
            channel.pipeline().addLast(PINNED_HANDLER, NoopHandler.INSTANCE);
            addHttpHandlers(channel);
          }
        });

      http2Bootstrap = http1Bootstrap.clone().handler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel channel) {
          channel.pipeline()
            .addLast(PINNED_HANDLER, NoopHandler.INSTANCE)
            .addLast(CHUNKED_WRITER_HANDLER, new ChunkedWriteHandler());

          if (config.getAdditionalChannelInitializer() != null) {
            config.getAdditionalChannelInitializer().accept(channel);
          }
        }
      });

      wsBootstrap = http1Bootstrap.clone().handler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel channel) {
          channel.pipeline().addLast(PINNED_HANDLER, NoopHandler.INSTANCE);
          addWsHandlers(channel);
        }
      });
    }

    private Bootstrap getHttp1BootstrapWithProxy(ProxyServer proxy) {
      return http1Bootstrap.clone().handler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) {
          ch.pipeline()
            .addLast(PINNED_HANDLER, NoopHandler.INSTANCE)
            .addLast(PROXY_HANDLER, proxy.newHandler());
          addHttpHandlers(ch);
        }
      });
    }

    private Bootstrap getWsBootstrapWithProxy(ProxyServer proxy) {
      return wsBootstrap.clone().handler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) {
          ch.pipeline()
            .addLast(PINNED_HANDLER, NoopHandler.INSTANCE)
            .addLast(PROXY_HANDLER, proxy.newHandler());
          addWsHandlers(ch);
        }
      });
    }
  }

  private final AtomicBoolean closed = new AtomicBoolean();
  private final HttpClientConfig config;
  private final EventExecutor channelGroupEventExecutor;
  private final ChannelGroup channelGroup;
  private final FastThreadLocal<EventLoopResources> eventLoopResources = new FastThreadLocal<>();

  public DefaultHttpClient(HttpClientConfig config) {
    this.config = config;
    channelGroupEventExecutor = new DefaultEventExecutor();
    channelGroup = new DefaultChannelGroup(channelGroupEventExecutor);
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      channelGroup.close().awaitUninterruptibly();
      channelGroupEventExecutor.shutdownGracefully(0, 1, TimeUnit.SECONDS);
      ReferenceCountUtil.release(config.getDefaultSslContext());
      ReferenceCountUtil.release(config.getDefaultAlpnSslContext());
    }
  }

  @Override
  public void sendRequest(Request request, long clientId, EventLoop eventLoop, HttpListener listener, SslContext sslContext, SslContext alpnSslContext) {
    if (isClosed()) {
      return;
    }

    if (sslContext == null) {
      sslContext = config.getDefaultSslContext();
      alpnSslContext = config.getDefaultAlpnSslContext();
    }

    HttpTx tx = buildTx(request, clientId, listener, sslContext, alpnSslContext);

    if (eventLoop.inEventLoop()) {
      sendTx(tx, eventLoop);
    } else if (!eventLoop.isShutdown()) {
      eventLoop.execute(() -> sendTx(tx, eventLoop));
    }
  }

  @Override
  public void sendHttp2Requests(Pair<Request, HttpListener>[] requestsAndListeners, long clientId, EventLoop eventLoop, SslContext sslContext, SslContext alpnSslContext) {
    if (isClosed()) {
      return;
    }
    for (Pair<Request, HttpListener> pair : requestsAndListeners) {
      pair.getRight().onSend();
    }

    Request headRequest = requestsAndListeners[0].getLeft();

    if (headRequest.getUri().isSecured() && headRequest.isHttp2Enabled() && !config.isEnableSni()) {
      for (Pair<Request, HttpListener> requestAndListener : requestsAndListeners) {
        HttpListener listener = requestAndListener.getRight();
        listener.onThrowable(new UnsupportedOperationException("HTTP/2 can't work if SNI is disabled."));
      }
      return;
    }

    if (sslContext == null) {
      sslContext = config.getDefaultSslContext();
      alpnSslContext = config.getDefaultAlpnSslContext();
    }

    List<HttpTx> txs = new ArrayList<>();
    for (Pair<Request, HttpListener> requestAndListener : requestsAndListeners) {
      Request request = requestAndListener.getLeft();
      HttpListener listener = requestAndListener.getRight();
      txs.add(buildTx(request, clientId, listener, sslContext, alpnSslContext));
    }

    if (eventLoop.inEventLoop()) {
      sendHttp2Txs(txs, eventLoop);
    } else if (!eventLoop.isShutdown()) {
      eventLoop.execute(() -> sendHttp2Txs(txs, eventLoop));
    }
  }

  //////////////////// EVERYTHING BELOW ONLY HAPPENS IN SAME EVENTLOOP //////////////////

  private EventLoopResources eventLoopResources(EventLoop eventLoop) {
    EventLoopResources resources = eventLoopResources.get();
    if (resources == null) {
      resources = new EventLoopResources(eventLoop);
      eventLoopResources.set(resources);
    }
    return resources;
  }

  private HttpTx buildTx(Request request, long clientId, HttpListener listener, SslContext sslContext, SslContext alpnSslContext) {
    RequestTimeout requestTimeout = RequestTimeout.requestTimeout(request.getRequestTimeout(), listener);
    ChannelPoolKey key = new ChannelPoolKey(clientId, RemoteKey.newKey(request.getUri(), request.getVirtualHost(), request.getProxyServer()));
    return new HttpTx(request, listener, requestTimeout, key, sslContext, alpnSslContext);
  }

  // only retry pooled keep-alive connections = when keep-alive timeout triggered server side while we were writing and request can be replayed
  boolean canRetry(HttpTx tx) {
    return tx.channelState == HttpTx.ChannelState.POOLED
      && !(tx.request.getBody() instanceof InputStreamRequestBody && ((InputStreamRequestBody) tx.request.getBody()).isConsumed()); // InputStreamRequestBody can't be replayed
  }

  void retry(HttpTx tx, EventLoop eventLoop) {
    if (isClosed()) {
      return;
    }

    tx.channelState = HttpTx.ChannelState.RETRY;
    LOGGER.debug("Retrying with new HTTP/1.1 connection");
    sendTx(tx, eventLoop);
  }

  void retryHttp2(List<HttpTx> txs, EventLoop eventLoop) {
    if (isClosed()) {
      return;
    }

    for (HttpTx tx : txs) {
      tx.channelState = HttpTx.ChannelState.RETRY;
    }
    LOGGER.debug("Retrying with new HTTP/2 connection");
    sendHttp2Txs(txs, eventLoop);
  }

  private void sendTx(HttpTx tx, EventLoop eventLoop) {

    EventLoopResources resources = eventLoopResources(eventLoop);
    Request request = tx.request;
    HttpListener listener = tx.listener;
    RequestTimeout requestTimeout = tx.requestTimeout;
    Uri requestUri = request.getUri();
    boolean tryHttp2 = request.isHttp2Enabled() && requestUri.isSecured() && !requestUri.isWebSocket();

    // use a fresh channel for WebSocket
    Channel pooledChannel = requestUri.isWebSocket() ? null : resources.channelPool.poll(tx.key);

    listener.onSend();
    if (tryHttp2 && !config.isEnableSni()) {
      listener.onThrowable(new UnsupportedOperationException("HTTP/2 can't work if SNI is disabled."));
      return;
    }

    // start timeout
    tx.requestTimeout.start(eventLoop);

    if (pooledChannel != null && tx.channelState != HttpTx.ChannelState.RETRY) {
      sendTxWithChannel(tx, pooledChannel);

    } else {
      InetSocketAddress unresolvedRemoteAddressThroughTunnelling = unresolvedRemoteAddressThroughTunnelling(request.getProxyServer(), requestUri);
      boolean logProxyAddress = unresolvedRemoteAddressThroughTunnelling != null;

      resolveRemoteAddresses(request, eventLoop, unresolvedRemoteAddressThroughTunnelling, listener, requestTimeout)
        .addListener((Future<List<InetSocketAddress>> whenRemoteAddresses) -> {
          if (requestTimeout.isDone()) {
            return;
          }

          if (whenRemoteAddresses.isSuccess()) {
            List<InetSocketAddress> addresses = whenRemoteAddresses.getNow();

            if (tryHttp2 && tx.channelState != HttpTx.ChannelState.RETRY) {
              String domain = requestUri.getHost();
              Channel coalescedChannel = resources.channelPool.pollCoalescedChannel(tx.key.clientId, domain, addresses);
              if (coalescedChannel != null) {
                tx.listener.onProtocolAwareness(true);
                sendTxWithChannel(tx, coalescedChannel);
              } else {
                sendTxWithNewChannel(tx, resources, eventLoop, addresses, logProxyAddress);
              }
            } else {
              sendTxWithNewChannel(tx, resources, eventLoop, addresses, logProxyAddress);
            }
          }
        });
    }
  }

  private void sendHttp2Txs(List<HttpTx> txs, EventLoop eventLoop) {

    HttpTx tx = txs.get(0);
    EventLoopResources resources = eventLoopResources(eventLoop);
    Request request = tx.request;
    HttpListener listener = tx.listener;
    RequestTimeout requestTimeout = tx.requestTimeout;
    Uri requestUri = request.getUri();

    // start timeouts
    for (HttpTx t : txs) {
      t.requestTimeout.start(eventLoop);
    }

    ProxyServer proxyServer = request.getProxyServer();
    InetSocketAddress unresolvedRemoteAddressThroughTunnelling = unresolvedRemoteAddressThroughTunnelling(proxyServer, requestUri);
    boolean logProxyAddress = unresolvedRemoteAddressThroughTunnelling != null;

    resolveRemoteAddresses(request, eventLoop, unresolvedRemoteAddressThroughTunnelling, listener, requestTimeout)
      .addListener((Future<List<InetSocketAddress>> whenRemoteAddresses) -> {
        if (requestTimeout.isDone()) {
          return;
        }

        if (whenRemoteAddresses.isSuccess()) {
          List<InetSocketAddress> addresses = whenRemoteAddresses.getNow();

          String domain = requestUri.getHost();
          Channel coalescedChannel = resources.channelPool.pollCoalescedChannel(tx.key.clientId, domain, addresses);
          if (coalescedChannel != null) {
            sendHttp2TxsWithChannel(txs, coalescedChannel);
          } else {
            sendHttp2TxsWithNewChannel(txs, resources, eventLoop, addresses, logProxyAddress);
          }
        }
      });
  }

  private void sendTxWithChannel(HttpTx tx, Channel channel) {

    if (isClosed()) {
      return;
    }

    if (ChannelPool.isHttp2(channel)) {
      tx.listener.onProtocolAwareness(true);
    }

    tx.requestTimeout.setChannel(channel);

    Realm realm = tx.request.getRealm();
    if (realm instanceof DigestRealm) {
      // FIXME is it the right place?
      // FIXME wouldn't work for WebSocket
      // FIXME wouldn't work with HTTP/2
      channel.pipeline().addBefore(APP_HTTP_HANDLER, DIGEST_AUTH_HANDLER, new DigestAuthHandler(tx, (DigestRealm) realm, config));
    }

    channel.write(tx);
  }

  private void sendHttp2TxsWithChannel(List<HttpTx> txs, Channel channel) {

    if (isClosed()) {
      return;
    }

    for (HttpTx tx : txs) {
      tx.requestTimeout.setChannel(channel);
      tx.listener.onProtocolAwareness(true);
      channel.write(tx);
    }
  }

  private InetSocketAddress unresolvedRemoteAddressThroughTunnelling(ProxyServer proxyServer, Uri requestUri) {
    return proxyServer != null && (proxyServer instanceof SockProxyServer || requestUri.isSecured() || requestUri.isWebSocket()) ?
      InetSocketAddress.createUnresolved(requestUri.getHost(), requestUri.getExplicitPort()) :
      null;
  }

  private Future<List<InetSocketAddress>> resolveRemoteAddresses(Request request, EventLoop eventLoop, InetSocketAddress unresolvedRemoteAddressThroughTunnelling, HttpListener listener, RequestTimeout requestTimeout) {
    ProxyServer proxyServer = request.getProxyServer();
    if (proxyServer != null) {
      InetSocketAddress remoteAddress = unresolvedRemoteAddressThroughTunnelling != null ?
        // ProxyHandler will take care of the connect logic
        unresolvedRemoteAddressThroughTunnelling :
        // directly connect to proxy over clear HTTP
        proxyServer.getAddress();

      return ImmediateEventExecutor.INSTANCE.newSucceededFuture(singletonList(remoteAddress));

    } else {
      Promise<List<InetSocketAddress>> p = eventLoop.newPromise();

      request.getNameResolver().resolveAll(request.getUri().getHost(), eventLoop.newPromise(), listener)
        .addListener((Future<List<InetAddress>> whenAddresses) -> {
          if (whenAddresses.isSuccess()) {
            List<InetSocketAddress> remoteInetSocketAddresses = whenAddresses.getNow().stream()
              .map(address -> new InetSocketAddress(address, request.getUri().getExplicitPort()))
              .collect(Collectors.toList());

            p.setSuccess(remoteInetSocketAddresses);
          } else {
            if (!requestTimeout.isDone()) {
              // only report if we haven't timed out
              listener.onThrowable(whenAddresses.cause());
            }
            p.setFailure(whenAddresses.cause());
            requestTimeout.cancel();
          }
        });
      return p;
    }
  }

  private void sendTxWithNewChannel(HttpTx tx,
                                    EventLoopResources resources,
                                    EventLoop eventLoop,
                                    List<InetSocketAddress> addresses,
                                    boolean logProxyAddress) {
    tx.channelState = HttpTx.ChannelState.NEW;
    openNewChannel(tx.request, logProxyAddress, eventLoop, resources, addresses, tx.listener, tx.requestTimeout)
      .addListener((Future<Channel> whenNewChannel) -> {
        if (whenNewChannel.isSuccess()) {
          Channel channel = whenNewChannel.getNow();
          if (tx.requestTimeout.isDone()) {
            channel.close();
            return;
          }

          channelGroup.add(channel);
          resources.channelPool.register(channel, tx.key);

          if (tx.request.getUri().isSecured()) {
            LOGGER.debug("Installing SslHandler for {}", tx.request.getUri());
            installSslHandler(tx, channel).addListener(f -> {
              if (tx.requestTimeout.isDone() || !f.isSuccess()) {
                channel.close();
                return;
              }

              if (tx.request.isAlpnRequired()) {
                LOGGER.debug("Installing Http2Handler for {}", tx.request.getUri());
                installHttp2Handler(tx, channel, resources.channelPool).addListener(f2 -> {
                  if (tx.requestTimeout.isDone() || !f2.isSuccess()) {
                    channel.close();
                    return;
                  }
                  sendTxWithChannel(tx, channel);
                });

              } else {
                sendTxWithChannel(tx, channel);
              }
            });
          } else {
            sendTxWithChannel(tx, channel);
          }
        }
      });
  }

  private void sendHttp2TxsWithNewChannel(List<HttpTx> txs,
                                          EventLoopResources resources,
                                          EventLoop eventLoop,
                                          List<InetSocketAddress> addresses,
                                          boolean logProxyAddress) {
    HttpTx tx = txs.get(0);
    openNewChannel(tx.request, logProxyAddress, eventLoop, resources, addresses, tx.listener, tx.requestTimeout)
      .addListener((Future<Channel> whenNewChannel) -> {
        if (whenNewChannel.isSuccess()) {
          Channel channel = whenNewChannel.getNow();
          if (tx.requestTimeout.isDone()) {
            channel.close();
            return;
          }

          channelGroup.add(channel);
          resources.channelPool.register(channel, tx.key);

          LOGGER.debug("Installing SslHandler for {}", tx.request.getUri());
          installSslHandler(tx, channel).addListener(f -> {
            if (tx.requestTimeout.isDone() || !f.isSuccess()) {
              channel.close();
              return;
            }
            LOGGER.debug("Installing Http2Handler for {}", tx.request.getUri());
            installHttp2Handler(tx, channel, resources.channelPool).addListener(f2 -> {
              if (tx.requestTimeout.isDone() || !f2.isSuccess()) {
                channel.close();
                return;
              }
              sendHttp2TxsWithChannel(txs, channel);
            });
          });
        }
      });
  }

  private Bootstrap bootstrap(Request request, EventLoopResources resources) {
    Uri uri = request.getUri();
    ProxyServer proxyServer = request.getProxyServer();

    if (proxyServer != null) {
      if (uri.isWebSocket()) {
        return resources.getWsBootstrapWithProxy(proxyServer);
      } else if (proxyServer instanceof SockProxyServer || uri.isSecured()) {
        // FIXME HTTP/2 with proxy
        return resources.getHttp1BootstrapWithProxy(proxyServer);
      }
    }

    if (uri.isWebSocket()) {
      return resources.wsBootstrap;
    } else if (request.isAlpnRequired() && request.getUri().isSecured()) {
      return resources.http2Bootstrap;
    } else {
      return resources.http1Bootstrap;
    }
  }

  private static InetSocketAddress localAddressWithRandomPort(InetAddress localAddress) {
    return localAddress != null ? new InetSocketAddress(localAddress, 0) : null;
  }

  private Future<Channel> openNewChannel(Request request,
                                         boolean logProxyAddress,
                                         EventLoop eventLoop,
                                         EventLoopResources resources,
                                         List<InetSocketAddress> remoteAddresses,
                                         HttpListener listener,
                                         RequestTimeout requestTimeout) {

    Bootstrap bootstrap = bootstrap(request, resources);
    Promise<Channel> channelPromise = eventLoop.newPromise();
    InetSocketAddress loggedProxyAddress = logProxyAddress ? request.getProxyServer().getAddress() : null;
    openNewChannelRec(remoteAddresses, loggedProxyAddress, localAddressWithRandomPort(request.getLocalIpV4Address()), localAddressWithRandomPort(request.getLocalIpV6Address()), 0, channelPromise, bootstrap, listener, requestTimeout);
    return channelPromise;
  }

  private static final Exception IGNORE_REQUEST_TIMEOUT_REACHED_WHILE_TRYING_TO_CONNECT = new TimeoutException("Request timeout reached while trying to connect, should be ignored") {
    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  };

  private void openNewChannelRec(List<InetSocketAddress> remoteAddresses,
                                 InetSocketAddress loggedProxyAddress,
                                 InetSocketAddress localIpV4Address,
                                 InetSocketAddress localIpV6Address,
                                 int i,
                                 Promise<Channel> channelPromise,
                                 Bootstrap bootstrap,
                                 HttpListener listener,
                                 RequestTimeout requestTimeout) {

    if (isClosed()) {
      return;
    }

    InetSocketAddress remoteAddress = remoteAddresses.get(i);
    InetSocketAddress localAddress;
    boolean forceMoveToNextRemoteAddress = false;

    if (localIpV4Address == null && localIpV6Address == null) {
      // non explicit local addresses, skip
      localAddress = null;
    } else if (remoteAddress.getAddress() instanceof Inet6Address) {
      if (localIpV6Address == null) {
        // forcing local IPv4 while remote is IPv6 is bound to fail => move to next address
        localAddress = null;
        forceMoveToNextRemoteAddress = true;
      } else {
        localAddress = localIpV6Address;
      }
    } else {
      // IPv4
      localAddress = NetUtil.isIpV6AddressesPreferred() && localIpV6Address != null ? localIpV6Address : localIpV4Address;
    }

    if (forceMoveToNextRemoteAddress) {
      int nextI = i + 1;
      if (nextI < remoteAddresses.size()) {
        openNewChannelRec(remoteAddresses, loggedProxyAddress, localIpV4Address, null, nextI, channelPromise, bootstrap, listener, requestTimeout);

      } else {
        requestTimeout.cancel();
        Exception cause = new UnsupportedOperationException("Can't connect to IPv6 remote " + remoteAddress + " + from IPv4 local one " + localIpV4Address);
        listener.onThrowable(cause);
        channelPromise.setFailure(cause);
      }
    } else {
      //[fl]
      //
      //[fl]
      ChannelFuture whenChannel = bootstrap.connect(remoteAddress, localAddress);

      whenChannel.addListener(f -> {
        if (f.isSuccess()) {
          //[fl]
          //
          //[fl]
          channelPromise.setSuccess(whenChannel.channel());

        } else {
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Failed to connect to remoteAddress=" + remoteAddress + " from localAddress=" + localAddress, f.cause());
          }
          //[fl]
          //
          //[fl]

          if (requestTimeout.isDone()) {
            channelPromise.setFailure(IGNORE_REQUEST_TIMEOUT_REACHED_WHILE_TRYING_TO_CONNECT);
            return;
          }

          int nextI = i + 1;
          if (nextI < remoteAddresses.size()) {
            openNewChannelRec(remoteAddresses, loggedProxyAddress, localIpV4Address, localIpV6Address, nextI, channelPromise, bootstrap, listener, requestTimeout);

          } else {
            requestTimeout.cancel();
            listener.onThrowable(f.cause());
            channelPromise.setFailure(f.cause());
          }
        }
      });
    }
  }

  private Future<Channel> installSslHandler(HttpTx tx, Channel channel) {

    try {
      SslHandler sslHandler = SslHandlers.newSslHandler(tx.sslContext(), channel.alloc(), tx.request.getUri(), tx.request.getVirtualHost(), config);
      //[fl]
      //
      //[fl]

      ChannelPipeline pipeline = channel.pipeline();
      String after = pipeline.get(PROXY_HANDLER) != null ? PROXY_HANDLER : PINNED_HANDLER;
      pipeline.addAfter(after, SSL_HANDLER, sslHandler);

      return sslHandler.handshakeFuture().addListener(f -> {
        if (tx.requestTimeout.isDone()) {
          return;
        }

        if (f.isSuccess()) {
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("TLS handshake successful: protocol={} cipher suite={}", sslHandler.engine().getSession().getProtocol(), sslHandler.engine().getSession().getCipherSuite());
          }

          //[fl]
          //
          //[fl]
        } else {
          tx.requestTimeout.cancel();
          //[fl]
          //
          //[fl]
          tx.listener.onThrowable(f.cause());
        }
      });
    } catch (RuntimeException e) {
      tx.requestTimeout.cancel();
      //[fl]
      //
      //[fl]
      tx.listener.onThrowable(e);
      return new DefaultPromise<Channel>(ImmediateEventExecutor.INSTANCE).setFailure(e);
    }
  }

  private Future<Channel> installHttp2Handler(HttpTx tx, Channel channel, ChannelPool channelPool) {

    Promise<Channel> whenAlpn = channel.eventLoop().newPromise();

    channel.pipeline().addAfter(SSL_HANDLER, ALPN_HANDLER, new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
      @Override
      protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {

        switch (protocol) {
          case ApplicationProtocolNames.HTTP_2:
            LOGGER.debug("ALPN led to HTTP/2 with remote {}", tx.request.getUri().getHost());
            tx.listener.onProtocolAwareness(true);
            Http2Connection connection = new DefaultHttp2Connection(false);

            HttpToHttp2ConnectionHandler http2Handler = new HttpToHttp2ConnectionHandlerBuilder()
              .initialSettings(Http2Settings.defaultSettings()) // FIXME override?
              .connection(connection)
              .frameListener(
                new DelegatingDecompressorFrameListener(
                  connection,
                  new ChunkedInboundHttp2ToHttpAdapter(connection, false, true, whenAlpn) {
                    @Override
                    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) {
                      ctx.fireChannelRead(new Http2AppHandler.GoAwayFrame(lastStreamId, errorCode));
                    }
                  }) {

                  @Override
                  protected EmbeddedChannel newContentDecompressor(final ChannelHandlerContext ctx, CharSequence contentEncoding)
                    throws Http2Exception {
                    if (Brotli4jLoader.isAvailable() && BR.contentEqualsIgnoreCase(contentEncoding)) {
                      return new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(),
                        ctx.channel().config(), new BrotliDecoder());
                    } else {
                      return super.newContentDecompressor(ctx, contentEncoding);
                    }
                  }
                }
              ).build();

            ctx.pipeline()
              .addLast(HTTP2_HANDLER, http2Handler)
              .addLast(APP_HTTP2_HANDLER, new Http2AppHandler(DefaultHttpClient.this, connection, http2Handler, channelPool, config));

            channelPool.offer(channel);

            SslHandler sslHandler = (SslHandler) ctx.pipeline().get(SSL_HANDLER);
            Set<String> subjectAlternativeNames = Tls.extractSubjectAlternativeNames(sslHandler.engine());
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug("TLS handshake successful: protocol={} cipher suite={}", sslHandler.engine().getSession().getProtocol(), sslHandler.engine().getSession().getCipherSuite());
            }
            if (!subjectAlternativeNames.isEmpty()) {
              channelPool.offerCoalescedChannel(subjectAlternativeNames, (InetSocketAddress) channel.remoteAddress(), channel, tx.key);
            }
            break;

          case ApplicationProtocolNames.HTTP_1_1:
            LOGGER.debug("ALPN led to HTTP/1 with remote {}", tx.request.getUri().getHost());
            if (tx.request.isHttp2PriorKnowledge()) {
              IllegalStateException e = new IllegalStateException("HTTP/2 Prior knowledge was set on host " + tx.request.getUri().getHost() + " but it only supports HTTP/1");
              whenAlpn.setFailure(e);
              throw e;
            }
            tx.listener.onProtocolAwareness(false);
            ctx.pipeline()
              .addBefore(CHUNKED_WRITER_HANDLER, HTTP_CLIENT_CODEC, newHttpClientCodec())
              .addBefore(CHUNKED_WRITER_HANDLER, INFLATER_HANDLER, newHttpContentDecompressor())
              .addAfter(CHUNKED_WRITER_HANDLER, APP_HTTP_HANDLER, new HttpAppHandler(DefaultHttpClient.this, channelPool, config));
            whenAlpn.setSuccess(ctx.channel());
            break;

          default:
            IllegalStateException e = new IllegalStateException("Unknown protocol: " + protocol);
            whenAlpn.setFailure(e);
            ctx.close();
            // FIXME do we really need to throw?
            throw e;
        }
      }
    });

    whenAlpn.addListener(f -> {
      if (!f.isSuccess()) {
        tx.listener.onThrowable(f.cause());
      }
    });

    return whenAlpn;
  }

  @Override
  public boolean isClosed() {
    return closed.get();
  }

  @Override
  public void flushClientIdChannels(long clientId, EventLoop eventLoop) {
    if (eventLoop.inEventLoop()) {
      eventLoopResources(eventLoop).channelPool.flushClientIdChannelPoolPartitions(clientId);
    } else if (!eventLoop.isShutdown()) {
      eventLoop.execute(() -> eventLoopResources(eventLoop).channelPool.flushClientIdChannelPoolPartitions(clientId));
    }
  }
}
