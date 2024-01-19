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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

@RunWith(Parameterized.class)
public abstract class AbstractBytecodeDSLExampleTest {
    protected static final BytecodeDSLExampleLanguage LANGUAGE = null;

    public static final BytecodeSerializer SERIALIZER = new BytecodeSerializer() {
        public void serialize(SerializerContext context, DataOutput buffer, Object object) throws IOException {
            if (object == null) {
                buffer.writeByte(0);
            } else if (object instanceof Long num) {
                buffer.writeByte(1);
                buffer.writeLong(num);
            } else if (object instanceof String str) {
                buffer.writeByte(2);
                buffer.writeUTF(str);
            } else if (object instanceof Boolean bool) {
                buffer.writeByte(3);
                buffer.writeBoolean(bool);
            } else if (object.getClass().isArray()) {
                buffer.writeByte(4);
                if (object instanceof long[] longs) {
                    buffer.writeByte(1);
                    buffer.writeInt(longs.length);
                    for (long num : longs) {
                        serialize(context, buffer, num);
                    }
                } else {
                    throw new AssertionError("Serializer does not handle array of type " + object.getClass());
                }
            } else if (object instanceof BytecodeDSLExample rootNode) {
                buffer.writeByte(5);
                context.writeBytecodeNode(buffer, rootNode);
            } else if (object instanceof Source source) {
                buffer.writeByte(6);
                buffer.writeUTF(source.getLanguage());
                buffer.writeUTF(source.getName());
                buffer.writeUTF(source.getCharacters().toString());
            } else {
                throw new AssertionError("Serializer does not handle object " + object);
            }
        }
    };

    public static final BytecodeDeserializer DESERIALIZER = new BytecodeDeserializer() {
        public Object deserialize(DeserializerContext context, DataInput buffer) throws IOException {
            byte objectCode = buffer.readByte();
            return switch (objectCode) {
                case 0 -> null;
                case 1 -> buffer.readLong();
                case 2 -> buffer.readUTF();
                case 3 -> buffer.readBoolean();
                case 4 -> {
                    byte arrayCode = buffer.readByte();
                    yield switch (arrayCode) {
                        case 1 -> {
                            int length = buffer.readInt();
                            long[] result = new long[length];
                            for (int i = 0; i < length; i++) {
                                result[i] = (long) deserialize(context, buffer);
                            }
                            yield result;
                        }
                        default -> throw new AssertionError("Deserializer does not handle array code " + arrayCode);
                    };
                }
                case 5 -> context.readBytecodeNode(buffer);
                case 6 -> {
                    String language = buffer.readUTF();
                    String name = buffer.readUTF();
                    String characters = buffer.readUTF();
                    yield Source.newBuilder(language, characters, name).build();
                }
                default -> throw new AssertionError("Deserializer does not handle code " + objectCode);
            };
        }
    };

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Parameters(name = "{0}_{1}")
    public static List<Object[]> getParameters() {
        List<Object[]> result = new ArrayList<>();
        for (Class<?> interpreterClass : allInterpreters()) {
            result.add(new Object[]{interpreterClass, false});
            result.add(new Object[]{interpreterClass, true});
        }
        return result;
    }

    @Parameter(0) public Class<? extends BytecodeDSLExample> interpreterClass;
    @Parameter(1) public Boolean testSerialize;

    public <T extends BytecodeDSLExampleBuilder> RootCallTarget parse(String rootName, BytecodeParser<T> builder) {
        BytecodeRootNode rootNode = parseNode(interpreterClass, testSerialize, rootName, builder);
        return ((RootNode) rootNode).getCallTarget();
    }

    public <T extends BytecodeDSLExampleBuilder> BytecodeDSLExample parseNode(String rootName, BytecodeParser<T> builder) {
        return parseNode(interpreterClass, testSerialize, rootName, builder);
    }

    public <T extends BytecodeDSLExampleBuilder> BytecodeDSLExample parseNodeWithSource(String rootName, BytecodeParser<T> builder) {
        return parseNodeWithSource(interpreterClass, testSerialize, rootName, builder);
    }

