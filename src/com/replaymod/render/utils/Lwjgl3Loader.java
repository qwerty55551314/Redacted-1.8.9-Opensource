package com.replaymod.render.utils;

import com.replaymod.core.ReplayMod;
import com.replaymod.render.rendering.Frame;
import com.replaymod.render.rendering.FrameConsumer;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.ProtectionDomain;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.apache.commons.io.IOUtils;

public class Lwjgl3Loader extends URLClassLoader {
   private static Path tempJarFile;
   private static Lwjgl3Loader instance;
   private final Set<String> implClasses = new CopyOnWriteArraySet<>();

   private Lwjgl3Loader(Path jarFile) throws IOException, ReflectiveOperationException {
      super(new URL[]{jarFile.toUri().toURL()}, Lwjgl3Loader.class.getClassLoader());
      Path nativesDir = ReplayMod.instance.folders.getCacheFolder().resolve("lwjgl-natives");
      Class<?> configClass = Class.forName("org.lwjgl.system.Configuration", true, this);
      Method setMethod = configClass.getMethod("set", Object.class);
      setMethod.invoke(configClass.getField("SHARED_LIBRARY_EXTRACT_DIRECTORY").get(null), nativesDir.toAbsolutePath().toString());
      setMethod.invoke(configClass.getField("SHARED_LIBRARY_EXTRACT_PATH").get(null), null);
   }

   private boolean canBeSharedWithMc(String name) {
      if (name.startsWith("org.lwjgl.")) {
         return false;
      }

      for (String implClass : this.implClasses) {
         if (name.startsWith(implClass)) {
            return false;
         }
      }

      return true;
   }

   @Override
   protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (!this.canBeSharedWithMc(name)) {
         synchronized (this.getClassLoadingLock(name)) {
            Class<?> cls = this.findLoadedClass(name);
            if (cls == null) {
               cls = this.findClass(name);
            }

            if (resolve) {
               this.resolveClass(cls);
            }

            return cls;
         }
      } else {
         return super.loadClass(name, resolve);
      }
   }

   @Override
   protected Class<?> findClass(String name) throws ClassNotFoundException {
      try {
         return super.findClass(name);
      } catch (ClassNotFoundException e) {
         String path = name.replace('.', '/').concat(".class");
         URL url = this.getParent().getResource(path);
         if (url == null) {
            throw e;
         }

         try {
            byte[] bytes = IOUtils.toByteArray(url);
            return this.defineClass(name, bytes, 0, bytes.length, (ProtectionDomain)null);
         } catch (IOException e1) {
            throw new ClassNotFoundException(name, e1);
         }
      }
   }

   private static synchronized Path getJarFile() throws IOException {
      if (tempJarFile == null) {
         Path jarFile = Files.createTempFile("replaymod-lwjgl", ".jar");
         jarFile.toFile().deleteOnExit();

         try (InputStream in = Lwjgl3Loader.class.getResourceAsStream("lwjgl.jar")) {
            if (in == null) {
               throw new IOException("Failed to find embedded lwjgl.jar file.");
            }

            Files.copy(in, jarFile, StandardCopyOption.REPLACE_EXISTING);
         }

         tempJarFile = jarFile;
      }

      return tempJarFile;
   }

   public static synchronized Lwjgl3Loader instance() {
      if (instance == null) {
         try {
            instance = new Lwjgl3Loader(getJarFile());
         } catch (IOException | ReflectiveOperationException e) {
            throw new RuntimeException(e);
         }
      }

      return instance;
   }

   public static <P extends Frame> FrameConsumer<P> createFrameConsumer(Class<? extends FrameConsumer<P>> implClass, Class<?>[] parameterTypes, Object[] args) {
      try {
         Lwjgl3Loader loader = instance();
         loader.implClasses.add(implClass.getName());
         Class<?> realClass = Class.forName(implClass.getName(), true, loader);
         Constructor<?> constructor = realClass.getConstructor(parameterTypes);
         return (FrameConsumer<P>)constructor.newInstance(args);
      } catch (ReflectiveOperationException e) {
         throw new RuntimeException(e);
      }
   }

   static {
      registerAsParallelCapable();
   }
}
