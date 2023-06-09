/*
* Copyright 2015 The Netty Project
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
package io.netty5.microbench.buffer;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.nio.ByteBuffer;

public class BufferBenchmark extends AbstractMicrobenchmark {

    private static final byte BYTE = '0';

    private ByteBuffer byteBuffer;
    private ByteBuffer directByteBuffer;
    private Buffer buffer;
    private Buffer directBuffer;
    private Buffer directBufferPooled;

    @Setup
    public void setup() {
        byteBuffer = ByteBuffer.allocate(8);
        directByteBuffer = ByteBuffer.allocateDirect(8);
        buffer = BufferAllocator.onHeapUnpooled().allocate(8);
        directBuffer = BufferAllocator.offHeapUnpooled().allocate(8);
        directBufferPooled = BufferAllocator.offHeapPooled().allocate(8);
    }

    @TearDown
    public void tearDown() {
        buffer.close();
        directBuffer.close();
        directBufferPooled.close();
    }

    @Benchmark
    public ByteBuffer setByteBufferHeap() {
        return byteBuffer.put(0, BYTE);
    }

    @Benchmark
    public ByteBuffer setByteBufferDirect() {
        return directByteBuffer.put(0, BYTE);
    }

    @Benchmark
    public Buffer setBufferHeap() {
        return buffer.setByte(0, BYTE);
    }

    @Benchmark
    public Buffer setBufferDirect() {
        return directBuffer.setByte(0, BYTE);
    }

    @Benchmark
    public Buffer setBufferDirectPooled() {
        return directBufferPooled.setByte(0, BYTE);
    }
}
