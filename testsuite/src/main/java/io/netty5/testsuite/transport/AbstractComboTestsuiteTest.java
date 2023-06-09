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
package io.netty5.testsuite.transport;

import io.netty5.bootstrap.AbstractBootstrap;
import io.netty5.buffer.BufferAllocator;
import io.netty5.testsuite.transport.TestsuitePermutation.AllocatorConfig;
import io.netty5.testsuite.util.TestUtils;
import io.netty5.util.internal.StringUtil;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class AbstractComboTestsuiteTest<SB extends AbstractBootstrap<?, ?, ?>,
        CB extends AbstractBootstrap<?, ?, ?>> {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected volatile CB cb;
    protected volatile SB sb;

    protected abstract List<TestsuitePermutation.BootstrapComboFactory<SB, CB>> newFactories();

    protected List<AllocatorConfig> newAllocators() {
        return TestsuitePermutation.allocator();
    }

    protected void run(TestInfo testInfo, Runner<SB, CB> runner) throws Throwable {
        List<TestsuitePermutation.BootstrapComboFactory<SB, CB>> combos = newFactories();
        String methodName = TestUtils.testMethodName(testInfo);
        for (AllocatorConfig config: newAllocators()) {
            int i = 0;
            for (TestsuitePermutation.BootstrapComboFactory<SB, CB> e : combos) {
                sb = e.newServerInstance();
                cb = e.newClientInstance();
                configure(sb, cb, config.bufferAllocator);
                logger.info(String.format(
                        "Running: %s %d of %d (%s + %s) with %s",
                        methodName, ++i, combos.size(), sb, cb,
                        StringUtil.simpleClassName(config.bufferAllocator)));
                runner.run(sb, cb);
            }
        }
    }

    protected abstract void configure(SB sb, CB cb, BufferAllocator bufferAllocator);

    public interface Runner<SB extends AbstractBootstrap<?, ?, ?>, CB extends AbstractBootstrap<?, ?, ?>> {
        void run(SB sb, CB cb) throws Throwable;
    }
}
