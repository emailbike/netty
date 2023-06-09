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
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.headers.HttpHeaders;

import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * <p>
 * Performs server side opening and closing handshakes for <a href="https://netty.io/s/rfc6455">RFC 6455</a>
 * (originally web socket specification <a href="https://netty.io/s/ws-17">draft-ietf-hybi-thewebsocketprotocol-17</a>).
 * </p>
 */
public class WebSocketServerHandshaker13 extends WebSocketServerHandshaker {

    /**
     * Constructor specifying the destination web socket location
     *
     * @param webSocketURL
     *        URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web
     *        socket frames will be sent to this URL.
     * @param subprotocols
     *        CSV of supported protocols
     * @param allowExtensions
     *        Allow extensions to be used in the reserved bits of the web socket frame
     * @param maxFramePayloadLength
     *        Maximum allowable frame payload length. Setting this value to your application's
     *        requirement may reduce denial of service attacks using long data frames.
     */
    public WebSocketServerHandshaker13(
            String webSocketURL, String subprotocols, boolean allowExtensions, int maxFramePayloadLength) {
        this(webSocketURL, subprotocols, allowExtensions, maxFramePayloadLength, false);
    }

    /**
     * Constructor specifying the destination web socket location
     *
     * @param webSocketURL
     *        URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web
     *        socket frames will be sent to this URL.
     * @param subprotocols
     *        CSV of supported protocols
     * @param allowExtensions
     *        Allow extensions to be used in the reserved bits of the web socket frame
     * @param maxFramePayloadLength
     *        Maximum allowable frame payload length. Setting this value to your application's
     *        requirement may reduce denial of service attacks using long data frames.
     * @param allowMaskMismatch
     *            When set to true, frames which are not masked properly according to the standard will still be
     *            accepted.
     */
    public WebSocketServerHandshaker13(
            String webSocketURL, String subprotocols, boolean allowExtensions, int maxFramePayloadLength,
            boolean allowMaskMismatch) {
        this(webSocketURL, subprotocols, WebSocketDecoderConfig.newBuilder()
            .allowExtensions(allowExtensions)
            .maxFramePayloadLength(maxFramePayloadLength)
            .allowMaskMismatch(allowMaskMismatch)
            .build());
    }

    /**
     * Constructor specifying the destination web socket location
     *
     * @param webSocketURL
     *        URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web
     *        socket frames will be sent to this URL.
     * @param subprotocols
     *        CSV of supported protocols
     * @param decoderConfig
     *            Frames decoder configuration.
     */
    public WebSocketServerHandshaker13(
            String webSocketURL, String subprotocols, WebSocketDecoderConfig decoderConfig) {
        super(WebSocketVersion.V13, webSocketURL, subprotocols, decoderConfig);
    }

    /**
     * <p>
     * Handle the web socket handshake for the web socket specification <a href=
     * "https://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-17">HyBi versions 13-17</a>. Versions 13-17
     * share the same wire protocol.
     * </p>
     *
     * <p>
     * Browser request to the server:
     * </p>
     *
     * <pre>
     * GET /chat HTTP/1.1
     * Host: server.example.com
     * Upgrade: websocket
     * Connection: Upgrade
     * Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
     * Origin: http://example.com
     * Sec-WebSocket-Protocol: chat, superchat
     * Sec-WebSocket-Version: 13
     * </pre>
     *
     * <p>
     * Server response:
     * </p>
     *
     * <pre>
     * HTTP/1.1 101 Switching Protocols
     * Upgrade: websocket
     * Connection: Upgrade
     * Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
     * Sec-WebSocket-Protocol: chat
     * </pre>
     */
    @Override
    protected FullHttpResponse newHandshakeResponse(BufferAllocator allocator, FullHttpRequest req,
                                                    HttpHeaders headers) {
        HttpMethod method = req.method();
        if (!HttpMethod.GET.equals(method)) {
            throw new WebSocketServerHandshakeException("Invalid WebSocket handshake method: " + method, req);
        }

        HttpHeaders reqHeaders = req.headers();
        if (!reqHeaders.contains(HttpHeaderNames.CONNECTION) ||
            !reqHeaders.containsIgnoreCase(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE)) {
            throw new WebSocketServerHandshakeException(
                    "not a WebSocket request: a |Connection| header must includes a token 'Upgrade'", req);
        }

        if (!reqHeaders.containsIgnoreCase(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET)) {
            throw new WebSocketServerHandshakeException(
                    "not a WebSocket request: a |Upgrade| header must containing the value 'websocket'", req);
        }

        CharSequence key = reqHeaders.get(HttpHeaderNames.SEC_WEBSOCKET_KEY);
        if (key == null) {
            throw new WebSocketServerHandshakeException("not a WebSocket request: missing key", req);
        }

        FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS,
                allocator.allocate(0));
        if (headers != null) {
            res.headers().add(headers);
        }

        String accept = WebSocketUtil.calculateV13Accept(key.toString());
        res.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET)
                     .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE)
                     .set(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT, accept);

        CharSequence subprotocols = reqHeaders.get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL);
        if (subprotocols != null) {
            String selectedSubprotocol = selectSubprotocol(subprotocols.toString());
            if (selectedSubprotocol == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Requested subprotocol(s) not supported: {}", subprotocols);
                }
            } else {
                res.headers().set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, selectedSubprotocol);
            }
        }
        return res;
    }

    @Override
    protected WebSocketFrameDecoder newWebsocketDecoder() {
        return new WebSocket13FrameDecoder(decoderConfig());
    }

    @Override
    protected WebSocketFrameEncoder newWebSocketEncoder() {
        return new WebSocket13FrameEncoder(false);
    }
}
