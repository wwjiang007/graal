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
package com.oracle.truffle.dsl.processor.bytecode.generator;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.ChildExecutionResult;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.LocalVariable;
import com.oracle.truffle.dsl.processor.generator.NodeGeneratorPlugs;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.NodeChildData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;

public class BytecodeDSLNodeGeneratorPlugs implements NodeGeneratorPlugs {

    private final ProcessorContext context;
    private final TypeMirror nodeType;
    private final BytecodeDSLModel model;
    private final InstructionModel instr;

    public BytecodeDSLNodeGeneratorPlugs(ProcessorContext context, TypeMirror nodeType, BytecodeDSLModel model, InstructionModel instr) {
        this.context = context;
        this.nodeType = nodeType;
        this.model = model;
        this.instr = instr;
    }

    @Override
    public List<? extends VariableElement> additionalArguments() {
        List<CodeVariableElement> result = new ArrayList<>();
        if (model.enableYield) {
            result.add(new CodeVariableElement(context.getTypes().VirtualFrame, "$stackFrame"));
        }
        result.addAll(List.of(
                        new CodeVariableElement(nodeType, "$root"),
                        new CodeVariableElement(context.getType(short[].class), "$bc"),
                        new CodeVariableElement(context.getType(int.class), "$bci"),
                        new CodeVariableElement(context.getType(int.class), "$sp")));
        return result;
    }

    @Override
    public ChildExecutionResult createExecuteChild(FlatNodeGenFactory factory, CodeTreeBuilder builder, FrameState originalFrameState, FrameState frameState, NodeExecutionData execution,
                    LocalVariable targetValue) {

        CodeTreeBuilder b = builder.create();

        b.string(targetValue.getName(), " = ");

        int index = execution.getIndex();

        boolean throwsUnexpectedResult = buildChildExecution(b, stackFrame(), index);

        return new ChildExecutionResult(b.build(), throwsUnexpectedResult);
    }

    private boolean buildChildExecution(CodeTreeBuilder b, String frame, int idx) {
        int index = idx;

        if (index < instr.signature.valueCount) {
            TypeMirror targetType = instr.signature.getParameterType(index);
            if (!ElementUtils.isObject(targetType)) {
                b.cast(targetType);
            }
            b.string("ACCESS.uncheckedGetObject(" + frame + ", $sp - " + (instr.signature.valueCount - index) + ")");
            return false;
        }

        index -= instr.signature.valueCount;

        if (index < instr.signature.localSetterCount) {
            List<InstructionImmediate> imms = instr.getImmediates(ImmediateKind.LOCAL_SETTER);
            InstructionImmediate imm = imms.get(index);
            b.startStaticCall(context.getTypes().LocalSetter, "get");
            b.string("ACCESS.shortArrayRead($bc, $bci + " + imm.offset + ")");
            b.end();
            return false;
        }

        index -= instr.signature.localSetterCount;

        if (index < instr.signature.localSetterRangeCount) {
            List<InstructionImmediate> imms = instr.getImmediates(ImmediateKind.LOCAL_SETTER_RANGE_START);
            InstructionImmediate imm = imms.get(index);
            b.startStaticCall(context.getTypes().LocalSetterRange, "get");
            b.string("ACCESS.shortArrayRead($bc, $bci + " + imm.offset + ")"); // start
            b.string("ACCESS.shortArrayRead($bc, $bci + " + (imm.offset + 1) + ")"); // length
            b.end();
            return false;
        }

        throw new AssertionError("index=" + index + ", signature=" + instr.signature);
    }

    @Override
    public void createNodeChildReferenceForException(FlatNodeGenFactory flatNodeGenFactory, FrameState frameState, CodeTreeBuilder builder, List<CodeTree> values, NodeExecutionData execution,
                    NodeChildData child, LocalVariable var) {
        builder.string("null");
    }

    private String stackFrame() {
        return model.enableYield ? "$stackFrame" : TemplateMethod.FRAME_NAME;
    }
}
