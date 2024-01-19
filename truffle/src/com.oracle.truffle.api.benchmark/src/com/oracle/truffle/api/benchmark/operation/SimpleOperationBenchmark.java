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
package com.oracle.truffle.api.benchmark.operation;

import static com.oracle.truffle.api.benchmark.operation.ManualBytecodeNode.OP_ADD;
import static com.oracle.truffle.api.benchmark.operation.ManualBytecodeNode.OP_CONST;
import static com.oracle.truffle.api.benchmark.operation.ManualBytecodeNode.OP_JUMP;
import static com.oracle.truffle.api.benchmark.operation.ManualBytecodeNode.OP_JUMP_FALSE;
import static com.oracle.truffle.api.benchmark.operation.ManualBytecodeNode.OP_LD_LOC;
import static com.oracle.truffle.api.benchmark.operation.ManualBytecodeNode.OP_LESS;
import static com.oracle.truffle.api.benchmark.operation.ManualBytecodeNode.OP_MOD;
import static com.oracle.truffle.api.benchmark.operation.ManualBytecodeNode.OP_RETURN;
import static com.oracle.truffle.api.benchmark.operation.ManualBytecodeNode.OP_ST_LOC;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.benchmark.TruffleBenchmark;
import com.oracle.truffle.api.benchmark.operation.ManualBytecodeNodedNode.AddNode;
import com.oracle.truffle.api.benchmark.operation.ManualBytecodeNodedNode.ModNode;

