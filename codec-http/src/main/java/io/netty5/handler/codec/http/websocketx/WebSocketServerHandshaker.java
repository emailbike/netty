/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.http.websocketx;

import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOutboundInvoker;
import io.netty5.channel.ChannelPipeline;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpContentCompressor;
import io.netty5.handler.codec.http.HttpObject;
import io.netty5.handler.codec.http.HttpObjectAggregator;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpRequestDecoder;
import io.netty5.handler.codec.http.HttpResponseEncoder;
import io.netty5.handler.codec.http.HttpServerCodec;
import io.netty5.handler.codec.http.LastHttpContent;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.EmptyArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Base class for server side web socket opening and closing handshakes
 */
public abstract class WebSocketServerHandshaker {
    protected static final Logger logger = LoggerFactory.getLogger(WebSocketServerHandshaker.class);

    private final String uri;

    private final String[] subprotocols;

    private final WebSocketVersion version;

    private final WebSocketDecoderConfig decoderConfig;

    private String selectedSubprotocol;

    /**
     * Use this as wildcard to support all requested sub-protocols
     */
    public static final String SUB_PROTOCOL_WILDCARD = "*";

    /**
     * Constructor specifying the destination web socket location
     *
     * @param version               the protocol version
     * @param uri                   URL for web socket communications. e.g "ws://myhost.com/mypath".
     *                              Subsequent web socket frames will be sent to this URL.
     * @param subprotocols          CSV of supported protocols. Null if sub protocols not supported.
     * @param maxFramePayloadLength Maximum length of a frame's payload
     */
    protected WebSocketServerHandshaker(
            WebSocketVersion version, String uri, String subprotocols,
            int maxFramePayloadLength) {
        this(version, uri, subprotocols, WebSocketDecoderConfig.newBuilder()
                                                               .maxFramePayloadLength(maxFramePayloadLength)
                                                               .build());
    }

    /**
     * Constructor specifying the destination web socket location
     *
     * @param version       the protocol version
     * @param uri           URL for web socket communications. e.g "ws://myhost.com/mypath".
     *                      Subsequent web socket frames will be sent to this URL.
     * @param subprotocols  CSV of supported protocols. Null if sub protocols not supported.
     * @param decoderConfig Frames decoder configuration.
     */
    protected WebSocketServerHandshaker(
            WebSocketVersion version, String uri, String subprotocols, WebSocketDecoderConfig decoderConfig) {
        this.version = version;
        this.uri = uri;
        if (subprotocols != null) {
            String[] subprotocolArray = subprotocols.split(",");
            for (int i = 0; i < subprotocolArray.length; i++) {
                subprotocolArray[i] = subprotocolArray[i].trim();
            }
            this.subprotocols = subprotocolArray;
        } else {
            this.subprotocols = EmptyArrays.EMPTY_STRINGS;
        }
        this.decoderConfig = requireNonNull(decoderConfig, "decoderConfig");
    }

    /**
     * Returns the URL of the web socket
     */
    public String uri() {
        return uri;
    }

    /**
     * Returns the CSV of supported sub protocols
     */
    public Set<String> subprotocols() {
        Set<String> ret = new LinkedHashSet<>();
        Collections.addAll(ret, subprotocols);
        return ret;
    }

    /**
     * Returns the version of the specification being supported
     */
    public WebSocketVersion version() {
        return version;
    }

    /**
     * Gets the maximum length for any frame's payload.
     *
     * @return The maximum length for a frame's payload
     */
    public int maxFramePayloadLength() {
        return decoderConfig.maxFramePayloadLength();
    }

    /**
     * Gets this decoder configuration.
     *
     * @return This decoder configuration.
     */
    public WebSocketDecoderConfig decoderConfig() {
        return decoderConfig;
    }

    /**
     * Performs the opening handshake. Note that the ownership of the {@link FullHttpRequest} which is passed in
     * belongs to the caller (ownership is not transferred to this method, and caller is responsible
     * to close the request parameter).
     *
     * @param channel Channel
     * @param req     HTTP Request
     * @return future
     * The {@link Future} which is notified once the opening handshake completes
     */
    public Future<Void> handshake(Channel channel, FullHttpRequest req) {
        return handshake(channel, req, null);
    }