    public <T extends BytecodeDSLExampleBuilder> BytecodeNodes<BytecodeDSLExample> createNodes(BytecodeConfig config, BytecodeParser<T> builder) {
        return createNodes(interpreterClass, testSerialize, config, builder);
    }

    /**
     * Creates a root node using the given parameters.
     *
     * In order to parameterize tests over multiple different interpreter configurations
     * ("variants"), we take the specific interpreterClass as input. Since interpreters are
     * instantiated using a static {@code create} method, we must invoke this method using
     * reflection.
     */
    @SuppressWarnings("unchecked")
    public static <T extends BytecodeDSLExampleBuilder> BytecodeNodes<BytecodeDSLExample> createNodes(Class<? extends BytecodeDSLExample> interpreterClass, boolean testSerialize,
                    BytecodeConfig config,
                    BytecodeParser<T> builder) {

        BytecodeNodes<BytecodeDSLExample> result = invokeCreate(interpreterClass, config, builder);
        if (testSerialize) {
            // Perform a serialize-deserialize round trip.
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            invokeSerialize(interpreterClass, config, new DataOutputStream(output), SERIALIZER, builder);
            Supplier<DataInput> input = () -> SerializationUtils.createDataInput(ByteBuffer.wrap(output.toByteArray()));
            BytecodeNodes<BytecodeDSLExample> deserialized = invokeDeserialize(interpreterClass, LANGUAGE, config, input, DESERIALIZER);

            assertBytecodeNodesEqual(result, deserialized);
        }
        return result;
    }

    public static <T extends BytecodeDSLExampleBuilder> RootCallTarget parse(Class<? extends BytecodeDSLExample> interpreterClass, boolean testSerialize, String rootName, BytecodeParser<T> builder) {
        BytecodeRootNode rootNode = parseNode(interpreterClass, testSerialize, rootName, builder);
        return ((RootNode) rootNode).getCallTarget();
    }

    public static <T extends BytecodeDSLExampleBuilder> BytecodeDSLExample parseNode(Class<? extends BytecodeDSLExample> interpreterClass, boolean testSerialize, String rootName,
                    BytecodeParser<T> builder) {
        BytecodeNodes<BytecodeDSLExample> nodes = createNodes(interpreterClass, testSerialize, BytecodeConfig.DEFAULT, builder);
        BytecodeDSLExample op = nodes.getNode(nodes.count() - 1);
        op.setName(rootName);
        return op;
    }

    public static <T extends BytecodeDSLExampleBuilder> BytecodeDSLExample parseNodeWithSource(Class<? extends BytecodeDSLExample> interpreterClass, boolean testSerialize, String rootName,
                    BytecodeParser<T> builder) {
        BytecodeNodes<BytecodeDSLExample> nodes = createNodes(interpreterClass, testSerialize, BytecodeConfig.WITH_SOURCE, builder);
        BytecodeDSLExample op = nodes.getNode(nodes.count() - 1);
        op.setName(rootName);
        return op;
    }