@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class SimpleOperationBenchmark extends TruffleBenchmark {

    private static final int TOTAL_ITERATIONS;
    static {
        String iters = System.getenv("TOTAL_ITERATIONS");
        TOTAL_ITERATIONS = (iters == null) ? 5000 : Integer.parseInt(iters);
    }

    private static final String NAME_OPERATION = "simple:operation-base";
    private static final String NAME_OPERATION_BASELINE = "simple:operation-baseline";
    private static final String NAME_OPERATION_UNSAFE = "simple:operation-unsafe";
    private static final String NAME_OPERATION_BE = "simple:operation-be";
    private static final String NAME_OPERATION_QUICKENED = "simple:operation-quickened";
    private static final String NAME_OPERATION_ALL = "simple:operation-all";
    private static final String NAME_MANUAL = "simple:manual";
    private static final String NAME_MANUAL_NO_BE = "simple:manual-no-be";
    private static final String NAME_MANUAL_UNSAFE = "simple:manual-unsafe";
    private static final String NAME_MANUAL_NODED = "simple:manual-noded";
    private static final String NAME_AST = "simple:ast";

    private static final Source SOURCE_OPERATION = Source.create("bm", NAME_OPERATION);
    private static final Source SOURCE_OPERATION_BASELINE = Source.create("bm", NAME_OPERATION_BASELINE);
    private static final Source SOURCE_OPERATION_UNSAFE = Source.create("bm", NAME_OPERATION_UNSAFE);
    private static final Source SOURCE_OPERATION_BE = Source.create("bm", NAME_OPERATION_BE);
    private static final Source SOURCE_OPERATION_QUICKENED = Source.create("bm", NAME_OPERATION_QUICKENED);
    private static final Source SOURCE_OPERATION_ALL = Source.create("bm", NAME_OPERATION_ALL);
    private static final Source SOURCE_MANUAL = Source.create("bm", NAME_MANUAL);
    private static final Source SOURCE_MANUAL_NO_BE = Source.create("bm", NAME_MANUAL_NO_BE);
    private static final Source SOURCE_MANUAL_UNSAFE = Source.create("bm", NAME_MANUAL_UNSAFE);
    private static final Source SOURCE_MANUAL_NODED = Source.create("bm", NAME_MANUAL_NODED);
    private static final Source SOURCE_AST = Source.create("bm", NAME_AST);

    private static final int LOC_I = 4;
    private static final int LOC_SUM = 5;
    private static final int LOC_J = 6;
    private static final int LOC_TEMP = 7;

    private static final short[] BYTECODE = {
                    // i = 0
                    /* 00 */ OP_CONST, 0, 0,
                    /* 03 */ OP_ST_LOC, LOC_I,

                    // sum = 0
                    /* 05 */ OP_CONST, 0, 0,
                    /* 08 */ OP_ST_LOC, LOC_SUM,

                    // while (i < 5000) {
                    /* while_0_start: */
                    /* 10 */ OP_LD_LOC, LOC_I,
                    /* 12 */ OP_CONST, 0, (short) TOTAL_ITERATIONS,
                    /* 15 */ OP_LESS,
                    /* 16 */ OP_JUMP_FALSE, 83, // while_0_end

                    // j = 0
                    /* 18 */ OP_CONST, 0, 0,
                    /* 21 */ OP_ST_LOC, LOC_J,

                    // while (j < i) {
                    /* while_1_start: */
                    /* 23 */ OP_LD_LOC, LOC_J, // j
                    /* 25 */ OP_LD_LOC, LOC_I, // i
                    /* 27 */ OP_LESS,
                    /* 28 */ OP_JUMP_FALSE, 66, // while_1_end

                    // if (i % 3 < 1) {
                    /* 30 */ OP_LD_LOC, LOC_I, // i
                    /* 32 */ OP_CONST, 0, 3,
                    /* 35 */ OP_MOD,
                    /* 36 */ OP_CONST, 0, 1,
                    /* 39 */ OP_LESS,
                    /* 40 */ OP_JUMP_FALSE, 49, // if_else

                    // temp = 1
                    /* 42 */ OP_CONST, 0, 1,
                    /* 45 */ OP_ST_LOC, LOC_TEMP, // temp

                    // } else {
                    /* 47 */ OP_JUMP, 57, // if_end
                    /* if_else: */

                    // temp = i % 3
                    /* 49 */ OP_LD_LOC, LOC_I, // i
                    /* 51 */ OP_CONST, 0, 3,
                    /* 54 */ OP_MOD,
                    /* 55 */ OP_ST_LOC, LOC_TEMP, // temp

                    // } // if end
                    /* if_end: */

                    // j = j + temp
                    /* 57 */ OP_LD_LOC, LOC_J, // j
                    /* 59 */ OP_LD_LOC, LOC_TEMP, // temp
                    /* 61 */ OP_ADD,
                    /* 62 */ OP_ST_LOC, LOC_J, // j

                    // } // while end
                    /* 64 */ OP_JUMP, 23, // while_1_start
                    /* while_1_end: */

                    // sum = sum + j
                    /* 66 */ OP_LD_LOC, LOC_SUM, // sum
                    /* 68 */ OP_LD_LOC, LOC_J, // j
                    /* 70 */ OP_ADD,
                    /* 71 */ OP_ST_LOC, LOC_SUM, // sum

                    // i = i + 1
                    /* 73 */ OP_LD_LOC, LOC_I, // i
                    /* 75 */ OP_CONST, 0, 1,
                    /* 78 */ OP_ADD,
                    /* 79 */ OP_ST_LOC, LOC_I, // i

                    // } // while end
                    /* 81 */ OP_JUMP, 10, // while_0_start
                    /* while_0_end: */

                    // return sum
                    /* 83 */ OP_LD_LOC, LOC_SUM, // sum
                    /* 85 */ OP_RETURN,

    };

    private static final short[] BC_SHORT = {
                    // i = 0
                    /* 00 */ OP_CONST, 0, // 0
                    /* 02 */ OP_ST_LOC, LOC_I,

                    // sum = 0
                    /* 04 */ OP_CONST, 0, // 0
                    /* 06 */ OP_ST_LOC, LOC_SUM,

                    // while (i < 5000) {
                    /* while_0_start: */
                    /* 08 */ OP_LD_LOC, LOC_I,
                    /* 10 */ OP_CONST, 9, // TOTAL_ITERATIONS (5000)
                    /* 12 */ OP_LESS,
                    /* 13 */ OP_JUMP_FALSE, 79, // while_0_end

                    // j = 0
                    /* 15 */ OP_CONST, 0, // 0
                    /* 17 */ OP_ST_LOC, LOC_J,

                    // while (j < i) {
                    /* while_1_start: */
                    /* 19 */ OP_LD_LOC, LOC_J,
                    /* 21 */ OP_LD_LOC, LOC_I,
                    /* 23 */ OP_LESS,
                    /* 24 */ OP_JUMP_FALSE, 61, // while_1_end

                    // if (i % 3 < 1) {
                    /* 26 */ OP_LD_LOC, LOC_I,
                    /* 28 */ OP_CONST, 2, // 3
                    /* 30 */ OP_MOD, 0,
                    /* 32 */ OP_CONST, 1, // 1
                    /* 34 */ OP_LESS,
                    /* 35 */ OP_JUMP_FALSE, 43, // if_else

                    // temp = 1
                    /* 37 */ OP_CONST, 1, // 1
                    /* 39 */ OP_ST_LOC, LOC_TEMP,
                    /* 41 */ OP_JUMP, 51, // if_end
                    // } else {

                    /* if_else: */
                    // temp = i % 3
                    /* 43 */ OP_LD_LOC, LOC_I,
                    /* 45 */ OP_CONST, 2, // 3
                    /* 47 */ OP_MOD, 1,
                    /* 49 */ OP_ST_LOC, LOC_TEMP,

                    // } // if end
                    /* if_end: */

                    // j = j + temp
                    /* 51 */ OP_LD_LOC, LOC_J,
                    /* 53 */ OP_LD_LOC, LOC_TEMP,
                    /* 55 */ OP_ADD, 2,
                    /* 57 */ OP_ST_LOC, LOC_J,
                    /* 59 */ OP_JUMP, 19, // while_1_start
                    // } // while end
                    /* while_1_end: */

                    // sum = sum + j
                    /* 61 */ OP_LD_LOC, LOC_SUM,
                    /* 63 */ OP_LD_LOC, LOC_J,
                    /* 65 */ OP_ADD, 3,
                    /* 67 */ OP_ST_LOC, LOC_SUM,

                    // i = i + 1
                    /* 69 */ OP_LD_LOC, LOC_I,
                    /* 71 */ OP_CONST, 1, // 1
                    /* 73 */ OP_ADD, 4,
                    /* 75 */ OP_ST_LOC, LOC_I,
                    /* 77 */ OP_JUMP, 8, // while_0_start
                    // } // while end
                    /* while_0_end: */

                    // return sum
                    /* 79 */ OP_LD_LOC, LOC_SUM,
                    /* 81 */ OP_RETURN,
    };

    private static final Object[] OBJ_SHORT = {
                    0,
                    1,
                    3,
                    10,
                    23,
                    27,
                    32,
                    40,
                    41,
                    TOTAL_ITERATIONS,
                    null
    };

    private static final Node[] NODE_SHORT = {
                    ModNode.create(), // node for bci 30
                    ModNode.create(), // node for bci 47
                    AddNode.create(), // node for bci 55
                    AddNode.create(), // node for bci 65
                    AddNode.create(), // node for bci 73
    };

    private Context context;

    /**
     * The code is equivalent to:
     *
     * <pre>
     * int i = 0;
     * int sum = 0;
     * while (i < 5000) {
     *     int j = 0;
     *     while (j < i) {
     *         int temp;
     *         if (i % 3 < 1) {
     *             temp = 1;
     *         } else {
     *             temp = i % 3;
     *         }
     *         j = j + temp;
     *     }
     *     sum = sum + j;
     *     i = i + 1;
     * }
     * return sum;
     * </pre>
     *
     * The result should be 12498333.
     */
    static {
        if (BYTECODE.length != 86) {
            throw new AssertionError("bad bytecode length: " + BYTECODE.length);
        }

        BenchmarkLanguage.registerName(NAME_OPERATION, BMOperationRootNodeBase.class, (lang, b) -> {
            createSimpleLoop(lang, b);
        });
        BenchmarkLanguage.registerName(NAME_OPERATION_BASELINE, BMOperationRootNodeWithBaseline.class, (lang, b) -> {
            createSimpleLoop(lang, b);
        });
        BenchmarkLanguage.registerName(NAME_OPERATION_UNSAFE, BMOperationRootNodeUnsafe.class, (lang, b) -> {
            createSimpleLoop(lang, b);
        });
        BenchmarkLanguage.registerName(NAME_OPERATION_BE, BMOperationRootNodeBoxingEliminated.class, (lang, b) -> {
            createSimpleLoop(lang, b);
        });
        BenchmarkLanguage.registerName(NAME_OPERATION_QUICKENED, BMOperationRootNodeQuickened.class, (lang, b) -> {
            createSimpleLoop(lang, b);
        });
        BenchmarkLanguage.registerName(NAME_OPERATION_ALL, BMOperationRootNodeAll.class, (lang, b) -> {
            createSimpleLoop(lang, b);
        });
        BenchmarkLanguage.registerName(NAME_MANUAL, lang -> {
            FrameDescriptor.Builder b = FrameDescriptor.newBuilder(3);
            b.addSlots(8, FrameSlotKind.Illegal);
            ManualBytecodeNode node = new ManualBytecodeNode(lang, b.build(), BYTECODE);
            return node.getCallTarget();
        });
        BenchmarkLanguage.registerName(NAME_MANUAL_NO_BE, lang -> {
            FrameDescriptor.Builder b = FrameDescriptor.newBuilder(3);
            b.addSlots(8, FrameSlotKind.Illegal);
            ManualBytecodeNodeNBE node = new ManualBytecodeNodeNBE(lang, b.build(), BYTECODE);
            return node.getCallTarget();
        });
        BenchmarkLanguage.registerName(NAME_MANUAL_UNSAFE, lang -> {
            FrameDescriptor.Builder b = FrameDescriptor.newBuilder(3);
            b.addSlots(8, FrameSlotKind.Illegal);
            ManualUnsafeBytecodeNode node = new ManualUnsafeBytecodeNode(lang, b.build(), BYTECODE);
            return node.getCallTarget();
        });
        BenchmarkLanguage.registerName(NAME_MANUAL_NODED, lang -> {
            FrameDescriptor.Builder b = FrameDescriptor.newBuilder(3);
            b.addSlots(8, FrameSlotKind.Illegal);
            ManualBytecodeNodedNode node = new ManualBytecodeNodedNode(lang, b.build(), BC_SHORT, OBJ_SHORT, NODE_SHORT);
            return node.getCallTarget();
        });
        BenchmarkLanguage.registerName(NAME_AST, lang -> {
            int iLoc = 0;
            int sumLoc = 1;
            int jLoc = 2;
            int tempLoc = 3;
            return new BMLRootNode(lang, 4, BlockNode.create(
                            // i = 0
                            StoreLocalNodeGen.create(iLoc, ConstNodeGen.create(0)),
                            // sum = 0
                            StoreLocalNodeGen.create(sumLoc, ConstNodeGen.create(0)),
                            // while (i < 5000) {
                            WhileNode.create(LessNodeGen.create(LoadLocalNodeGen.create(iLoc), ConstNodeGen.create(TOTAL_ITERATIONS)), BlockNode.create(
                                            // j = 0
                                            StoreLocalNodeGen.create(jLoc, ConstNodeGen.create(0)),
                                            // while (j < i) {
                                            WhileNode.create(LessNodeGen.create(LoadLocalNodeGen.create(jLoc), LoadLocalNodeGen.create(iLoc)), BlockNode.create(
                                                            // if (i % 3 < 1) {
                                                            IfNode.create(LessNodeGen.create(ModNodeGen.create(LoadLocalNodeGen.create(iLoc), ConstNodeGen.create(3)), ConstNodeGen.create(1)),
                                                                            // temp = 1
                                                                            StoreLocalNodeGen.create(tempLoc, ConstNodeGen.create(1)),
                                                                            // } else {
                                                                            // temp = i % 3
                                                                            StoreLocalNodeGen.create(tempLoc, ModNodeGen.create(LoadLocalNodeGen.create(iLoc), ConstNodeGen.create(3)))),
                                                            // }
                                                            // j = j + temp
                                                            StoreLocalNodeGen.create(jLoc, AddNodeGen.create(LoadLocalNodeGen.create(jLoc), LoadLocalNodeGen.create(tempLoc))))),
                                            // }
                                            // sum = sum + j
                                            StoreLocalNodeGen.create(sumLoc, AddNodeGen.create(LoadLocalNodeGen.create(sumLoc), LoadLocalNodeGen.create(jLoc))),
                                            // i = i + 1
                                            StoreLocalNodeGen.create(iLoc, AddNodeGen.create(LoadLocalNodeGen.create(iLoc), ConstNodeGen.create(1))))),
                            // return sum
                            ReturnNodeGen.create(LoadLocalNodeGen.create(sumLoc)))).getCallTarget();
        });
    }

    private static void createSimpleLoop(BenchmarkLanguage lang, BMOperationRootNodeBuilder b) {
        b.beginRoot(lang);

        OperationLocal iLoc = b.createLocal();
        OperationLocal sumLoc = b.createLocal();
        OperationLocal jLoc = b.createLocal();
        OperationLocal tempLoc = b.createLocal();

        // int i = 0;
        b.beginStoreLocal(iLoc);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        // int sum = 0;
        b.beginStoreLocal(sumLoc);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        // while (i < TOTAL_ITERATIONS) {
        b.beginWhile();
        b.beginLess();
        b.emitLoadLocal(iLoc);
        b.emitLoadConstant(TOTAL_ITERATIONS);
        b.endLess();
        b.beginBlock();

        // int j = 0;
        b.beginStoreLocal(jLoc);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        // while (j < i) {
        b.beginWhile();
        b.beginLess();
        b.emitLoadLocal(jLoc);
        b.emitLoadLocal(iLoc);
        b.endLess();
        b.beginBlock();

        // int temp;
        // if (i % 3 < 1) {
        b.beginIfThenElse();

        b.beginLess();
        b.beginMod();
        b.emitLoadLocal(iLoc);
        b.emitLoadConstant(3);
        b.endMod();
        b.emitLoadConstant(1);
        b.endLess();

        // temp = 1;
        b.beginStoreLocal(tempLoc);
        b.emitLoadConstant(1);
        b.endStoreLocal();

        // } else {
        // temp = i % 3;
        b.beginStoreLocal(tempLoc);
        b.beginMod();
        b.emitLoadLocal(iLoc);
        b.emitLoadConstant(3);
        b.endMod();
        b.endStoreLocal();

        // }
        b.endIfThenElse();

        // j = j + temp;
        b.beginStoreLocal(jLoc);
        b.beginAdd();
        b.emitLoadLocal(jLoc);
        b.emitLoadLocal(tempLoc);
        b.endAdd();
        b.endStoreLocal();

        // }
        b.endBlock();
        b.endWhile();

        // sum = sum + j;
        b.beginStoreLocal(sumLoc);
        b.beginAdd();
        b.emitLoadLocal(sumLoc);
        b.emitLoadLocal(jLoc);
        b.endAdd();
        b.endStoreLocal();

        // i = i + 1;
        b.beginStoreLocal(iLoc);
        b.beginAdd();
        b.emitLoadLocal(iLoc);
        b.emitLoadConstant(1);
        b.endAdd();
        b.endStoreLocal();

        // }
        b.endBlock();
        b.endWhile();

        // return sum;
        b.beginReturn();
        b.emitLoadLocal(sumLoc);
        b.endReturn();

        b.endRoot();
    }

    @Setup(Level.Trial)
    public void setup() {
        context = Context.newBuilder("bm").allowExperimentalOptions(true).build();
    }

    @Setup(Level.Iteration)
    public void enterContext() {
        context.enter();
    }

    @TearDown(Level.Iteration)
    public void leaveContext() {
        context.leave();
    }

    private static final boolean PRINT_RESULTS = System.getProperty("PrintResults") != null;

    private void doEval(Source source) {
        Value v = context.eval(source);
        if (PRINT_RESULTS) {
            System.err.println(source.getCharacters() + " = " + v);
        }
    }

    @Benchmark
    public void operation() {
        doEval(SOURCE_OPERATION);
    }

    @Benchmark
    public void operationWithBaseline() {
        doEval(SOURCE_OPERATION_BASELINE);
    }

    @Benchmark
    public void operationUnsafe() {
        doEval(SOURCE_OPERATION_UNSAFE);
    }

    @Benchmark
    public void operationBE() {
        doEval(SOURCE_OPERATION_BE);
    }

    @Benchmark
    public void operationQuicken() {
        doEval(SOURCE_OPERATION_QUICKENED);
    }

    @Benchmark
    public void operationAll() {
        doEval(SOURCE_OPERATION_ALL);
    }

    @Benchmark
    public void manual() {
        doEval(SOURCE_MANUAL);
    }

    @Benchmark
    public void manualNoBE() {
        doEval(SOURCE_MANUAL_NO_BE);
    }

    @Benchmark
    public void manualUnsafe() {
        doEval(SOURCE_MANUAL_UNSAFE);
    }

    @Benchmark
    public void manualNoded() {
        doEval(SOURCE_MANUAL_NODED);
    }

    @Benchmark
    public void ast() {
        doEval(SOURCE_AST);
    }
}