    /**
     * Performs the opening handshake
     * <p>
     * Note that the ownership of the {@link FullHttpRequest} which is passed in
     * belongs to the caller (ownership is not transferred to this method, and caller is responsible
     * to close the request parameter).
     *
     * @param channel         Channel
     * @param req             HTTP Request
     * @param responseHeaders Extra headers to add to the handshake response or {@code null}
     *                        if no extra headers should be added
     * @return future
     * the {@link Future} which is notified when the opening handshake is done
     */
    public final Future<Void> handshake(Channel channel, FullHttpRequest req, HttpHeaders responseHeaders) {

        if (logger.isDebugEnabled()) {
            logger.debug("{} WebSocket version {} server handshake", channel, version());
        }
        FullHttpResponse response = newHandshakeResponse(channel.bufferAllocator(), req, responseHeaders);
        ChannelPipeline p = channel.pipeline();
        if (p.get(HttpObjectAggregator.class) != null) {
            p.remove(HttpObjectAggregator.class);
        }
        if (p.get(HttpContentCompressor.class) != null) {
            p.remove(HttpContentCompressor.class);
        }
        ChannelHandlerContext ctx = p.context(HttpRequestDecoder.class);
        final String encoderName;
        if (ctx == null) {
            // this means the user use an HttpServerCodec
            ctx = p.context(HttpServerCodec.class);
            if (ctx == null) {
                response.close();
                return channel.newFailedFuture(
                        new IllegalStateException("No HttpDecoder and no HttpServerCodec in the pipeline"));
            }
            p.addBefore(ctx.name(), "wsencoder", newWebSocketEncoder());
            p.addBefore(ctx.name(), "wsdecoder", newWebsocketDecoder());
            encoderName = ctx.name();
        } else {
            p.replace(ctx.name(), "wsdecoder", newWebsocketDecoder());

            encoderName = p.context(HttpResponseEncoder.class).name();
            p.addBefore(encoderName, "wsencoder", newWebSocketEncoder());
        }
        return channel.writeAndFlush(response).addListener(channel, (ch, future) -> {
            if (future.isSuccess()) {
                ChannelPipeline p1 = ch.pipeline();
                p1.remove(encoderName);
            }
        });
    }

    /**
     * Performs the opening handshake. Note that the ownership of the {@link FullHttpRequest} which is passed in
     * belongs to the caller (ownership is not transferred to this method, and caller is responsible
     * to close the request parameter).
     *
     * @param channel Channel
     * @param req     HTTP Request
     * @return future
     * The {@link Future} which is notified once the opening handshake completes
     */
    public Future<Void> handshake(Channel channel, HttpRequest req) {
        return handshake(channel, req, null);
    }

