/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.c;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

@Platforms(Platform.HOSTED_ONLY.class)
public final class CIsolateDataFactory {

    public static <T extends PointerBase> CIsolateData<T> createStruct(String name, Class<T> clazz) {
        return create(name, SizeOf.unsigned(clazz));
    }

    public static <T extends PointerBase> CIsolateData<T> create(String name, UnsignedWord size) {
        return new CIsolateData<>(name, size.rawValue());
    }

    private static int unspecifiedSuffix = 1;

    /**
     * This is a workaround method for generating unique suffixes for objects which cannot be named
     * uniquely.
     */
    public static synchronized String getUnspecifiedSuffix() {
        return "unspecified" + unspecifiedSuffix++;
    }

    private CIsolateDataFactory() {
    }
}
