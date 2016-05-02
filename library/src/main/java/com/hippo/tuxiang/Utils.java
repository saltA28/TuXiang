/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.tuxiang;

import android.content.pm.ConfigurationInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class Utils {
    private Utils() {}

    private static int sGLESVersion = -1;

    public static int getGLESVersion() {
        if (sGLESVersion == -1) {
            sGLESVersion = getGLESVersionInternal();
        }
        return sGLESVersion;
    }

    private static int getGLESVersionInternal() {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method method = clazz.getMethod("getInt", String.class, int.class);
            Object result = method.invoke(null, "ro.opengles.version",
                    ConfigurationInfo.GL_ES_VERSION_UNDEFINED);
            if (result instanceof Integer) {
                return (Integer) result;
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return ConfigurationInfo.GL_ES_VERSION_UNDEFINED;
    }
}