    /**
     * Performs the opening handshake
     * <p>
     * Note that the ownership of the {@link FullHttpRequest} which is passed in
     * belongs to the caller (ownership is not transferred to this method, and caller is responsible
     * to close the request parameter).
     *
     * @param channel         Channel
     * @param req             HTTP Request
     * @param responseHeaders Extra headers to add to the handshake response or {@code null}
     *                        if no extra headers should be added
     * @return future
     * the {@link Future} which is notified when the opening handshake is done
     */
    public final Future<Void> handshake(final Channel channel, HttpRequest req,
                                        final HttpHeaders responseHeaders) {
        if (req instanceof FullHttpRequest) {
            return handshake(channel, (FullHttpRequest) req, responseHeaders);
        }

        ChannelPipeline pipeline = channel.pipeline();
        ChannelHandlerContext ctx = pipeline.context(HttpRequestDecoder.class);
        if (ctx == null) {
            // This means the user use a HttpServerCodec
            ctx = pipeline.context(HttpServerCodec.class);
            if (ctx == null) {
                return channel.newFailedFuture(
                        new IllegalStateException("No HttpDecoder and no HttpServerCodec in the pipeline"));
            }
        }

        final Promise<Void> promise = channel.newPromise();
        pipeline.addAfter(ctx.name(), "handshaker", new ChannelHandlerAdapter() {

            private FullHttpRequest fullHttpRequest;

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof HttpObject) {
                    try {
                        handleHandshakeRequest(ctx, (HttpObject) msg);
                    } finally {
                        Resource.dispose(msg);
                    }
                } else {
                    super.channelRead(ctx, msg);
                }
            }

            @Override
            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                ctx.pipeline().remove(this);
                promise.tryFailure(cause);
                super.channelExceptionCaught(ctx, cause);
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                try {
                    // Fail promise if Channel was closed
                    if (!promise.isDone()) {
                        promise.tryFailure(new ClosedChannelException());
                    }
                    ctx.fireChannelInactive();
                } finally {
                    releaseFullHttpRequest();
                }
            }

            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                releaseFullHttpRequest();
            }

            private void handleHandshakeRequest(ChannelHandlerContext ctx, HttpObject httpObject) {
                if (httpObject instanceof FullHttpRequest) {
                    ctx.pipeline().remove(this);
                    handshake(channel, (FullHttpRequest) httpObject, responseHeaders).cascadeTo(promise);
                    return;
                }

                if (httpObject instanceof LastHttpContent) {
                    assert fullHttpRequest != null;
                    try (FullHttpRequest handshakeRequest = fullHttpRequest) {
                        fullHttpRequest = null;
                        ctx.pipeline().remove(this);
                        handshake(channel, handshakeRequest, responseHeaders).cascadeTo(promise);
                    }
                    return;
                }

                if (httpObject instanceof HttpRequest) {
                    HttpRequest httpRequest = (HttpRequest) httpObject;
                    fullHttpRequest = new DefaultFullHttpRequest(httpRequest.protocolVersion(), httpRequest.method(),
                                                                 httpRequest.uri(), ctx.bufferAllocator().allocate(0),
                                                                 httpRequest.headers(), HttpHeaders.emptyHeaders());
                    if (httpRequest.decoderResult().isFailure()) {
                        fullHttpRequest.setDecoderResult(httpRequest.decoderResult());
                    }
                }
            }

            private void releaseFullHttpRequest() {
                if (fullHttpRequest != null) {
                    fullHttpRequest.close();
                    fullHttpRequest = null;
                }
            }
        });

        try {
            ctx.fireChannelRead(ReferenceCountUtil.retain(req));
        } catch (Throwable cause) {
            promise.setFailure(cause);
        }

        return promise.asFuture();
    }

    /**
     * Returns a new {@link FullHttpResponse) which will be used for as response to the handshake request.
     */
    protected abstract FullHttpResponse newHandshakeResponse(BufferAllocator allocator, FullHttpRequest req,
                                                             HttpHeaders responseHeaders);

    /**
     * Performs the closing handshake.
     * <p>
     * When called from within a {@link ChannelHandler} you most likely want to use
     * {@link #close(ChannelHandlerContext, CloseWebSocketFrame)}.
     *
     * @param channel the {@link Channel} to use.
     * @param frame   Closing Frame that was received.
     */
    public Future<Void> close(Channel channel, CloseWebSocketFrame frame) {
        requireNonNull(channel, "channel");
        return close0(channel, frame);
    }

    /**
     * Performs the closing handshake.
     *
     * @param ctx   the {@link ChannelHandlerContext} to use.
     * @param frame Closing Frame that was received.
     */
    public Future<Void> close(ChannelHandlerContext ctx, CloseWebSocketFrame frame) {
        requireNonNull(ctx, "ctx");
        return close0(ctx, frame);
    }

    private static Future<Void> close0(ChannelOutboundInvoker invoker, CloseWebSocketFrame frame) {
        return invoker.writeAndFlush(frame).addListener(invoker, ChannelFutureListeners.CLOSE);
    }

    /**
     * Selects the first matching supported sub protocol
     *
     * @param requestedSubprotocols CSV of protocols to be supported. e.g. "chat, superchat"
     * @return First matching supported sub protocol. Null if not found.
     */
    protected String selectSubprotocol(String requestedSubprotocols) {
        if (requestedSubprotocols == null || subprotocols.length == 0) {
            return null;
        }

        String[] requestedSubprotocolArray = requestedSubprotocols.split(",");
        for (String p : requestedSubprotocolArray) {
            String requestedSubprotocol = p.trim();

            for (String supportedSubprotocol : subprotocols) {
                if (SUB_PROTOCOL_WILDCARD.equals(supportedSubprotocol)
                    || requestedSubprotocol.equals(supportedSubprotocol)) {
                    selectedSubprotocol = requestedSubprotocol;
                    return requestedSubprotocol;
                }
            }
        }

        // No match found
        return null;
    }

    /**
     * Returns the selected subprotocol. Null if no subprotocol has been selected.
     * <p>
     * This is only available AFTER <tt>handshake()</tt> has been called.
     * </p>
     */
    public String selectedSubprotocol() {
        return selectedSubprotocol;
    }

    /**
     * Returns the decoder to use after handshake is complete.
     */
    protected abstract WebSocketFrameDecoder newWebsocketDecoder();

    /**
     * Returns the encoder to use after the handshake is complete.
     */
    protected abstract WebSocketFrameEncoder newWebSocketEncoder();

}
