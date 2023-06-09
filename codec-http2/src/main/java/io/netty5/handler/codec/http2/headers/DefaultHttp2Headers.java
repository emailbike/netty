/*
 * Copyright 2022 The Netty Project
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
package io.netty5.handler.codec.http2.headers;

import io.netty5.handler.codec.http.headers.DefaultHttpHeaders;
import io.netty5.handler.codec.http.headers.HeaderValidationException;
import io.netty5.handler.codec.http.headers.HttpCookiePair;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.handler.codec.http.headers.HttpSetCookie;
import io.netty5.handler.codec.http.headers.MultiMap;
import io.netty5.util.AsciiString;
import io.netty5.util.ByteProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import static io.netty5.handler.codec.http2.headers.Http2Headers.PseudoHeaderName.hasPseudoHeaderFormat;
import static io.netty5.util.AsciiString.isUpperCase;
import static java.util.Collections.emptyIterator;

/**
 * Default implementation of {@link Http2Headers}.
 *
 * @apiNote It is an implementation detail that this class extends {@link MultiMap}. The multi-map itself is not part
 * of the public API for this class. Only the methods declared in {@link HttpHeaders} are considered public API.
 */
public class DefaultHttp2Headers extends DefaultHttpHeaders implements Http2Headers {
    private static final ByteProcessor HTTP2_NAME_VALIDATOR_PROCESSOR = value -> !isUpperCase(value);

    @Nullable
    private Http2MultiMapEntry firstPseudoHeader;
    @Nullable
    private Http2MultiMapEntry lastPseudoHeader;
    @Nullable
    private Http2MultiMapEntry firstNormalHeader;
    @Nullable
    private Http2MultiMapEntry lastNormalHeader;

    /**
     * Create a new instance.
     *
     * @param arraySizeHint   A hint as to how large the hash data structure should be. The next positive power of two
     *                        will be used. An upper bound may be enforced.
     * @param validateNames   {@code true} to validate header names.
     * @param validateCookies {@code true} to validate cookie contents when parsing.
     * @param validateValues  {@code true} to validate header values.
     */
    public DefaultHttp2Headers(int arraySizeHint, boolean validateNames, boolean validateCookies,
                                  boolean validateValues) {
        super(arraySizeHint, validateNames, validateCookies, validateValues);
    }

    @Override
    protected CharSequence validateKey(@Nullable CharSequence name, boolean forAdd) {
        if (name == null || name.length() == 0) {
            throw new HeaderValidationException("empty headers are not allowed");
        }
        if (validateNames) {
            if (hasPseudoHeaderFormat(name)) {
                if (!PseudoHeaderName.isPseudoHeader(name)) {
                    throw new HeaderValidationException("'" + name + "' is not a standard pseudo-header.");
                }
                if (forAdd && contains(name)) {
                    throw new HeaderValidationException("Duplicate HTTP/2 pseudo-header '" + name + "' encountered.");
                }
            } else {
                validateHeaderName(name);
                if (name instanceof AsciiString) {
                    int index = ((AsciiString) name).forEachByte(HTTP2_NAME_VALIDATOR_PROCESSOR);
                    if (index != -1) {
                        throw new HeaderValidationException("'" + name + "' is an invalid header name.");
                    }
                } else {
                    for (int i = 0; i < name.length(); ++i) {
                        if (isUpperCase(name.charAt(i))) {
                            throw new HeaderValidationException("'" + name + "' is an invalid header name.");
                        }
                    }
                }
            }
        }
        return name;
    }

    @Override
    protected CharSequence validateValue(CharSequence key, CharSequence value) {
        // https://datatracker.ietf.org/doc/html/rfc9113#section-8.3.1
        // pseudo headers must not be empty
        if (validateValues && (value == null || value.length() == 0) && hasPseudoHeaderFormat(key)) {
            throw new HeaderValidationException("HTTP/2 pseudo-header '" + key + "' must not be empty.");
        }
        return super.validateValue(key, value);
    }

