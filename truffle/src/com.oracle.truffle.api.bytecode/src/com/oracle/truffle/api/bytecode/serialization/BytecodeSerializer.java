/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode.serialization;

import java.io.DataOutput;
import java.io.IOException;

import com.oracle.truffle.api.bytecode.BytecodeRootNode;

/**
 * Represents a class that can serialize constants in a bytecode interpreter.
 *
 * A {@link BytecodeSerializer} establishes a byte encoding for objects. The
 * {@link BytecodeDeserializer} used to deserialize an interpreter should follow the same encoding.
 *
 * For example:
 *
 * <pre>
 * public class MyBytecodeSerializer implements BytecodeSerializer {
 *     &#64;Override
 *     public void serialize(SerializerContext context, DataOutput buffer, Object object) throws IOException {
 *         if (object instanceof Integer i) {
 *             buffer.writeByte(0);
 *             buffer.writeInt(i);
 *         } else if (object instanceof String s) {
 *             buffer.writeByte(1);
 *             buffer.writeUTF(s);
 *         } else ...
 *     }
 * }
 * </pre>
 *
 * @see com.oracle.truffle.api.bytecode.GenerateBytecode#enableSerialization
 */
@FunctionalInterface
public interface BytecodeSerializer {

    interface SerializerContext {
        void writeBytecodeNode(DataOutput buffer, BytecodeRootNode node) throws IOException;
    }

    /**
     * The serialization process. The byte encoding of {@code object} should be written to
     * {@code buffer}.
     *
     * The {@code context} is supplied so that a {@link BytecodeSerializer} can transitively
     * serialize other {@link BytecodeRootNode root nodes} (e.g., inner functions) if necessary.
     *
     * Must not be dependent on any side-effects of the language.
     */
    void serialize(SerializerContext context, DataOutput buffer, Object object) throws IOException;
}
