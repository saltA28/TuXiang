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

import android.util.Log;

import javax.microedition.khronos.opengles.GL10;

final class GLThreadManager {
    private static final String TAG = "GLThreadManager";
    private static final boolean LOG_DEBUG = false;

    public static GLThreadManager sInstance;

    public static GLThreadManager getInstance() {
        if (sInstance == null) {
            sInstance = new GLThreadManager();
        }
        return sInstance;
    }

    private GLThreadManager() {}

    public synchronized void threadExiting(GLThread thread) {
        if (LOG_DEBUG) {
            Log.i("GLThread", "exiting tid=" +  thread.getId());
        }
        thread.mExited = true;
        if (mEglOwner == thread) {
            mEglOwner = null;
        }
        notifyAll();
    }

    /*
     * Tries once to acquire the right to use an EGL
     * context. Does not block. Requires that we are already
     * in the sGLThreadManager monitor when this is called.
     *
     * @return true if the right to use an EGL context was acquired.
     */
    public boolean tryAcquireEglContextLocked(GLThread thread) {
        if (mEglOwner == thread || mEglOwner == null) {
            mEglOwner = thread;
            notifyAll();
            return true;
        }
        checkGLESVersion();
        if (mMultipleGLESContextsAllowed) {
            return true;
        }
        // Notify the owning thread that it should release the context.
        // TODO: implement a fairness policy. Currently
        // if the owning thread is drawing continuously it will just
        // reacquire the EGL context.
        if (mEglOwner != null) {
            mEglOwner.requestReleaseEglContextLocked();
        }
        return false;
    }

    /*
     * Releases the EGL context. Requires that we are already in the
     * sGLThreadManager monitor when this is called.
     */
    public void releaseEglContextLocked(GLThread thread) {
        if (mEglOwner == thread) {
            mEglOwner = null;
        }
        notifyAll();
    }

    public synchronized boolean shouldReleaseEGLContextWhenPausing() {
        // Release the EGL context when pausing even if
        // the hardware supports multiple EGL contexts.
        // Otherwise the device could run out of EGL contexts.
        return mLimitedGLESContexts;
    }

    public synchronized boolean shouldTerminateEGLWhenPausing() {
        checkGLESVersion();
        return !mMultipleGLESContextsAllowed;
    }

    public synchronized void checkGLDriver(GL10 gl) {
        if (! mGLESDriverCheckComplete) {
            checkGLESVersion();
            String renderer = gl.glGetString(GL10.GL_RENDERER);
            if (mGLESVersion < kGLES_20) {
                mMultipleGLESContextsAllowed =
                        ! renderer.startsWith(kMSM7K_RENDERER_PREFIX);
                notifyAll();
            }
            mLimitedGLESContexts = !mMultipleGLESContextsAllowed;
            if (LOG_DEBUG) {
                Log.w(TAG, "checkGLDriver renderer = \"" + renderer + "\" multipleContextsAllowed = "
                        + mMultipleGLESContextsAllowed
                        + " mLimitedGLESContexts = " + mLimitedGLESContexts);
            }
            mGLESDriverCheckComplete = true;
        }
    }

    private void checkGLESVersion() {
        if (! mGLESVersionCheckComplete) {
            mGLESVersion = Utils.getGLESVersion();
            if (mGLESVersion >= kGLES_20) {
                mMultipleGLESContextsAllowed = true;
            }
            if (LOG_DEBUG) {
                Log.w(TAG, "checkGLESVersion mGLESVersion =" +
                        " " + mGLESVersion + " mMultipleGLESContextsAllowed = " + mMultipleGLESContextsAllowed);
            }
            mGLESVersionCheckComplete = true;
        }
    }

    /**
     * This check was required for some pre-Android-3.0 hardware. Android 3.0 provides
     * support for hardware-accelerated views, therefore multiple EGL contexts are
     * supported on all Android 3.0+ EGL drivers.
     */
    private boolean mGLESVersionCheckComplete;
    private int mGLESVersion;
    private boolean mGLESDriverCheckComplete;
    private boolean mMultipleGLESContextsAllowed;
    private boolean mLimitedGLESContexts;
    private static final int kGLES_20 = 0x20000;
    private static final String kMSM7K_RENDERER_PREFIX =
            "Q3Dimension MSM7500 ";
    private GLThread mEglOwner;
}
