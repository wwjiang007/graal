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
package com.oracle.truffle.api.bytecode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.introspection.Argument;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;

/**
 * Generates a bytecode interpreter using the Bytecode DSL. The Bytecode DSL automatically produces
 * an optimizing bytecode interpreter from a set of Node-like "operations". The following is an
 * example of an Bytecode DSL interpreter with a single {@code Add} operation.
 *
 * <pre>
 * &#64;GenerateBytecode(languageClass = MyLanguage.class)
 * public abstract class MyBytecodeRootNode extends RootNode implements BytecodeRootNode {
 *     &#64;Operation
 *     public static final class Add {
 *         &#64;Specialization
 *         public static int doInts(int lhs, int rhs) {
 *             return lhs + rhs;
 *         }
 *
 *         &#64;Specialization
 *         &#64;TruffleBoundary
 *         public static String doStrings(String lhs, String rhs) {
 *             return lhs + rhs;
 *         }
 *     }
 * }
 * </pre>
 *
 * The DSL generates a node suffixed with {@code Gen} (e.g., {@code MyBytecodeRootNodeGen} that
 * contains (among other things) a full bytecode encoding, an optimizing interpreter, and a
 * {@code Builder} class to generate and validate bytecode automatically.
 *
 * A node can opt in to additional features, like an
 * {@code #enableUncachedInterpreter uncached interpreter},
 * {@code #boxingEliminationTypes boxing elimination},
 * {@code #enableQuickening quickened instructions}, and more. The fields of this annotation control
 * which features are included in the generated interpreter.
 *
 * For information about using the Bytecode DSL, please consult the
 * <a href="https://github.com/oracle/graal/blob/master/truffle/docs/BytecodeDSL.md">tutorial</a>
 * and the <a href=
 * "https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/package-summary.html">Javadoc</a>.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface GenerateBytecode {
    /**
     * The {@link TruffleLanguage} class associated with this node.
     */
    Class<? extends TruffleLanguage<?>> languageClass();

    /**
     * Whether to generate an uncached interpreter.
     *
     * The uncached interpreter improves start-up performance by executing
     * {@link com.oracle.truffle.api.dsl.GenerateUncached uncached} nodes instead of allocating and
     * executing cached (specializing) nodes.
     *
     * The node will transition to a specializing interpreter after enough invocations/back-edges
     * (as determined by the {@link BytecodeRootNode#setUncachedInterpreterThreshold uncached
     * interpreter threshold}).
     */
    boolean enableUncachedInterpreter() default false;

    /**
     * Whether the generated interpreter should support serialization and deserialization.
     *
     * When serialization is enabled, the generated class will define static {@code serialize} and
     * {@code deserialize} methods that can be used to convert bytecode nodes to and from a
     * serialized byte array representation.
     *
     * This feature can be used to avoid the overhead of reparsing source code. Note that there is
     * still some overhead, as it does not trivially copy bytecode directly: in order to validate
     * the bytecode (for a balanced stack pointer, valid branches, etc.), serialization encodes the
     * calls to the builder object and deserialization replays those calls.
     *
     * Note that the generated {@code deserialize} method takes a {@link java.util.function.Supplier
     * Supplier<DataInput>} rather than a {@link java.io.DataInput} directly. The supplier should
     * produce a fresh {@link java.io.DataInput} each time because the input may be processed
     * multiple times (due to {@link BytecodeNodes#reparse reparsing}).
     *
     * @see com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer
     * @see com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer
     */
    boolean enableSerialization() default false;

    /**
     * Whether the generated interpreter should support instrumentation.
     *
     * When instrumentation is enabled, the generated builder will define <code>startTag(...)</code>
     * and <code>endTag()</code> methods that can be used to annotate the bytecode with
     * {@link com.oracle.truffle.api.instrumentation.Tag tags}.
     *
     * @see #enableRootTagging()
     */
    boolean enableInstrumentation() default false;

    /**
     * Enables automatic root tagging if {@link #enableInstrumentation() instrumentation} is
     * enabled. Automatic root tagging automatically annotates each root node with {@link RootTag}.
     */
    boolean enableRootTagging() default true;

    /**
     * Whether to use unsafe array accesses.
     *
     * Unsafe accesses are faster, but they do not perform array bounds checks. This means it is
     * possible (though unlikely) for unsafe accesses to cause undefined behaviour.
     */
    boolean allowUnsafe() default true;

    /**
     * Whether the generated interpreter should support coroutines via a {@code yield} operation.
     *
     * The yield operation returns a {@link ContinuationResult} from the current point in execution.
     * The {@link ContinuationResult} saves the current state of the interpreter for resumption at a
     * later point in time, as well as an optional return value.
     *
     * @see com.oracle.truffle.api.bytecode.ContinuationResult
     */
    boolean enableYield() default false;

    /**
     * Whether quickened bytecodes should be emitted.
     *
     * Quickened versions of instructions support a subset of the
     * {@link com.oracle.truffle.api.dsl.Specialization specializations} defined by an operation.
     * They can improve interpreted performance by reducing footprint and requiring fewer guards.
     *
     * Quickened versions of operations can be specified using
     * {@link com.oracle.truffle.api.bytecode.ForceQuickening}. When an instruction re-specializes
     * itself, the interpreter attempts to automatically replace it with a quickened instruction.
     */
    boolean enableQuickening() default true;

    /**
     * Whether the generated interpreter should always store the bytecode index (bci) in the frame.
     *
     * When this flag is set, the language can use {@link BytecodeRootNode#readBciFromFrame} to read
     * the bci from the frame. The interpreter does not always store the bci, so it is undefined
     * behaviour to invoke {@link BytecodeRootNode#readBciFromFrame} when this flag is
     * {@code false}.
     *
     * Note that this flag can slow down interpreter performance, so it should only be set if the
     * language needs fast-path access to the bci outside of the current operation (e.g., for
     * closures or frame introspection). Within the current operation, you can bind the bci as a
     * parameter {@code @Bind("$bci")} on the fast path; if you only need access to the bci on the
     * slow path, it can be computed from a stack walk using {@link BytecodeRootNode#findBci}.
     */
    boolean storeBciInFrame() default false;

    /**
     * Path to a file containing optimization decisions. This file is generated using tracing on a
     * representative corpus of code.
     *
     * This feature is not yet supported.
     *
     * @see #forceTracing()
     */
    String decisionsFile() default "";

    /**
     * Path to files with manually-provided optimization decisions. These files can be used to
     * encode optimizations that are not generated automatically via tracing.
     *
     * This feature is not yet supported.
     */
    String[] decisionOverrideFiles() default {};

    /**
     * Whether to build the interpreter with tracing. Can also be configured using the
     * {@code truffle.dsl.OperationsEnableTracing} option during compilation.
     *
     * Note that this is a debug option that should not be used in production. Also note that this
     * field only affects code generation: whether tracing is actually performed at run time is
     * still controlled by the aforementioned option.
     *
     * This feature is not yet supported.
     */
    boolean forceTracing() default false;

    /**
     * Primitive types for which the interpreter should attempt to avoid boxing.
     *
     * If boxing elimination types are provided, the cached interpreter will generate instruction
     * variants that load/store primitive values when possible. It will automatically use these
     * instructions in a best-effort manner (falling back on boxed representations when necessary).
     */
    Class<?>[] boxingEliminationTypes() default {};

    /**
     * Whether to generate introspection data for specializations. The data is accessible using
     * {@link Argument#getSpecializationInfo()}.
     *
     * @see com.oracle.truffle.api.bytecode.introspection.BytecodeIntrospection
     */
    boolean enableSpecializationIntrospection() default true;

}
