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
package com.oracle.truffle.api.bytecode.test.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNodes;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@RunWith(Parameterized.class)
public class BytecodeDSLExampleFindBciTest extends AbstractBytecodeDSLExampleTest {
    public void assumeTestIsApplicable() {
        /*
         * TODO: BytecodeRootNode#findBci does not check for ContinuationRootNodes in the uncached
         * case. Since these nodes do not have a parent interface, we cannot distinguish them in the
         * static method. We can wait until GR-49484 lands to fix this, since we'll need to generate
         * a findBytecodeIndex method on the generated node anyway (and it can reference a specific
         * ContinuationRootNode).
         */
        assumeTrue(interpreterClass != BytecodeDSLExampleWithUncached.class && interpreterClass != BytecodeDSLExampleProduction.class);
    }

    @Test
    public void testStacktrace() {
        assumeTestIsApplicable();
        /**
         * @formatter:off
         * def baz(arg0) {
         *   if (arg0) <trace1> else <trace2>  // directly returns a trace
         * }
         *
         * def bar(arg0) {
         *   baz(arg0)
         * }
         *
         * def foo(arg0) {
         *   c = bar(arg0);
         *   c
         * }
         * @formatter:on
         */

        Source bazSource = Source.newBuilder("test", "if (arg0) <trace1> else <trace2>", "baz").build();
        Source barSource = Source.newBuilder("test", "baz(arg0)", "bar").build();
        Source fooSource = Source.newBuilder("test", "c = bar(arg0); c", "foo").build();
        BytecodeNodes<BytecodeDSLExample> nodes = createNodes(BytecodeConfig.WITH_SOURCE, b -> {
            // @formatter:off
            // collectBcis
            b.beginRoot(LANGUAGE);
                b.beginReturn();
                b.emitCollectBcis();
                b.endReturn();
            BytecodeDSLExample collectBcis = b.endRoot();
            collectBcis.setName("collectBcis");

            // baz
            b.beginRoot(LANGUAGE);
                b.beginSource(bazSource);
                b.beginBlock();

                b.beginIfThenElse();

                b.emitLoadArgument(0);

                b.beginReturn();
                b.beginSourceSection(10, 8);
                b.beginInvoke();
                b.emitLoadConstant(collectBcis);
                b.endInvoke();
                b.endSourceSection();
                b.endReturn();

                b.beginReturn();
                b.beginSourceSection(24, 8);
                b.beginInvoke();
                b.emitLoadConstant(collectBcis);
                b.endInvoke();
                b.endSourceSection();
                b.endReturn();

                b.endIfThenElse();

                b.endBlock();
                b.endSource();
            BytecodeDSLExample baz = b.endRoot();
            baz.setName("baz");

            // bar
            b.beginRoot(LANGUAGE);
                b.beginSource(barSource);

                b.beginReturn();

                b.beginSourceSection(0, 9);
                b.beginInvoke();
                b.emitLoadConstant(baz);
                b.emitLoadArgument(0);
                b.endInvoke();
                b.endSourceSection();

                b.endReturn();

                b.endSource();
            BytecodeDSLExample bar = b.endRoot();
            bar.setName("bar");

            // foo
            b.beginRoot(LANGUAGE);
                b.beginSource(fooSource);
                b.beginBlock();
                BytecodeLocal c = b.createLocal();

                b.beginSourceSection(0, 13);
                b.beginStoreLocal(c);
                b.beginSourceSection(4, 9);
                b.beginInvoke();
                b.emitLoadConstant(bar);
                b.emitLoadArgument(0);
                b.endInvoke();
                b.endSourceSection();
                b.endStoreLocal();
                b.endSourceSection();

                b.beginReturn();
                b.beginSourceSection(15, 1);
                b.emitLoadLocal(c);
                b.endSourceSection();
                b.endReturn();

                b.endBlock();
                b.endSource();
            BytecodeDSLExample foo = b.endRoot();
            foo.setName("foo");
        });

        List<BytecodeDSLExample> nodeList = nodes.getNodes();
        assert nodeList.size() == 4;
        BytecodeDSLExample foo = nodeList.get(3);
        assert foo.getName().equals("foo");
        BytecodeDSLExample bar = nodeList.get(2);
        assert bar.getName().equals("bar");
        BytecodeDSLExample baz = nodeList.get(1);
        assert baz.getName().equals("baz");

        for (boolean fooArgument : List.of(true, false)) {
            Object result = foo.getCallTarget().call(fooArgument);
            assertTrue(result instanceof List<?>);

            @SuppressWarnings("unchecked")
            List<Integer> bytecodeIndices = (List<Integer>) result;

            assertEquals(4, bytecodeIndices.size());

            // skip the helper

            // baz
            int bazBci = bytecodeIndices.get(1);
            assertNotEquals(-1, bazBci);
            SourceSection bazSourceSection = baz.findSourceSectionAtBci(bazBci);
            assertEquals(bazSource, bazSourceSection.getSource());
            if (fooArgument) {
                assertEquals("<trace1>", bazSourceSection.getCharacters());
            } else {
                assertEquals("<trace2>", bazSourceSection.getCharacters());
            }

            // bar
            int barBci = bytecodeIndices.get(2);
            assertNotEquals(-1, barBci);
            SourceSection barSourceSection = bar.findSourceSectionAtBci(barBci);
            assertEquals(barSource, barSourceSection.getSource());
            assertEquals("baz(arg0)", barSourceSection.getCharacters());

            // foo
            int fooBci = bytecodeIndices.get(3);
            assertNotEquals(-1, fooBci);
            SourceSection fooSourceSection = foo.findSourceSectionAtBci(fooBci);
            assertEquals(fooSource, fooSourceSection.getSource());
            assertEquals("bar(arg0)", fooSourceSection.getCharacters());
        }
    }

