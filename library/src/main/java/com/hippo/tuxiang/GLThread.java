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

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.opengles.GL10;

// android-7.0.0_r1

/**
 * A generic GL Thread. Takes care of initializing EGL and GL. Delegates
 * to a Renderer instance to do the actual drawing. Can be configured to
 * render continuously or on request.
 *
 * All potentially blocking synchronization is done through the
 * sGLThreadManager object. This avoids multiple-lock ordering issues.
 *
 */
class GLThread extends Thread {
    GLThread(WeakReference<GLStuff> glStuffWeakRef) {
        super();
        mWidth = 0;
        mHeight = 0;
        mRequestRender = true;
        mRenderMode = GLStuff.RENDERMODE_CONTINUOUSLY;
        mWantRenderNotification = false;
        mGLStuffWeakRef = glStuffWeakRef;
        mGLThreadManager = GLThreadManager.getInstance();
    }

    @Override
    public void run() {
        setName("GLThread " + getId());
        if (GLStuff.LOG_THREADS) {
            Log.i("GLThread", "starting tid=" + getId());
        }

        GLStuff stuff;

        stuff = mGLStuffWeakRef.get();
        if (stuff != null) {
            stuff.getRenderer().onGLThreadStart();
            stuff = null;
        }

        try {
            guardedRun();
        } catch (InterruptedException e) {
            // fall thru and exit normally
        } finally {
            stuff = mGLStuffWeakRef.get();
            if (stuff != null) {
                stuff.getRenderer().onGLThreadExit();
            }
            mGLThreadManager.threadExiting(this);
        }
    }

    /*
     * This private method should only be called inside a
     * synchronized(mGLThreadManager) block.
     */
    private void stopEglSurfaceLocked() {
        if (mHaveEglSurface) {
            mHaveEglSurface = false;
            mEglHelper.destroySurface();
        }
    }

    /*
     * This private method should only be called inside a
     * synchronized(mGLThreadManager) block.
     */
    private void stopEglContextLocked() {
        if (mHaveEglContext) {
            mEglHelper.finish();
            mHaveEglContext = false;
            mGLThreadManager.releaseEglContextLocked(this);
        }
    }

