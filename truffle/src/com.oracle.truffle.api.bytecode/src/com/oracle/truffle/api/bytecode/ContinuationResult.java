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

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Representation of a continuation closure, consisting of a {@link #location}, {@link #frame
 * interpreter state}, and a {@link #result}. A {@link ContinuationResult} is returned when the
 * interpreter yields.
 *
 * Below illustrates an example usage of {@link ContinuationResult}.
 *
 * <pre>
 * // Assume yieldingRootNode implements the following pseudocode:
 * //
 * // fun f(x):
 * //   y = yield (x + 1)
 * //   return x + y
 * //
 * MyBytecodeRootNode yieldingRootNode = ...;
 *
 * // The result is a ContinuationResult
 * ContinuationResult yielded = (ContinuationResult) yieldingRootNode.getCallTarget().call(42);
 * assert yielded.getResult() == 43;
 *
 * // Resume the continuation using continueWith. Pass 58 as the value for yield.
 * Integer returned = (Integer) yielded.continueWith(58);
 * assert returned == 100;
 * </pre>
 *
 * For performance reasons, a language may wish to define an inline cache over continuation
 * locations. In such a case, they should not call {@link #continueWith}, but instead cache and call
 * the {@link #getContinuationRootNode root node} or {@link #getContinuationCallTarget call target}
 * directly. Be careful to conform to the {@link #getContinuationCallTarget calling convention}.
 */
public final class ContinuationResult {

    private final ContinuationLocation location;
    private final MaterializedFrame frame;
    private final Object result;

    ContinuationResult(ContinuationLocation location, MaterializedFrame frame, Object result) {
        this.location = location;
        this.frame = frame;
        this.result = result;
    }

    /**
     * Resumes the continuation. The {@link #value} becomes the value produced by the yield
     * operation in the resumed execution.
     */
    public Object continueWith(Object value) {
        return getContinuationCallTarget().call(frame, value);
    }

    /**
     * Returns the root node that resumes execution.
     *
     * Note that the continuation root node has a specific calling convention. See
     * {@link #getContinuationCallTarget} for more details, or invoke the root node directly using
     * {@link #continueWith}.
     *
     * @see #getContinuationCallTarget()
     */
    public RootNode getContinuationRootNode() {
        return location.getRootNode();
    }

    /**
     * Returns the call target for the {@link #getContinuationRootNode continuation root node}. The
     * call target can be invoked to resume the continuation.
     *
     * Languages can invoke this call target directly via {@link #continueWith}. However, they may
     * instead choose to access and call this call target directly (e.g., to register it in an
     * inline cache).
     *
     * The call target takes two parameters: the interpreter {@link #getFrame frame} and an
     * {@code Object} to resume execution with. The {@code Object} becomes the value produced by the
     * yield operation in the resumed execution.
     */
    public RootCallTarget getContinuationCallTarget() {
        return location.getRootNode().getCallTarget();
    }

    /**
     * Returns the state of the interpreter at the point that it was suspended.
     */
    public MaterializedFrame getFrame() {
        return frame;
    }

    /**
     * Returns the value computed by the yield operation.
     */
    public Object getResult() {
        return result;
    }

    @Override
    public String toString() {
        return String.format("ContinuationResult [location=%s, result=%s]", location, result);
    }
}