    @SuppressWarnings("unchecked")
    public static <T extends BytecodeDSLExample> BytecodeNodes<T> invokeCreate(Class<? extends BytecodeDSLExample> interpreterClass, BytecodeConfig config,
                    BytecodeParser<? extends BytecodeDSLExampleBuilder> builder) {
        try {
            Method create = interpreterClass.getMethod("create", BytecodeConfig.class, BytecodeParser.class);
            return (BytecodeNodes<T>) create.invoke(null, config, builder);
        } catch (InvocationTargetException e) {
            // Exceptions thrown by the invoked method can be rethrown as runtime exceptions that
            // get caught by the test harness.
            throw new IllegalStateException(e.getCause());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends BytecodeDSLExample> BytecodeNodes<T> invokeDeserialize(Class<? extends BytecodeDSLExample> interpreterClass, TruffleLanguage<?> language, BytecodeConfig config,
                    Supplier<DataInput> input, BytecodeDeserializer callback) {
        try {
            Method deserialize = interpreterClass.getMethod("deserialize", TruffleLanguage.class, BytecodeConfig.class, Supplier.class, BytecodeDeserializer.class);
            return (BytecodeNodes<T>) deserialize.invoke(null, language, config, input, callback);
        } catch (InvocationTargetException e) {
            // Exceptions thrown by the invoked method can be rethrown as runtime exceptions that
            // get caught by the test harness.
            throw new IllegalStateException(e.getCause());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends BytecodeDSLExampleBuilder> void invokeSerialize(Class<? extends BytecodeDSLExample> interpreterClass, BytecodeConfig config, DataOutput buffer,
                    BytecodeSerializer callback,
                    BytecodeParser<T> parser) {
        try {
            Method serialize = interpreterClass.getMethod("serialize", BytecodeConfig.class, DataOutput.class, BytecodeSerializer.class, BytecodeParser.class);
            serialize.invoke(null, config, buffer, callback, parser);
        } catch (InvocationTargetException e) {
            // Exceptions thrown by the invoked method can be rethrown as runtime exceptions that
            // get caught by the test harness.
            throw new IllegalStateException(e.getCause());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void assertBytecodeNodesEqual(BytecodeNodes<BytecodeDSLExample> expectedBytecodeNodes, BytecodeNodes<BytecodeDSLExample> actualBytecodeNodes) {
        List<BytecodeDSLExample> expectedNodes = expectedBytecodeNodes.getNodes();
        List<BytecodeDSLExample> actualNodes = actualBytecodeNodes.getNodes();
        assertEquals(expectedNodes.size(), actualNodes.size());
        for (int i = 0; i < expectedNodes.size(); i++) {
            BytecodeDSLExample expectedNode = expectedNodes.get(i);
            BytecodeDSLExample actualNode = actualNodes.get(i);

            assertEquals(expectedNode.name, actualNode.name);
            assertArrayEquals((short[]) readField(expectedNode, "bc"), (short[]) readField(actualNode, "bc"));
            assertConstantsEqual((Object[]) readField(expectedNode, "constants"), (Object[]) readField(actualNode, "constants"));
            assertArrayEquals((int[]) readField(expectedNode, "handlers"), (int[]) readField(actualNode, "handlers"));
        }
    }

    private static void assertConstantsEqual(Object[] expectedConstants, Object[] actualConstants) {
        assertEquals(expectedConstants.length, actualConstants.length);
        for (int i = 0; i < expectedConstants.length; i++) {
            Object expected = expectedConstants[i];
            Object actual = actualConstants[i];

            if (expected instanceof BytecodeDSLExample expectedRoot && actual instanceof BytecodeDSLExample actualRoot) {
                // We don't implement equals for root nodes (that's what we're trying to test). Make
                // sure it's at least the same name.
                assertEquals(expectedRoot.name, actualRoot.name);
            } else if (expected instanceof long[] expectedLongs && actual instanceof long[] actualLongs) {
                assertArrayEquals(expectedLongs, actualLongs);
            } else {
                assertEquals(expected, actual);
            }
        }
    }

    private static Object readField(BytecodeDSLExample node, String name) {
        try {
            Field field = node.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(node);
        } catch (ReflectiveOperationException ex) {
            fail("Failed to access interpreter field " + name + " with introspection.");
        }
        throw new AssertionError("unreachable");
    }

    protected static void emitReturn(BytecodeDSLExampleBuilder b, long value) {
        b.beginReturn();
        b.emitLoadConstant(value);
        b.endReturn();
    }

    protected static void emitAppend(BytecodeDSLExampleBuilder b, long value) {
        b.beginAppenderOperation();
        b.emitLoadArgument(0);
        b.emitLoadConstant(value);
        b.endAppenderOperation();
    }

    protected static void emitThrow(BytecodeDSLExampleBuilder b, long value) {
        b.beginThrowOperation();
        b.emitLoadConstant(value);
        b.endThrowOperation();
    }

    public static List<Class<? extends BytecodeDSLExample>> allInterpreters() {
        return List.of(BytecodeDSLExampleBase.class, BytecodeDSLExampleUnsafe.class, BytecodeDSLExampleWithUncached.class, BytecodeDSLExampleWithBE.class, BytecodeDSLExampleWithOptimizations.class,
                        BytecodeDSLExampleProduction.class);
    }

    public static boolean hasBE(Class<? extends BytecodeDSLExample> c) {
        return c == BytecodeDSLExampleWithBE.class || c == BytecodeDSLExampleProduction.class;
    }

}
