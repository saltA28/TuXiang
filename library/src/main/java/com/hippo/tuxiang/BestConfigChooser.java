/*
 * Copyright 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.tuxiang;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

public class BestConfigChooser extends BaseConfigChooser {

    private final int[] mValue = new int[1];

    public BestConfigChooser(int eglContextClientVersion) {
        super(eglContextClientVersion, new int[] {
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_NONE});
    }

    @Override
    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display,
            EGLConfig[] configs) {
        // Use score to avoid "No config chosen"
        int configIndex = 0;
        int maxScore = 0;

        for (int i = 0, n = configs.length; i < n; i++) {
            final EGLConfig config = configs[i];
            final int redSize = findConfigAttrib(egl, display, config,
                    EGL10.EGL_RED_SIZE, 0);
            final int greenSize = findConfigAttrib(egl, display, config,
                    EGL10.EGL_GREEN_SIZE, 0);
            final int blueSize = findConfigAttrib(egl, display, config,
                    EGL10.EGL_BLUE_SIZE, 0);
            final int alphaSize = findConfigAttrib(egl, display, config,
                    EGL10.EGL_ALPHA_SIZE, 0);
            final int sampleBuffers = findConfigAttrib(egl, display, config,
                    EGL10.EGL_SAMPLE_BUFFERS, 0);
            final int samples = findConfigAttrib(egl, display, config,
                    EGL10.EGL_SAMPLES, 0);

            final int score = redSize + greenSize + blueSize + alphaSize +
                    sampleBuffers + samples;

            if (score > maxScore) {
                maxScore = score;
                configIndex = i;
            }
        }

        return configs[configIndex];
    }

    private int findConfigAttrib(EGL10 egl, EGLDisplay display,
            EGLConfig config, int attribute, int defaultValue) {
        if (egl.eglGetConfigAttrib(display, config, attribute, mValue)) {
            return mValue[0];
        }
        return defaultValue;
    }
}