    private void guardedRun() throws InterruptedException {
        mEglHelper = new EglHelper(mGLStuffWeakRef);
        mHaveEglContext = false;
        mHaveEglSurface = false;
        mWantRenderNotification = false;

        try {
            GL10 gl = null;
            boolean createEglContext = false;
            boolean createEglSurface = false;
            boolean createGlInterface = false;
            boolean lostEglContext = false;
            boolean sizeChanged = false;
            boolean wantRenderNotification = false;
            boolean doRenderNotification = false;
            boolean askedToReleaseEglContext = false;
            int w = 0;
            int h = 0;
            Runnable event = null;

            while (true) {
                synchronized (mGLThreadManager) {
                    while (true) {
                        if (mShouldExit) {
                            return;
                        }

                        if (! mEventQueue.isEmpty()) {
                            event = mEventQueue.remove(0);
                            break;
                        }

                        // Update the pause state.
                        boolean pausing = false;
                        if (mPaused != mRequestPaused) {
                            pausing = mRequestPaused;
                            mPaused = mRequestPaused;
                            mGLThreadManager.notifyAll();
                            if (GLStuff.LOG_PAUSE_RESUME) {
                                Log.i("GLThread", "mPaused is now " + mPaused + " tid=" + getId());
                            }
                            final GLStuff stuff = mGLStuffWeakRef.get();
                            if (stuff != null) {
                                if (pausing) {
                                    stuff.getRenderer().onGLThreadPause();
                                } else {
                                    stuff.getRenderer().onGLThreadResume();
                                }
                            }
                        }

                        // Do we need to give up the EGL context?
                        if (mShouldReleaseEglContext) {
                            if (GLStuff.LOG_SURFACE) {
                                Log.i("GLThread", "releasing EGL context because asked to tid=" + getId());
                            }
                            stopEglSurfaceLocked();
                            stopEglContextLocked();
                            mShouldReleaseEglContext = false;
                            askedToReleaseEglContext = true;
                        }

                        // Have we lost the EGL context?
                        if (lostEglContext) {
                            stopEglSurfaceLocked();
                            stopEglContextLocked();
                            lostEglContext = false;
                        }

                        // When pausing, release the EGL surface:
                        if (pausing && mHaveEglSurface) {
                            if (GLStuff.LOG_SURFACE) {
                                Log.i("GLThread", "releasing EGL surface because paused tid=" + getId());
                            }
                            stopEglSurfaceLocked();
                        }

                        // When pausing, optionally release the EGL Context:
                        if (pausing && mHaveEglContext) {
                            final GLStuff stuff = mGLStuffWeakRef.get();
                            final boolean preserveEglContextOnPause = stuff != null && stuff.getPreserveEGLContextOnPause();
                            if (!preserveEglContextOnPause || mGLThreadManager.shouldReleaseEGLContextWhenPausing()) {
                                stopEglContextLocked();
                                if (GLStuff.LOG_SURFACE) {
                                    Log.i("GLThread", "releasing EGL context because paused tid=" + getId());
                                }
                            }
                        }

                        // When pausing, optionally terminate EGL:
                        if (pausing) {
                            if (mGLThreadManager.shouldTerminateEGLWhenPausing()) {
                                mEglHelper.finish();
                                if (GLStuff.LOG_SURFACE) {
                                    Log.i("GLThread", "terminating EGL because paused tid=" + getId());
                                }
                            }
                        }

                        // Have we lost the SurfaceView surface?
                        if ((! mHasSurface) && (! mWaitingForSurface)) {
                            if (GLStuff.LOG_SURFACE) {
                                Log.i("GLThread", "noticed surfaceView surface lost tid=" + getId());
                            }
                            if (mHaveEglSurface) {
                                stopEglSurfaceLocked();
                            }
                            mWaitingForSurface = true;
                            mSurfaceIsBad = false;
                            mGLThreadManager.notifyAll();
                        }

                        // Have we acquired the surface view surface?
                        if (mHasSurface && mWaitingForSurface) {
                            if (GLStuff.LOG_SURFACE) {
                                Log.i("GLThread", "noticed surfaceView surface acquired tid=" + getId());
                            }
                            mWaitingForSurface = false;
                            mGLThreadManager.notifyAll();
                        }

                        if (doRenderNotification) {
                            if (GLStuff.LOG_SURFACE) {
                                Log.i("GLThread", "sending render notification tid=" + getId());
                            }
                            mWantRenderNotification = false;
                            doRenderNotification = false;
                            mRenderComplete = true;
                            mGLThreadManager.notifyAll();
                        }

                        // Ready to draw?
                        if (readyToDraw()) {

                            // If we don't have an EGL context, try to acquire one.
                            if (! mHaveEglContext) {
                                if (askedToReleaseEglContext) {
                                    askedToReleaseEglContext = false;
                                } else if (mGLThreadManager.tryAcquireEglContextLocked(this)) {
                                    try {
                                        mEglHelper.start();
                                    } catch (RuntimeException t) {
                                        mGLThreadManager.releaseEglContextLocked(this);
                                        throw t;
                                    }
                                    mHaveEglContext = true;
                                    createEglContext = true;

                                    mGLThreadManager.notifyAll();
                                }
                            }

                            if (mHaveEglContext && !mHaveEglSurface) {
                                mHaveEglSurface = true;
                                createEglSurface = true;
                                createGlInterface = true;
                                sizeChanged = true;
                            }

                            if (mHaveEglSurface) {
                                if (mSizeChanged) {
                                    sizeChanged = true;
                                    w = mWidth;
                                    h = mHeight;
                                    mWantRenderNotification = true;
                                    if (GLStuff.LOG_SURFACE) {
                                        Log.i("GLThread",
                                                "noticing that we want render notification tid="
                                                        + getId());
                                    }

                                    // Destroy and recreate the EGL surface.
                                    createEglSurface = true;

                                    mSizeChanged = false;
                                }
                                mRequestRender = false;
                                mGLThreadManager.notifyAll();
                                if (mWantRenderNotification) {
                                    wantRenderNotification = true;
                                }
                                break;
                            }
                        }

                        // By design, this is the only place in a GLThread thread where we wait().
                        if (GLStuff.LOG_THREADS) {
                            Log.i("GLThread", "waiting tid=" + getId()
                                    + " mHaveEglContext: " + mHaveEglContext
                                    + " mHaveEglSurface: " + mHaveEglSurface
                                    + " mFinishedCreatingEglSurface: " + mFinishedCreatingEglSurface
                                    + " mPaused: " + mPaused
                                    + " mHasSurface: " + mHasSurface
                                    + " mSurfaceIsBad: " + mSurfaceIsBad
                                    + " mWaitingForSurface: " + mWaitingForSurface
                                    + " mWidth: " + mWidth
                                    + " mHeight: " + mHeight
                                    + " mRequestRender: " + mRequestRender
                                    + " mRenderMode: " + mRenderMode);
                        }
                        mGLThreadManager.wait();
                    }
                } // end of synchronized(mGLThreadManager)

                if (event != null) {
                    event.run();
                    event = null;
                    continue;
                }

                if (createEglSurface) {
                    if (GLStuff.LOG_SURFACE) {
                        Log.w("GLThread", "egl createSurface");
                    }
                    if (mEglHelper.createSurface()) {
                        synchronized(mGLThreadManager) {
                            mFinishedCreatingEglSurface = true;
                            mGLThreadManager.notifyAll();
                        }
                    } else {
                        synchronized(mGLThreadManager) {
                            mFinishedCreatingEglSurface = true;
                            mSurfaceIsBad = true;
                            mGLThreadManager.notifyAll();
                        }
                        continue;
                    }
                    createEglSurface = false;
                }

                if (createGlInterface) {
                    gl = (GL10) mEglHelper.createGL();

                    mGLThreadManager.checkGLDriver(gl);
                    createGlInterface = false;
                }

                if (createEglContext) {
                    if (GLStuff.LOG_RENDERER) {
                        Log.w("GLThread", "onSurfaceCreated");
                    }
                    final GLStuff stuff = mGLStuffWeakRef.get();
                    if (stuff != null) {
                        stuff.getRenderer().onSurfaceCreated(gl, mEglHelper.mEglConfig);
                    }
                    createEglContext = false;
                }

                if (sizeChanged) {
                    if (GLStuff.LOG_RENDERER) {
                        Log.w("GLThread", "onSurfaceChanged(" + w + ", " + h + ")");
                    }
                    final GLStuff stuff = mGLStuffWeakRef.get();
                    if (stuff != null) {
                        stuff.getRenderer().onSurfaceChanged(gl, w, h);
                    }
                    sizeChanged = false;
                }

                if (GLStuff.LOG_RENDERER_DRAW_FRAME) {
                    Log.w("GLThread", "onDrawFrame tid=" + getId());
                }
                boolean drew = false;
                {
                    final GLStuff stuff = mGLStuffWeakRef.get();
                    if (stuff != null) {
                        drew = stuff.getRenderer().onDrawFrame(gl);
                    }
                }
                if (drew) {
                    final int swapError = mEglHelper.swap();
                    switch (swapError) {
                        case EGL10.EGL_SUCCESS:
                            break;
                        case EGL11.EGL_CONTEXT_LOST:
                            if (GLStuff.LOG_SURFACE) {
                                Log.i("GLThread", "egl context lost tid=" + getId());
                            }
                            lostEglContext = true;
                            break;
                        default:
                            // Other errors typically mean that the current surface is bad,
                            // probably because the SurfaceView surface has been destroyed,
                            // but we haven't been notified yet.
                            // Log the error to help developers understand why rendering stopped.
                            EglHelper.logEglErrorAsWarning("GLThread", "eglSwapBuffers", swapError);

                            synchronized (mGLThreadManager) {
                                mSurfaceIsBad = true;
                                mGLThreadManager.notifyAll();
                            }
                            break;
                    }
                }

                if (wantRenderNotification) {
                    doRenderNotification = true;
                    wantRenderNotification = false;
                }
            }

        } finally {
            /*
             * clean-up everything...
             */
            synchronized (mGLThreadManager) {
                stopEglSurfaceLocked();
                stopEglContextLocked();
            }
        }
    }

