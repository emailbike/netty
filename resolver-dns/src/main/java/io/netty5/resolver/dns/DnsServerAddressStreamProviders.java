/*
 * Copyright 2017 The Netty Project
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
package io.netty5.resolver.dns;

import io.netty5.util.internal.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility methods related to {@link DnsServerAddressStreamProvider}.
 */
public final class DnsServerAddressStreamProviders {

    private static final Logger LOGGER = LoggerFactory.getLogger(DnsServerAddressStreamProviders.class);
    private static final MethodHandle STREAM_PROVIDER_CONSTRUCTOR_HANDLE;
    private static final String MACOS_PROVIDER_CLASS_NAME =
            "io.netty5.resolver.dns.macos.MacOSDnsServerAddressStreamProvider";

    static {
        MethodHandle constructorHandle = null;
        if (PlatformDependent.isOsx()) {
            try {
                // As MacOSDnsServerAddressStreamProvider is contained in another jar which depends on this jar
                // we use reflection to use it if its on the classpath.
                Object maybeProvider = AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                    try {
                        return Class.forName(
                                MACOS_PROVIDER_CLASS_NAME,
                                true,
                                DnsServerAddressStreamProviders.class.getClassLoader());
                    } catch (Throwable cause) {
                        return cause;
                    }
                });
                if (maybeProvider instanceof Class) {
                    @SuppressWarnings("unchecked")
                    Class<? extends DnsServerAddressStreamProvider> providerClass =
                            (Class<? extends DnsServerAddressStreamProvider>) maybeProvider;
                    MethodHandles.Lookup lookup = MethodHandles.lookup();
                    constructorHandle = lookup.findConstructor(providerClass, MethodType.methodType(void.class));
                    constructorHandle.invoke(); // ctor ensures availability
                    LOGGER.debug("{}: available", MACOS_PROVIDER_CLASS_NAME);
                } else {
                    throw (Throwable) maybeProvider;
                }
            } catch (ClassNotFoundException cause) {
                LOGGER.warn("Can not find {} in the classpath, fallback to system defaults. This may result in "
                        + "incorrect DNS resolutions on MacOS. Check whether you have a dependency on "
                        + "'io.netty:netty5-resolver-dns-native-macos'", MACOS_PROVIDER_CLASS_NAME);
            } catch (Throwable cause) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.error("Unable to load {}, fallback to system defaults. This may result in "
                            + "incorrect DNS resolutions on MacOS. Check whether you have a dependency on "
                            + "'io.netty:netty5-resolver-dns-native-macos'", MACOS_PROVIDER_CLASS_NAME, cause);
                } else {
                    LOGGER.error("Unable to load {}, fallback to system defaults. This may result in "
                            + "incorrect DNS resolutions on MacOS. Check whether you have a dependency on "
                            + "'io.netty:netty5-resolver-dns-native-macos'. Use DEBUG level to see the full stack: {}",
                            MACOS_PROVIDER_CLASS_NAME,
                            cause.getCause() != null ? cause.getCause().toString() : cause.toString());
                }
                constructorHandle = null;
            }
        }
        STREAM_PROVIDER_CONSTRUCTOR_HANDLE = constructorHandle;
    }

    private DnsServerAddressStreamProviders() {
    }

    /**
     * A {@link DnsServerAddressStreamProvider} which inherits the DNS servers from your local host's configuration.
     * <p>
     * Note that only macOS and Linux are currently supported.
     * @return A {@link DnsServerAddressStreamProvider} which inherits the DNS servers from your local host's
     * configuration.
     */
    public static DnsServerAddressStreamProvider platformDefault() {
        if (STREAM_PROVIDER_CONSTRUCTOR_HANDLE != null) {
            try {
                return (DnsServerAddressStreamProvider) STREAM_PROVIDER_CONSTRUCTOR_HANDLE.invoke();
            } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
                // ignore
            } catch (Throwable cause) {
                PlatformDependent.throwException(cause);
            }
        }
        return unixDefault();
    }

    public static DnsServerAddressStreamProvider unixDefault() {
        return DefaultProviderHolder.DEFAULT_DNS_SERVER_ADDRESS_STREAM_PROVIDER;
    }

    // We use a Holder class to only initialize DEFAULT_DNS_SERVER_ADDRESS_STREAM_PROVIDER if we really
    // need it.
    private static final class DefaultProviderHolder {
        // We use 5 minutes which is the same as what OpenJDK is using in sun.net.dns.ResolverConfigurationImpl.
        private static final long REFRESH_INTERVAL = TimeUnit.MINUTES.toNanos(5);

        // TODO(scott): how is this done on Windows? This may require a JNI call to GetNetworkParams
        // https://msdn.microsoft.com/en-us/library/aa365968(VS.85).aspx.
        static final DnsServerAddressStreamProvider DEFAULT_DNS_SERVER_ADDRESS_STREAM_PROVIDER =
                new DnsServerAddressStreamProvider() {
                    private volatile DnsServerAddressStreamProvider currentProvider = provider();
                    private final AtomicLong lastRefresh = new AtomicLong(System.nanoTime());

                    @Override
                    public DnsServerAddressStream nameServerAddressStream(String hostname) {
                        long last = lastRefresh.get();
                        DnsServerAddressStreamProvider current = currentProvider;
                        if (System.nanoTime() - last > REFRESH_INTERVAL) {
                            // This is slightly racy which means it will be possible still use the old configuration
                            // for a small amount of time, but that's ok.
                            if (lastRefresh.compareAndSet(last, System.nanoTime())) {
                                current = currentProvider = provider();
                            }
                        }
                        return current.nameServerAddressStream(hostname);
                    }

                    private DnsServerAddressStreamProvider provider() {
                        // If on windows just use the DefaultDnsServerAddressStreamProvider.INSTANCE as otherwise
                        // we will log some error which may be confusing.
                        return PlatformDependent.isWindows() ? DefaultDnsServerAddressStreamProvider.INSTANCE :
                                UnixResolverDnsServerAddressStreamProvider.parseSilently();
                    }
                };
    }
}
