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
package com.oracle.truffle.dsl.processor.bytecode.model;

public record ShortCircuitInstructionModel(Operator operator,
                InstructionModel booleanConverterInstruction) {

    /**
     * The processor cannot directly depend on the module containing
     * ShortCircuitOperation.Operation, so the definition is mirrored here.
     */
    enum Operator {
        AND_RETURN_VALUE(true, false),
        AND_RETURN_CONVERTED(true, true),
        OR_RETURN_VALUE(false, false),
        OR_RETURN_CONVERTED(false, true);

        boolean continueWhen;
        boolean returnConvertedBoolean;

        Operator(boolean continueWhen, boolean returnConvertedBoolean) {
            this.continueWhen = continueWhen;
            this.returnConvertedBoolean = returnConvertedBoolean;
        }

        static Operator parse(String value) {
            return switch (value) {
                case "AND_RETURN_VALUE" -> AND_RETURN_VALUE;
                case "AND_RETURN_CONVERTED" -> AND_RETURN_CONVERTED;
                case "OR_RETURN_VALUE" -> OR_RETURN_VALUE;
                case "OR_RETURN_CONVERTED" -> OR_RETURN_CONVERTED;
                default -> throw new AssertionError(value);
            };
        }
    }

    public boolean continueWhen() {
        return operator.continueWhen;
    }

    public boolean returnConvertedBoolean() {
        return operator.returnConvertedBoolean;
    }

    public static ShortCircuitInstructionModel parse(String operator, InstructionModel booleanConverterInstruction) {
        return new ShortCircuitInstructionModel(Operator.parse(operator), booleanConverterInstruction);
    }
}
