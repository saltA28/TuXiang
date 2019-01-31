/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.hippo.tuxiang.demo;

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

import android.content.Context;
import android.graphics.PixelFormat;
import com.hippo.tuxiang.ComponentSizeChooser;
import com.hippo.tuxiang.DefaultContextFactory;
import com.hippo.tuxiang.GLSurfaceView;
import com.hippo.tuxiang.Renderer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * A simple GLSurfaceView sub-class that demonstrate how to perform
 * OpenGL ES 2.0 rendering into a GL Surface. Note the following important
 * details:
 *
 * - The class must use a custom context factory to enable 2.0 rendering.
 *   See ContextFactory class definition below.
 *
 * - The class must use a custom EGLConfigChooser to be able to select
 *   an EGLConfig that supports 2.0. This is done by providing a config
 *   specification to eglChooseConfig() that has the attribute
 *   EGL10.ELG_RENDERABLE_TYPE containing the EGL_OPENGL_ES2_BIT flag
 *   set. See ConfigChooser class definition below.
 *
 * - The class must select the surface's format, then choose an EGLConfig
 *   that matches it exactly (with regards to red/green/blue/alpha channels
 *   bit depths). Failure to do so would result in an EGL_BAD_MATCH error.
 */
class GL2JNIView extends GLSurfaceView {

  public GL2JNIView(Context context) {
    super(context);
    init(false, 0, 0);
  }

  public GL2JNIView(Context context, boolean translucent, int depth, int stencil) {
    super(context);
    init(translucent, depth, stencil);
  }

  private void init(boolean translucent, int depth, int stencil) {

    /* By default, GLSurfaceView() creates a RGB_565 opaque surface.
     * If we want a translucent one, we should change the surface's
     * format here, using PixelFormat.TRANSLUCENT for GL Surfaces
     * is interpreted as any 32-bit surface with alpha by SurfaceFlinger.
     */
    if (translucent) {
      this.getHolder().setFormat(PixelFormat.TRANSLUCENT);
    }

    /* Setup the context factory for 2.0 rendering.
     * See ContextFactory class definition below
     */
    setEGLContextFactory(new DefaultContextFactory(2));

    /* We need to choose an EGLConfig that matches the format of
     * our surface exactly. This is going to be done in our
     * custom config chooser. See ConfigChooser class definition
     * below.
     */
    setEGLConfigChooser( translucent ?
        new ComponentSizeChooser(2, 8, 8, 8, 8, depth, stencil) :
        new ComponentSizeChooser(2, 5, 6, 5, 0, depth, stencil) );

    /* Set the renderer responsible for frame rendering */
    setRenderer(new GL2JNIRenderer());
  }

  private static class GL2JNIRenderer implements Renderer {

    @Override
    public void onGLThreadStart() { }

    @Override
    public void onGLThreadExit() { }

    @Override
    public void onGLThreadPause() { }

    @Override
    public void onGLThreadResume() { }

    public boolean onDrawFrame(GL10 gl) {
      GL2JNILib.step();
      return true;
    }

    public void onSurfaceChanged(GL10 gl, int width, int height) {
      GL2JNILib.init(width, height);
    }

    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
      // Do nothing.
    }
  }
}
