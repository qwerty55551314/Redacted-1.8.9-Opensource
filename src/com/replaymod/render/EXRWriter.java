package com.replaymod.render;

import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.render.frame.BitmapFrame;
import com.replaymod.render.rendering.Channel;
import com.replaymod.render.rendering.FrameConsumer;
import com.replaymod.render.utils.ByteBufferPool;
import com.replaymod.render.utils.Lwjgl3Loader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.crash.CrashReport;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyexr.EXRChannelInfo;
import org.lwjgl.util.tinyexr.EXRHeader;
import org.lwjgl.util.tinyexr.EXRImage;
import org.lwjgl.util.tinyexr.TinyEXR;
import org.lwjgl.util.tinyexr.EXRChannelInfo.Buffer;

public class EXRWriter implements FrameConsumer<BitmapFrame> {
   private static final int COMPRESSION = Runtime.getRuntime().availableProcessors() >= 8 ? 2 : 0;
   private final Path outputFolder;
   private final boolean keepAlpha;

   public static FrameConsumer<BitmapFrame> create(Path outputFolder, boolean keepAlpha) {
      return Lwjgl3Loader.createFrameConsumer(EXRWriter.class, new Class[]{Path.class, boolean.class}, new Object[]{outputFolder, keepAlpha});
   }

   public EXRWriter(Path outputFolder, boolean keepAlpha) throws IOException {
      this.outputFolder = outputFolder;
      this.keepAlpha = keepAlpha;
      Files.createDirectories(outputFolder);
   }

   @Override
   public void consume(Map<Channel, BitmapFrame> channels) {
      BitmapFrame bgraFrame = channels.get(Channel.BRGA);
      BitmapFrame depthFrame = channels.get(Channel.DEPTH);
      Path path = this.outputFolder.resolve(bgraFrame.getFrameId() + ".exr");
      ReadableDimension size = bgraFrame.getSize();
      ByteBuffer bgra = bgraFrame.getByteBuffer();
      int width = size.getWidth();
      int height = size.getHeight();
      int numChannels = 4 + (depthFrame != null ? 1 : 0);
      MemoryStack stack = MemoryStack.stackPush();
      EXRHeader header = EXRHeader.malloc(stack);
      TinyEXR.InitEXRHeader(header);
      Buffer channelInfos = EXRChannelInfo.malloc(numChannels, stack);
      IntBuffer pixelTypes = MemoryStack.stackMallocInt(numChannels);
      IntBuffer requestedPixelTypes = MemoryStack.stackMallocInt(numChannels);
      EXRImage image = EXRImage.malloc(stack);
      TinyEXR.InitEXRImage(image);
      PointerBuffer imagePointers = MemoryStack.stackMallocPointer(numChannels);
      FloatBuffer images = MemoryUtil.memAllocFloat(width * height * numChannels);
      PointerBuffer err = MemoryStack.stackMallocPointer(1);

      try {
         header.num_channels(numChannels);
         header.channels(channelInfos);
         header.pixel_types(pixelTypes);
         header.requested_pixel_types(requestedPixelTypes);
         header.compression_type(COMPRESSION);
         MemoryUtil.memASCII("A", true, ((EXRChannelInfo)channelInfos.get(0)).name());
         MemoryUtil.memASCII("B", true, ((EXRChannelInfo)channelInfos.get(1)).name());
         MemoryUtil.memASCII("G", true, ((EXRChannelInfo)channelInfos.get(2)).name());
         MemoryUtil.memASCII("R", true, ((EXRChannelInfo)channelInfos.get(3)).name());

         for (int i = 0; i < numChannels; i++) {
            pixelTypes.put(i, 2);
            requestedPixelTypes.put(i, 1);
         }

         if (depthFrame != null) {
            MemoryUtil.memASCII("Z", true, ((EXRChannelInfo)channelInfos.get(4)).name());
            requestedPixelTypes.put(4, 2);
         }

         image.num_channels(numChannels);
         image.width(width);
         image.height(height);
         image.images(imagePointers);
         FloatBuffer[] bgrChannels = new FloatBuffer[4];
         FloatBuffer depthChannel = null;

         for (int i = 0; i < numChannels; i++) {
            FloatBuffer channel = images.slice();
            ((java.nio.Buffer)channel).position(width * height * i);
            imagePointers.put(i, MemoryUtil.memAddress(channel));
            if (i == 4) {
               depthChannel = channel;
            } else {
               bgrChannels[(i + 3) % 4] = channel;
            }
         }

         int alphaMask = this.keepAlpha ? 0 : 255;

         for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
               bgrChannels[0].put((bgra.get() & 0xFF) / 255.0F);
               bgrChannels[1].put((bgra.get() & 0xFF) / 255.0F);
               bgrChannels[2].put((bgra.get() & 0xFF) / 255.0F);
               bgrChannels[3].put((bgra.get() & 0xFF | alphaMask) / 255.0F);
            }
         }

         if (depthFrame != null && depthChannel != null) {
            depthChannel.put(depthFrame.getByteBuffer().asFloatBuffer());
         }

         int ret = TinyEXR.SaveEXRImageToFile(image, header, path.toString(), err);
         if (ret != 0) {
            String message = MemoryUtil.memASCII(err.get(0));
            TinyEXR.nFreeEXRErrorMessage(err.get(0));
            throw new IOException(message);
         }
      } catch (Throwable t) {
         MCVer.getMinecraft().crashed(CrashReport.makeCrashReport(t, "Exporting EXR frame"));
      } finally {
         MemoryUtil.memFree(images);
         MemoryStack.stackPop();
         channels.values().forEach(it -> ByteBufferPool.release(it.getByteBuffer()));
      }
   }

   @Override
   public void close() {
   }

   @Override
   public boolean isParallelCapable() {
      return true;
   }
}
