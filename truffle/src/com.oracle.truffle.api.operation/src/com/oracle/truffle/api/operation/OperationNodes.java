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
package com.oracle.truffle.api.operation;

import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

public abstract class OperationNodes<T extends RootNode & OperationRootNode> {
    private final OperationParser<? extends OperationBuilder> parse;
    @CompilationFinal(dimensions = 1) protected T[] nodes;
    @CompilationFinal(dimensions = 1) protected volatile Source[] sources;
    @CompilationFinal private boolean hasInstrumentation;

    protected OperationNodes(OperationParser<? extends OperationBuilder> parse) {
        this.parse = parse;
    }

    @Override
    public String toString() {
        return String.format("OperationNodes %s", Arrays.toString(nodes));
    }

    @SuppressWarnings({"unchecked", "cast", "rawtypes"})
    public List<T> getNodes() {
        return List.of(nodes);
    }

    public boolean hasSources() {
        return sources != null;
    }

    public boolean hasInstrumentation() {
        return hasInstrumentation;
    }

    public OperationParser<? extends OperationBuilder> getParser() {
        return parse;
    }

    private boolean checkNeedsWork(OperationConfig config) {
        if (config.isWithSource() && !hasSources()) {
            return true;
        }
        if (config.isWithInstrumentation() && !hasInstrumentation()) {
            return true;
        }
        return false;
    }

    public boolean updateConfiguration(OperationConfig config) {
        if (!checkNeedsWork(config)) {
            return false;
        }

        CompilerDirectives.transferToInterpreterAndInvalidate();
        reparse(config);
        return true;
    }

    @SuppressWarnings("hiding")
    protected abstract void reparseImpl(OperationConfig config, OperationParser<? extends OperationBuilder> parse, T[] nodes);

    void reparse(OperationConfig config) {
        CompilerAsserts.neverPartOfCompilation("parsing should never be compiled");
        reparseImpl(config, parse, nodes);
    }

    /**
     * Checks if the sources are present, and if not tries to reparse to get them.
     */
    protected final void ensureSources() {
        if (sources == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            reparse(OperationConfig.WITH_SOURCE);
        }
    }

    protected final void ensureInstrumentation() {
        if (!hasInstrumentation) {
            CompilerDirectives.transferToInterpreter();
            reparse(OperationConfig.COMPLETE);
        }
    }
}