    public boolean ableToDraw() {
        return mHaveEglContext && mHaveEglSurface && readyToDraw();
    }

    private boolean readyToDraw() {
        return (!mPaused) && mHasSurface && (!mSurfaceIsBad)
                && (mWidth > 0) && (mHeight > 0)
                && (mRequestRender || (mRenderMode == GLStuff.RENDERMODE_CONTINUOUSLY));
    }

    public void setRenderMode(int renderMode) {
        if ( !((GLStuff.RENDERMODE_WHEN_DIRTY <= renderMode) && (renderMode <= GLStuff.RENDERMODE_CONTINUOUSLY)) ) {
            throw new IllegalArgumentException("renderMode");
        }
        synchronized(mGLThreadManager) {
            mRenderMode = renderMode;
            mGLThreadManager.notifyAll();
        }
    }

    public int getRenderMode() {
        synchronized(mGLThreadManager) {
            return mRenderMode;
        }
    }

    public void requestRender() {
        synchronized(mGLThreadManager) {
            mRequestRender = true;
            mGLThreadManager.notifyAll();
        }
    }

    public void requestRenderAndWait() {
        synchronized(mGLThreadManager) {
            // If we are already on the GL thread, this means a client callback
            // has caused reentrancy, for example via updating the SurfaceView parameters.
            // We will return to the client rendering code, so here we don't need to
            // do anything.
            if (Thread.currentThread() == this) {
                return;
            }

            mWantRenderNotification = true;
            mRequestRender = true;
            mRenderComplete = false;

            mGLThreadManager.notifyAll();

            while (!mExited && !mPaused && !mRenderComplete && ableToDraw()) {
                try {
                    mGLThreadManager.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

        }
    }

    public void surfaceCreated() {
        synchronized(mGLThreadManager) {
            if (GLStuff.LOG_THREADS) {
                Log.i("GLThread", "surfaceCreated tid=" + getId());
            }
            mHasSurface = true;
            mFinishedCreatingEglSurface = false;
            mGLThreadManager.notifyAll();
            while (mWaitingForSurface
                    && !mFinishedCreatingEglSurface
                    && !mExited) {
                try {
                    mGLThreadManager.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void surfaceDestroyed() {
        synchronized(mGLThreadManager) {
            if (GLStuff.LOG_THREADS) {
                Log.i("GLThread", "surfaceDestroyed tid=" + getId());
            }
            mHasSurface = false;
            mGLThreadManager.notifyAll();
            while((!mWaitingForSurface) && (!mExited)) {
                try {
                    mGLThreadManager.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void onPause() {
        synchronized (mGLThreadManager) {
            if (GLStuff.LOG_PAUSE_RESUME) {
                Log.i("GLThread", "onPause tid=" + getId());
            }
            mRequestPaused = true;
            mGLThreadManager.notifyAll();
            while ((! mExited) && (! mPaused)) {
                if (GLStuff.LOG_PAUSE_RESUME) {
                    Log.i("Main thread", "onPause waiting for mPaused.");
                }
                try {
                    mGLThreadManager.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void onResume() {
        synchronized (mGLThreadManager) {
            if (GLStuff.LOG_PAUSE_RESUME) {
                Log.i("GLThread", "onResume tid=" + getId());
            }
            mRequestPaused = false;
            mRequestRender = true;
            mRenderComplete = false;
            mGLThreadManager.notifyAll();
            while ((! mExited) && mPaused && (!mRenderComplete)) {
                if (GLStuff.LOG_PAUSE_RESUME) {
                    Log.i("Main thread", "onResume waiting for !mPaused.");
                }
                try {
                    mGLThreadManager.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void onWindowResize(int w, int h) {
        synchronized (mGLThreadManager) {
            mWidth = w;
            mHeight = h;
            mSizeChanged = true;
            mRequestRender = true;
            mRenderComplete = false;

            // If we are already on the GL thread, this means a client callback
            // has caused reentrancy, for example via updating the SurfaceView parameters.
            // We need to process the size change eventually though and update our EGLSurface.
            // So we set the parameters and return so they can be processed on our
            // next iteration.
            if (Thread.currentThread() == this) {
                return;
            }

            mGLThreadManager.notifyAll();

            // Wait for thread to react to resize and render a frame
            while (! mExited && !mPaused && !mRenderComplete
                    && ableToDraw()) {
                if (GLStuff.LOG_SURFACE) {
                    Log.i("Main thread", "onWindowResize waiting for render complete from tid=" + getId());
                }
                try {
                    mGLThreadManager.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void requestExitAndWait() {
        // don't call this from GLThread thread or it is a guaranteed
        // deadlock!
        synchronized(mGLThreadManager) {
            mShouldExit = true;
            mGLThreadManager.notifyAll();
            while (! mExited) {
                try {
                    mGLThreadManager.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void requestReleaseEglContextLocked() {
        mShouldReleaseEglContext = true;
        mGLThreadManager.notifyAll();
    }

    /**
     * Queue an "event" to be run on the GL rendering thread.
     * @param r the runnable to be run on the GL rendering thread.
     */
    public void queueEvent(Runnable r) {
        if (r == null) {
            throw new IllegalArgumentException("r must not be null");
        }
        synchronized(mGLThreadManager) {
            mEventQueue.add(r);
            mGLThreadManager.notifyAll();
        }
    }

    // Once the thread is started, all accesses to the following member
    // variables are protected by the mGLThreadManager monitor
    private boolean mShouldExit;
    boolean mExited;
    private boolean mRequestPaused;
    private boolean mPaused;
    private boolean mHasSurface;
    private boolean mSurfaceIsBad;
    private boolean mWaitingForSurface;
    private boolean mHaveEglContext;
    private boolean mHaveEglSurface;
    private boolean mFinishedCreatingEglSurface;
    private boolean mShouldReleaseEglContext;
    private int mWidth;
    private int mHeight;
    private int mRenderMode;
    private boolean mRequestRender;
    private boolean mWantRenderNotification;
    private boolean mRenderComplete;
    private final ArrayList<Runnable> mEventQueue = new ArrayList<>();
    private boolean mSizeChanged = true;

    // End of member variables protected by the mGLThreadManager monitor.

    private final GLThreadManager mGLThreadManager;
    private EglHelper mEglHelper;

    /**
     * Set once at thread construction time, nulled out when the parent view is garbage
     * called. This weak reference allows the GLSurfaceView to be garbage collected while
     * the GLThread is still alive.
     */
    private final WeakReference<GLStuff> mGLStuffWeakRef;
}