    @Test
    public void testStacktraceWithContinuation() {
        assumeTestIsApplicable();
        /**
         * @formatter:off
         * def baz(arg0) {
         *   if (arg0) <trace1> else <trace2>  // directly returns a trace
         * }
         *
         * def bar() {
         *   x = yield 1;
         *   baz(x)
         * }
         *
         * def foo(arg0) {
         *   c = bar();
         *   continue(c, arg0)
         * }
         * @formatter:on
         */
        Source bazSource = Source.newBuilder("test", "if (arg0) <trace1> else <trace2>", "baz").build();
        Source barSource = Source.newBuilder("test", "x = yield 1; baz(x)", "bar").build();
        Source fooSource = Source.newBuilder("test", "c = bar(); continue(c, arg0)", "foo").build();
        BytecodeNodes<BytecodeDSLExample> nodes = createNodes(BytecodeConfig.WITH_SOURCE, b -> {
            // @formatter:off
            // collectBcis
            b.beginRoot(LANGUAGE);
                b.beginReturn();
                b.emitCollectBcis();
                b.endReturn();
            BytecodeDSLExample collectBcis = b.endRoot();
            collectBcis.setName("collectBcis");

            // baz
            b.beginRoot(LANGUAGE);
                b.beginSource(bazSource);
                b.beginBlock();

                b.beginIfThenElse();

                b.emitLoadArgument(0);

                b.beginReturn();
                b.beginSourceSection(10, 8);
                b.beginInvoke();
                b.emitLoadConstant(collectBcis);
                b.endInvoke();
                b.endSourceSection();
                b.endReturn();

                b.beginReturn();
                b.beginSourceSection(24, 8);
                b.beginInvoke();
                b.emitLoadConstant(collectBcis);
                b.endInvoke();
                b.endSourceSection();
                b.endReturn();

                b.endIfThenElse();

                b.endBlock();
                b.endSource();
            BytecodeDSLExample baz = b.endRoot();
            baz.setName("baz");

            // bar
            b.beginRoot(LANGUAGE);
                b.beginSource(barSource);
                b.beginBlock();
                BytecodeLocal x = b.createLocal();

                b.beginStoreLocal(x);
                b.beginYield();
                b.emitLoadConstant(1L);
                b.endYield();
                b.endStoreLocal();

                b.beginReturn();
                b.beginSourceSection(13, 6);
                b.beginInvoke();
                b.emitLoadConstant(baz);
                b.emitLoadLocal(x);
                b.endInvoke();
                b.endSourceSection();
                b.endReturn();

                b.endBlock();
                b.endSource();
            BytecodeDSLExample bar = b.endRoot();
            bar.setName("bar");

            // foo
            b.beginRoot(LANGUAGE);
                b.beginSource(fooSource);
                b.beginBlock();

                BytecodeLocal c = b.createLocal();

                b.beginStoreLocal(c);
                b.beginInvoke();
                b.emitLoadConstant(bar);
                b.endInvoke();
                b.endStoreLocal();

                b.beginReturn();
                b.beginSourceSection(11, 17);
                b.beginContinue();
                b.emitLoadLocal(c);
                b.emitLoadArgument(0);
                b.endContinue();
                b.endSourceSection();
                b.endReturn();

                b.endBlock();
                b.endSource();
            BytecodeDSLExample foo = b.endRoot();
            foo.setName("foo");
            // @formatter:off
        });

        List<BytecodeDSLExample> nodeList = nodes.getNodes();
        assert nodeList.size() == 4;
        BytecodeDSLExample foo = nodeList.get(3);
        assert foo.getName().equals("foo");
        BytecodeDSLExample bar = nodeList.get(2);
        assert bar.getName().equals("bar");
        BytecodeDSLExample baz = nodeList.get(1);
        assert baz.getName().equals("baz");

        for (boolean continuationArgument : List.of(true, false)) {
            Object result = foo.getCallTarget().call(continuationArgument);
            assertTrue(result instanceof List<?>);

            @SuppressWarnings("unchecked")
            List<Integer> bytecodeIndices = (List<Integer>) result;
            assertEquals(4, bytecodeIndices.size());

            // skip the helper

            // baz
            int bazBci = bytecodeIndices.get(1);
            assertNotEquals(-1, bazBci);
            SourceSection bazSourceSection = baz.findSourceSectionAtBci(bazBci);
            assertEquals(bazSource, bazSourceSection.getSource());
            if (continuationArgument) {
                assertEquals("<trace1>", bazSourceSection.getCharacters());
            } else {
                assertEquals("<trace2>", bazSourceSection.getCharacters());
            }

            // bar
            int barBci = bytecodeIndices.get(2);
            assertNotEquals(-1, barBci);
            SourceSection barSourceSection = bar.findSourceSectionAtBci(barBci);
            assertEquals(barSource, barSourceSection.getSource());
            assertEquals("baz(x)", barSourceSection.getCharacters());

            // foo
            int fooBci = bytecodeIndices.get(3);
            assertNotEquals(-1, fooBci);
            SourceSection fooSourceSection = foo.findSourceSectionAtBci(fooBci);
            assertEquals(fooSource, fooSourceSection.getSource());
            assertEquals("continue(c, arg0)", fooSourceSection.getCharacters());
        }
    }
}
