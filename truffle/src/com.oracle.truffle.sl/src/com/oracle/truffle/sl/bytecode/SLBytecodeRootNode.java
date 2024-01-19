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
package com.oracle.truffle.sl.bytecode;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.bytecode.debug.BytecodeDebugTraceListener;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.nodes.SLExpressionNode;
import com.oracle.truffle.sl.nodes.SLRootNode;
import com.oracle.truffle.sl.nodes.SLTypes;
import com.oracle.truffle.sl.nodes.expression.SLAddNode;
import com.oracle.truffle.sl.nodes.expression.SLDivNode;
import com.oracle.truffle.sl.nodes.expression.SLEqualNode;
import com.oracle.truffle.sl.nodes.expression.SLFunctionLiteralNode;
import com.oracle.truffle.sl.nodes.expression.SLLessOrEqualNode;
import com.oracle.truffle.sl.nodes.expression.SLLessThanNode;
import com.oracle.truffle.sl.nodes.expression.SLLogicalNotNode;
import com.oracle.truffle.sl.nodes.expression.SLMulNode;
import com.oracle.truffle.sl.nodes.expression.SLReadPropertyNode;
import com.oracle.truffle.sl.nodes.expression.SLSubNode;
import com.oracle.truffle.sl.nodes.expression.SLWritePropertyNode;
import com.oracle.truffle.sl.nodes.util.SLToBooleanNode;
import com.oracle.truffle.sl.nodes.util.SLUnboxNode;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.sl.runtime.SLUndefinedNameException;

@GenerateBytecode(//
                languageClass = SLLanguage.class, //
                decisionsFile = "decisions.json", //
                boxingEliminationTypes = {long.class, boolean.class}, //
                enableSerialization = true)
@TypeSystemReference(SLTypes.class)
@OperationProxy(SLAddNode.class)
@OperationProxy(SLDivNode.class)
@OperationProxy(SLEqualNode.class)
@OperationProxy(SLLessOrEqualNode.class)
@OperationProxy(SLLessThanNode.class)
@OperationProxy(SLLogicalNotNode.class)
@OperationProxy(SLMulNode.class)
@OperationProxy(SLReadPropertyNode.class)
@OperationProxy(SLSubNode.class)
@OperationProxy(SLWritePropertyNode.class)
@OperationProxy(SLUnboxNode.class)
@OperationProxy(SLFunctionLiteralNode.class)
@OperationProxy(SLToBooleanNode.class)
@ShortCircuitOperation(name = "SLAnd", booleanConverter = SLToBooleanNode.class, continueWhen = true)
@ShortCircuitOperation(name = "SLOr", booleanConverter = SLToBooleanNode.class, continueWhen = false)
public abstract class SLBytecodeRootNode extends SLRootNode implements BytecodeRootNode, BytecodeDebugTraceListener {

    protected SLBytecodeRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super((SLLanguage) language, frameDescriptor);
    }

    protected TruffleString tsName;

    private transient String saved;

    public void executeProlog(VirtualFrame frame) {
        if (saved == null) {
            saved = dump();
            printDump(saved);
        }
    }

    public void executeEpilog(VirtualFrame frame, Object returnValue, Throwable throwable) {
        String newDump = dump();
        if (!newDump.equals(saved)) {
            printDump(newDump);
            saved = newDump;
        }
    }

    @TruffleBoundary
    private static void printDump(String dump) {
        System.out.println(dump);
    }

    @Override
    public SLExpressionNode getBodyNode() {
        return null;
    }

    @Override
    public SourceSection getSourceSection() {
        return findSourceSectionAtBci(0);
    }

    @Override
    public TruffleString getTSName() {
        return tsName;
    }

    public void setTSName(TruffleString tsName) {
        this.tsName = tsName;
    }

    @Operation
    @TypeSystemReference(SLTypes.class)
    public static final class SLInvoke {
        @Specialization(limit = "3", //
                        guards = "function.getCallTarget() == cachedTarget", //
                        assumptions = "callTargetStable")
        @SuppressWarnings("unused")
        protected static Object doDirect(SLFunction function, @Variadic Object[] arguments,
                        @Bind("this") Node node,
                        @Cached("function.getCallTargetStable()") Assumption callTargetStable,
                        @Cached("function.getCallTarget()") RootCallTarget cachedTarget,
                        @Cached("create(cachedTarget)") DirectCallNode callNode) {

            /* Inline cache hit, we are safe to execute the cached call target. */
            Object returnValue = callNode.call(arguments);
            return returnValue;
        }

        /**
         * Slow-path code for a call, used when the polymorphic inline cache exceeded its maximum
         * size specified in <code>INLINE_CACHE_SIZE</code>. Such calls are not optimized any
         * further, e.g., no method inlining is performed.
         */
        @Specialization(replaces = "doDirect")
        protected static Object doIndirect(SLFunction function, @Variadic Object[] arguments,
                        @Cached IndirectCallNode callNode) {
            /*
             * SL has a quite simple call lookup: just ask the function for the current call target,
             * and call it.
             */
            return callNode.call(function.getCallTarget(), arguments);
        }

        @Specialization
        protected static Object doInterop(
                        Object function,
                        @Variadic Object[] arguments,
                        @CachedLibrary(limit = "3") InteropLibrary library,
                        @Bind("$root") Node node,
                        @Bind("$bci") int bci) {
            try {
                return library.execute(function, arguments);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                /* Execute was not successful. */
                throw SLUndefinedNameException.undefinedFunction(node, bci, function);
            }
        }
    }
}