    @Override
    public Http2Headers copy() {
        DefaultHttp2Headers copy = new DefaultHttp2Headers(
                size(), validateNames, validateCookies, validateValues);
        copy.add(this);
        return copy;
    }

    @Override
    public Http2Headers add(CharSequence name, CharSequence value) {
        super.add(name, value);
        return this;
    }

    @Override
    public Http2Headers add(CharSequence name, Iterable<? extends CharSequence> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public Http2Headers add(CharSequence name, Iterator<? extends CharSequence> valuesItr) {
        super.add(name, valuesItr);
        return this;
    }

    @Override
    public Http2Headers add(CharSequence name, CharSequence... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public Http2Headers add(HttpHeaders headers) {
        super.add(headers);
        return this;
    }

    @Override
    public Http2Headers set(CharSequence name, CharSequence value) {
        super.set(name, value);
        return this;
    }

    @Override
    public Http2Headers set(CharSequence name, Iterable<? extends CharSequence> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public Http2Headers set(CharSequence name, Iterator<? extends CharSequence> valueItr) {
        super.set(name, valueItr);
        return this;
    }

    @Override
    public Http2Headers set(CharSequence name, CharSequence... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public Http2Headers set(final HttpHeaders headers) {
        super.set(headers);
        return this;
    }

    @Override
    public Http2Headers replace(final HttpHeaders headers) {
        super.replace(headers);
        return this;
    }

    @Override
    public Http2Headers clear() {
        super.clear();
        firstPseudoHeader = lastPseudoHeader = firstNormalHeader = lastNormalHeader = null;
        return this;
    }

    @Override
    public Http2Headers addCookie(HttpCookiePair cookie) {
        super.addCookie(cookie);
        return this;
    }

    @Override
    public Http2Headers addCookie(final CharSequence name, final CharSequence value) {
        super.addCookie(name, value);
        return this;
    }

    @Override
    public Http2Headers addSetCookie(HttpSetCookie cookie) {
        super.addSetCookie(cookie);
        return this;
    }

    @Override
    public Http2Headers addSetCookie(final CharSequence name, final CharSequence value) {
        super.addSetCookie(name, value);
        return this;
    }

    @Override
    public Http2Headers method(CharSequence value) {
        set(PseudoHeaderName.METHOD.value(), value);
        return this;
    }

    @Override
    public Http2Headers scheme(CharSequence value) {
        set(PseudoHeaderName.SCHEME.value(), value);
        return this;
    }

    @Override
    public Http2Headers authority(CharSequence value) {
        set(PseudoHeaderName.AUTHORITY.value(), value);
        return this;
    }

    @Override
    public Http2Headers path(CharSequence value) {
        set(PseudoHeaderName.PATH.value(), value);
        return this;
    }

    @Override
    public Http2Headers status(CharSequence value) {
        set(PseudoHeaderName.STATUS.value(), value);
        return this;
    }

    @Override
    public CharSequence method() {
        return get(PseudoHeaderName.METHOD.value());
    }

    @Override
    public CharSequence scheme() {
        return get(PseudoHeaderName.SCHEME.value());
    }

    @Override
    public CharSequence authority() {
        return get(PseudoHeaderName.AUTHORITY.value());
    }

    @Override
    public CharSequence path() {
        return get(PseudoHeaderName.PATH.value());
    }

    @Override
    public CharSequence status() {
        return get(PseudoHeaderName.STATUS.value());
    }

    @Override
    protected void removeEntry(MultiMap.@NotNull BucketHead<CharSequence, CharSequence> bucketHead,
                               @NotNull MultiMapEntry<CharSequence, CharSequence> entryToRemove, int bucketIndex) {
        super.removeEntry(bucketHead, entryToRemove, bucketIndex);
        Http2MultiMapEntry entry = (Http2MultiMapEntry) entryToRemove;
        if (entry == lastNormalHeader) {
            if (entry == firstNormalHeader) {
                firstNormalHeader = lastNormalHeader = null;
            } else {
                lastNormalHeader = entry.toPrev;
            }
        } else if (entry == firstNormalHeader) {
            firstNormalHeader = entry.toNext;
        } else if (entry == lastPseudoHeader) {
            if (entry == firstPseudoHeader) {
                firstPseudoHeader = lastPseudoHeader = null;
            } else {
                lastPseudoHeader = entry.toPrev;
            }
        } else if (entry == firstPseudoHeader) {
            firstPseudoHeader = entry.toNext;
        }
        entry.unlink();
    }

    @Override
    protected MultiMapEntry<CharSequence, CharSequence> newEntry(CharSequence key, CharSequence value, int keyHash) {
        Http2MultiMapEntry entry = new Http2MultiMapEntry(key, value, keyHash);
        if (hasPseudoHeaderFormat(key)) {
            if (firstPseudoHeader == null) {
                assert lastPseudoHeader == null;
                firstPseudoHeader = lastPseudoHeader = entry;
                if (firstNormalHeader != null) {
                    entry.linkBefore(firstNormalHeader);
                }
            } else {
                entry.linkAfter(lastPseudoHeader);
                lastPseudoHeader = entry;
            }
        } else {
            if (firstNormalHeader == null) {
                firstNormalHeader = lastNormalHeader = entry;
                if (lastPseudoHeader != null) {
                    entry.linkAfter(lastPseudoHeader);
                }
            } else {
                entry.linkAfter(lastNormalHeader);
                lastNormalHeader = entry;
            }
        }
        return entry;
    }

    private static final class Http2MultiMapEntry extends MultiMapEntry<CharSequence, CharSequence> {
        // Total-Order linkages that preserves pseudo-headers in the front.
        private Http2MultiMapEntry toNext;
        private Http2MultiMapEntry toPrev;

        private Http2MultiMapEntry(CharSequence key, CharSequence value, int keyHash) {
            super(key, value, keyHash);
        }

        void linkBefore(Http2MultiMapEntry entry) {
            toNext = entry;
            if (entry.toPrev != null) {
                toPrev = entry.toPrev;
                toPrev.toNext = this;
            }
            entry.toPrev = this;
        }

        void linkAfter(Http2MultiMapEntry entry) {
            toPrev = entry;
            if (entry.toNext != null) {
                toNext = entry.toNext;
                toNext.toPrev = this;
            }
            entry.toNext = this;
        }

        void unlink() {
            if (toNext != null) {
                toNext.toPrev = toPrev;
            }
            if (toPrev != null) {
                toPrev.toNext = toNext;
            }
            toNext = null;
            toPrev = null;
        }

        int keyHash() {
            return keyHash;
        }
    }

    @Override
    public Iterator<Entry<CharSequence, CharSequence>> iterator() {
        return isEmpty() ? emptyIterator() : new FullHttp2EntryIterator(
                firstPseudoHeader != null ? firstPseudoHeader : firstNormalHeader);
    }

    private final class FullHttp2EntryIterator implements Iterator<Entry<CharSequence, CharSequence>> {
        private Http2MultiMapEntry curr;
        private Http2MultiMapEntry prev;

        FullHttp2EntryIterator(Http2MultiMapEntry entry) {
            curr = entry;
        }

        @Override
        public boolean hasNext() {
            return curr != null;
        }

        @Override
        public Entry<CharSequence, CharSequence> next() {
            if (curr == null) {
                throw new NoSuchElementException();
            }
            prev = curr;
            curr = curr.toNext;
            return prev;
        }

        @Override
        public void remove() {
            if (prev == null) {
                throw new IllegalStateException();
            }
            final int i = index(prev.keyHash());
            removeEntry(entries[i], prev, i);
            prev = null;
        }
    }
}
