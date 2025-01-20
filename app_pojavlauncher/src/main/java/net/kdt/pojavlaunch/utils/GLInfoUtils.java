package net.kdt.pojavlaunch.utils;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.util.Log;

public class GLInfoUtils {
    public static String GLES_VERSION_PREFIX = "OpenGL ES ";
    private static GLInfo info;

    private static int getMajorGLVersion(String versionString) {
        if(versionString.startsWith(GLES_VERSION_PREFIX)) {
            versionString = versionString.substring(GLES_VERSION_PREFIX.length());
        }
        int firstDot = versionString.indexOf('.');
        String majorVersion = versionString.substring(0, firstDot).trim();
        return Integer.parseInt(majorVersion);
    }

    private static GLInfo queryInfo(int contextGLVersion) {
        String vendor = GLES20.glGetString(GLES20.GL_VENDOR);
        String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
        String versionString = GLES20.glGetString(GLES30.GL_VERSION);
        int version = 2;
        try {
            version = getMajorGLVersion(versionString);
        }catch (NumberFormatException e) {
            Log.w("GLInfoUtils","Failed to parse GL version number, falling back to 2", e);
        }
        // LTW depends on the ability to create a context with a major version of 3,
        // and even if the string parse returns 3 while EGL can only create 2,
        // it's still a noncompilant implementation
        version = Math.min(version, contextGLVersion);
        return new GLInfo(vendor, renderer, version);
    }

    private static void initDummyInfo() {
        Log.e("GLInfoUtils", "An error happened during info query. Will use dummy info. This should be investigated.");
        info = new GLInfo("<Unknown>", "<Unknown>", 2);
    }

    private static EGLContext tryCreateContext(EGLDisplay eglDisplay, EGLConfig config, int majorVersion) {
        int[] egl_context_attributes = new int[] { EGL14.EGL_CONTEXT_CLIENT_VERSION, majorVersion, EGL14.EGL_NONE };
        EGLContext context = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, egl_context_attributes, 0);
        if(context == EGL14.EGL_NO_CONTEXT) {
            Log.e("GLInfoUtils", "Failed to create a context with major version "+majorVersion);
            return null;
        }
        return context;
    }

    private static boolean initAndQueryInfo() {
        // This is here just to satisfy Android M which incorrectly null-checks it
        int[] egl_version = new int[2];
        EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if(eglDisplay == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(eglDisplay, egl_version, 0 , egl_version, 1)) return false;
        int[] egl_attributes = new int[]  {
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 24,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT|EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] config = new EGLConfig[1];
        int[] num_configs = new int[]{0};
        if(!EGL14.eglChooseConfig(eglDisplay, egl_attributes, 0, config, 0, 1, num_configs, 0) || num_configs[0] == 0) {
            EGL14.eglTerminate(eglDisplay);
            Log.e("GLInfoUtils", "Failed to choose an EGL config");
            return false;
        }

        int contextGLVersion = 3;

        EGLContext context;
        context = tryCreateContext(eglDisplay, config[0], 3);
        if(context == null) {
            contextGLVersion = 2;
            context = tryCreateContext(eglDisplay, config[0], 2);
        }

        if(!EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, context)) {
            Log.e("GLInfoUtils", "Failed to make context current");
            EGL14.eglDestroyContext(eglDisplay, context);
            EGL14.eglTerminate(eglDisplay);
        }

        info = queryInfo(contextGLVersion);

        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroyContext(eglDisplay, context);
        EGL14.eglTerminate(eglDisplay);
        return true;
    }

    public static GLInfo getInfo() {
        if(info != null) return info;
        Log.i("GLInfoUtils", "Querying graphics device info...");
        boolean infoQueryResult = false;
        try {
            infoQueryResult = initAndQueryInfo();
        }catch (Throwable e) {
            Log.e("GLInfoUtils", "Throwable when trying to initialize GL info", e);
        }
        if(!infoQueryResult) initDummyInfo();
        return info;
    }

    public static class GLInfo {
        public final String vendor;
        public final String renderer;
        public final int glesMajorVersion;
        protected GLInfo(String vendor, String renderer, int glesMajorVersion) {
            this.vendor = vendor;
            this.renderer = renderer;
            this.glesMajorVersion = glesMajorVersion;
        }
    }
}
