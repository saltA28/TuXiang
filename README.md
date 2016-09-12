# TuXiang

这是 android.opengl.GLSurfaceView 的修改版，并且尽量与 Android 源码同步，可在文件开头的注释中找到当前对应的 Android 源码的 tag。如果你想使用这个项目，一定要了解具体修改了那些内容后再使用。

1. 所有 GLSurfaceView 内部类都改为外部类；
2. 将 GLSurfaceView 抽象为 GLStuff，提供了类似 GLSurfaceView 且继承 SurfaceView 的 GLSurfaceView，GLSurfaceView 和 GLSurfaceView 都实现了 GLStuff 接口；
3. Renderer.onDrawFrame() 添加了 boolean 类型的返回值，以表示是否需要 SwapBuffers；
4. Renderer 添加了 onGLThreadStart(), onGLThreadExit(), onGLThreadPause() 和 onGLThreadResume() 四个接口。
