/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.BytecodeNodeWithStoredBci.MyException;
import com.oracle.truffle.api.bytecode.test.BytecodeNodeWithStoredBci.RootAndFrame;
import com.oracle.truffle.api.bytecode.test.example.BytecodeDSLExampleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class ReadBciFromFrameTest {
    /*
     * NB: The tests in this class test some undocumented behaviour.
     *
     * We only store the bci back into the frame when control can possibly reach a point where the
     * bci can be read. This happens when exiting the root node (return, throw, yield) or executing
     * a custom operation that has a specialization with a cached parameter. An example of this
     * latter case is a @Cached parameter that calls another root node, which then performs a stack
     * walk. @Bind variables are also included in this criteria, because the operation
     * could @Bind("$root") and then invoke {@link BytecodeRootNode#readBciFromFrame} on $root.
     */
    public BytecodeNodeWithStoredBci parseNode(BytecodeParser<BytecodeNodeWithStoredBciGen.Builder> builder) {
        return BytecodeNodeWithStoredBciGen.create(BytecodeConfig.WITH_SOURCE, builder).getNodes().get(0);
    }

    @Test
    public void testSimple() {
        Source source = Source.newBuilder("test", "arg0 ? foo : bar", "testSimple").build();
        BytecodeNodeWithStoredBci root = parseNode(b -> {
            b.beginRoot(null);
            b.beginSource(source);
            b.beginSourceSection(0, 16);
            b.beginBlock();

            BytecodeLocal rootAndFrame = b.createLocal();
            b.beginStoreLocal(rootAndFrame);
            b.emitMakeRootAndFrame();
            b.endStoreLocal();

            b.beginReturn();
            b.beginConditional();

            b.beginSourceSection(0, 4); // arg0
            b.emitLoadArgument(0);
            b.endSourceSection();

            b.beginSourceSection(7, 3); // foo
            b.beginGetSourceCharacters();
            b.emitLoadLocal(rootAndFrame);
            b.endGetSourceCharacters();
            b.endSourceSection();

            b.beginSourceSection(13, 3); // bar
            b.beginGetSourceCharacters();
            b.emitLoadLocal(rootAndFrame);
            b.endGetSourceCharacters();
            b.endSourceSection();

            b.endConditional();
            b.endReturn();

            b.endBlock();
            b.endSourceSection();
            b.endSource();
            b.endRoot();
        });

        assertEquals("arg0 ? foo : bar", root.getCallTarget().call(true));
        assertEquals("arg0 ? foo : bar", root.getCallTarget().call(false));
    }

    @Test
    public void testStoreOnReturn() {
        // The bci should be updated to the return bci, thus causing the matched source section to
        // be the outer one.
        Source source = Source.newBuilder("test", "return foo", "testSimple").build();
        BytecodeNodeWithStoredBci root = parseNode(b -> {
            b.beginRoot(null);
            b.beginSource(source);
            b.beginBlock();

            BytecodeLocal rootAndFrame = b.createLocal();
            b.beginStoreLocal(rootAndFrame);
            b.emitMakeRootAndFrame();
            b.endStoreLocal();

            b.beginSourceSection(0, 10); // return foo
            b.beginReturn();

            b.beginSourceSection(7, 3); // foo
            b.emitLoadLocal(rootAndFrame);
            b.endSourceSection();

            b.endReturn();
            b.endSourceSection();

            b.endBlock();
            b.endSource();
            b.endRoot();
        });

        RootAndFrame result = (RootAndFrame) root.getCallTarget().call();
        assertEquals("return foo", result.getSourceCharacters());
    }

    @Test
    public void testStoreOnThrow() {
        // The bci should be updated when an exception is thrown.
        Source source = Source.newBuilder("test", "throw foo", "testSimple").build();
        BytecodeNodeWithStoredBci root = parseNode(b -> {
            b.beginRoot(null);
            b.beginSource(source);
            b.beginBlock();

            BytecodeLocal rootAndFrame = b.createLocal();
            b.beginStoreLocal(rootAndFrame);
            b.emitMakeRootAndFrame();
            b.endStoreLocal();

            b.beginSourceSection(0, 9); // throw foo
            b.beginThrow();

            b.beginSourceSection(6, 3); // foo
            b.emitLoadLocal(rootAndFrame);
            b.endSourceSection();

            b.endThrow();
            b.endSourceSection();

            b.endBlock();
            b.endSource();
            b.endRoot();
        });

        try {
            root.getCallTarget().call();
            fail("Expected call to fail");
        } catch (MyException ex) {
            RootAndFrame result = (RootAndFrame) ex.result;
            assertEquals("throw foo", result.getSourceCharacters());
        }
    }

    @Test
    public void testStoreOnYield() {
        // The bci should be updated when a coroutine yields.
        Source source = Source.newBuilder("test", "yield foo; return bar", "testSimple").build();
        BytecodeNodeWithStoredBci root = parseNode(b -> {
            b.beginRoot(null);
            b.beginSource(source);
            b.beginBlock();

            BytecodeLocal rootAndFrame = b.createLocal();
            b.beginStoreLocal(rootAndFrame);
            b.emitMakeRootAndFrame();
            b.endStoreLocal();

            b.beginSourceSection(0, 17); // yield foo; return
            b.beginBlock();

            b.beginSourceSection(0, 9); // yield foo
            b.beginYield();
            b.emitLoadLocal(rootAndFrame);
            b.endYield();
            b.endSourceSection();

            b.beginSourceSection(11, 10); // return bar
            b.beginReturn();
            b.emitLoadLocal(rootAndFrame);
            b.endReturn();
            b.endSourceSection();

            b.endBlock();
            b.endSourceSection();

            b.endBlock();
            b.endSource();
            b.endRoot();
        });

        ContinuationResult contResult = (ContinuationResult) root.getCallTarget().call();
        RootAndFrame result = (RootAndFrame) contResult.getResult();
        assertEquals("yield foo", result.getSourceCharacters());
        contResult.continueWith(null);
        assertEquals("return bar", result.getSourceCharacters());
    }
}

@GenerateBytecode(languageClass = BytecodeDSLExampleLanguage.class, storeBciInFrame = true, enableYield = true)
abstract class BytecodeNodeWithStoredBci extends RootNode implements BytecodeRootNode {

    protected BytecodeNodeWithStoredBci(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    public static final class MyException extends AbstractTruffleException {
        private static final long serialVersionUID = 1L;
        public final Object result;
        public int bci = -1;

        MyException(Object result) {
            super();
            this.result = result;
        }
    }

    public static final class RootAndFrame {
        final BytecodeNodeWithStoredBci root;
        final Frame frame;

        RootAndFrame(BytecodeNodeWithStoredBci root, Frame frame) {
            this.root = root;
            this.frame = frame.materialize();
        }

        public String getSourceCharacters() {
            int bci = root.readBciFromFrame(frame);
            SourceSection section = root.getSourceSectionAtBci(bci);
            return section.getCharacters().toString();
        }
    }

    @Operation
    public static final class MakeRootAndFrame {
        @Specialization
        public static RootAndFrame perform(VirtualFrame frame, @Bind("$root") BytecodeNodeWithStoredBci rootNode) {
            return new RootAndFrame(rootNode, frame);
        }
    }

    @Operation
    public static final class GetSourceCharacters {
        @Specialization
        public static String perform(@SuppressWarnings("unused") VirtualFrame frame, RootAndFrame rootAndFrame) {
            return rootAndFrame.getSourceCharacters();
        }
    }

    @Operation
    public static final class Throw {
        @Specialization
        public static Object perform(Object result) {
            throw new MyException(result);
        }
    }
}
