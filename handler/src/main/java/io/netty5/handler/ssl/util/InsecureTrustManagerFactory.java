/*
 * Copyright 2014 The Netty Project
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
package io.netty5.handler.ssl.util;

import io.netty5.util.internal.EmptyArrays;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

/**
 * An insecure {@link TrustManagerFactory} that trusts all X.509 certificates without any verification.
 * <p>
 * <strong>NOTE:</strong>
 * Never use this {@link TrustManagerFactory} in production.
 * It is purely for testing purposes, and thus it is very insecure.
 * </p>
 */
@TestOnly
public final class InsecureTrustManagerFactory extends SimpleTrustManagerFactory {

    private static final Logger logger = LoggerFactory.getLogger(InsecureTrustManagerFactory.class);

    public static final TrustManagerFactory INSTANCE = new InsecureTrustManagerFactory();

    private static final TrustManager tm = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String s) {
            if (logger.isDebugEnabled()) {
                logger.debug("Accepting a client certificate: " + chain[0].getSubjectDN());
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String s) {
            if (logger.isDebugEnabled()) {
                logger.debug("Accepting a server certificate: " + chain[0].getSubjectDN());
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return EmptyArrays.EMPTY_X509_CERTIFICATES;
        }
    };

    private InsecureTrustManagerFactory() { }

    @Override
    protected void engineInit(KeyStore keyStore) throws Exception { }

    @Override
    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws Exception { }

    @Override
    protected TrustManager[] engineGetTrustManagers() {
        return new TrustManager[] { tm };
    }
}
