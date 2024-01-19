package com.oracle.truffle.api.bytecode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is only used for testing. The DSL generates multiple variants of the interpreter
 * with slightly different {@link GenerateOperations configurations}.
 *
 * Importantly, all of the variants' Builders share a common superclass, which allows us to write
 * tests once and run them on multiple configurations.
 *
 * In order for the variants and their Builders to be compatible, the configurations must agree on
 * specific fields. In particular, the {@link GenerateOperations#languageClass} must match, and
 * fields that generate new builder methods (e.g. {@link GenerateOperations#enableYield()}) must
 * agree. These properties are checked by the DSL.
 *
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GenerateOperationsTestVariants {
    Variant[] value();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Variant {
        String suffix();

        GenerateOperations configuration();
    }
}
