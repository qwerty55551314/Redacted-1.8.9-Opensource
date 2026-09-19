package com.replaymod.core.versions;

import org.lwjglx.opengl.Display;

public class GLFW {
   public static boolean glfwWindowShouldClose(long handle) {
      return Display.isCloseRequested();
   }
}
