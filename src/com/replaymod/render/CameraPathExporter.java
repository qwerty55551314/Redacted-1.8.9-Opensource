package com.replaymod.render;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.utils.Utils;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.javagl.jgltf.impl.v2.Accessor;
import com.replaymod.lib.de.javagl.jgltf.impl.v2.Animation;
import com.replaymod.lib.de.javagl.jgltf.impl.v2.AnimationChannel;
import com.replaymod.lib.de.javagl.jgltf.impl.v2.AnimationChannelTarget;
import com.replaymod.lib.de.javagl.jgltf.impl.v2.AnimationSampler;
import com.replaymod.lib.de.javagl.jgltf.impl.v2.Asset;
import com.replaymod.lib.de.javagl.jgltf.impl.v2.BufferView;
import com.replaymod.lib.de.javagl.jgltf.impl.v2.Camera;
import com.replaymod.lib.de.javagl.jgltf.impl.v2.CameraPerspective;
import com.replaymod.lib.de.javagl.jgltf.impl.v2.GlTF;
import com.replaymod.lib.de.javagl.jgltf.impl.v2.Node;
import com.replaymod.lib.de.javagl.jgltf.model.io.v2.GltfAssetV2;
import com.replaymod.lib.de.javagl.jgltf.model.io.v2.GltfAssetWriterV2;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Quaternion;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Vector4f;
import com.replaymod.replay.camera.CameraEntity;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.apache.commons.io.FilenameUtils;

public class CameraPathExporter {
   private final Minecraft mc = MCVer.getMinecraft();
   private final RenderSettings settings;
   private int framesDone;
   private ByteBuffer timeBuffer;
   private ByteBuffer cameraTranslationBuffer;
   private ByteBuffer cameraRotationBuffer;

   public CameraPathExporter(RenderSettings settings) {
      this.settings = settings;
   }

   public void setup(int totalFrames) {
      this.timeBuffer = ByteBuffer.allocate(4 * totalFrames).order(ByteOrder.LITTLE_ENDIAN);
      this.cameraTranslationBuffer = ByteBuffer.allocate(4 * totalFrames * 3).order(ByteOrder.LITTLE_ENDIAN);
      this.cameraRotationBuffer = ByteBuffer.allocate(4 * totalFrames * 4).order(ByteOrder.LITTLE_ENDIAN);
   }

   public void recordFrame(float tickDelta) {
      Entity entity = (Entity)(this.mc.getRenderViewEntity() == null ? this.mc.thePlayer : this.mc.getRenderViewEntity());
      float eyeHeight = entity.getEyeHeight();
      float x = (float)(entity.prevPosX + (entity.posX - entity.prevPosX) * tickDelta);
      float y = (float)(entity.prevPosY + (entity.posY - entity.prevPosY) * tickDelta + eyeHeight);
      float z = (float)(entity.prevPosZ + (entity.posZ - entity.prevPosZ) * tickDelta);
      float yaw = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * tickDelta + 180.0F;
      float pitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * tickDelta;
      float roll = entity instanceof CameraEntity ? ((CameraEntity)entity).roll : 0.0F;
      Quaternion quatYaw = new Quaternion();
      Quaternion quatPitch = new Quaternion();
      Quaternion quatRoll = new Quaternion();
      quatYaw.setFromAxisAngle(new Vector4f(0.0F, -1.0F, 0.0F, (float)Math.toRadians(yaw)));
      quatPitch.setFromAxisAngle(new Vector4f(-1.0F, 0.0F, 0.0F, (float)Math.toRadians(pitch)));
      quatRoll.setFromAxisAngle(new Vector4f(0.0F, 0.0F, -1.0F, (float)Math.toRadians(roll)));
      Quaternion quaternion = new Quaternion(0.0F, 0.0F, 0.0F, 1.0F);
      Quaternion.mul(quaternion, quatYaw, quaternion);
      Quaternion.mul(quaternion, quatPitch, quaternion);
      Quaternion.mul(quaternion, quatRoll, quaternion);
      quaternion.normalise(quaternion);
      float[] translation = new float[]{x, y, z};
      float[] rotation = new float[]{quaternion.getX(), quaternion.getY(), quaternion.getZ(), quaternion.getW()};
      this.timeBuffer.putFloat((float)this.framesDone / this.settings.getFramesPerSecond());

      for (float f : translation) {
         this.cameraTranslationBuffer.putFloat(f);
      }

      for (float f : rotation) {
         this.cameraRotationBuffer.putFloat(f);
      }

      this.framesDone++;
   }

   public void finish() throws IOException {
      int timeBufferSize = ((Buffer)this.timeBuffer).rewind().remaining();
      int cameraTranslationBufferSize = ((Buffer)this.cameraTranslationBuffer).rewind().remaining();
      int cameraRotationBufferSize = ((Buffer)this.cameraRotationBuffer).rewind().remaining();
      int binaryDataSize = 0;
      binaryDataSize += timeBufferSize;
      binaryDataSize += cameraTranslationBufferSize;
      binaryDataSize += cameraRotationBufferSize;
      ByteBuffer binaryData = ByteBuffer.allocate(binaryDataSize);
      int timeBufferOffset = binaryData.position();
      binaryData.put(this.timeBuffer);
      int cameraTranslationBufferOffset = binaryData.position();
      binaryData.put(this.cameraTranslationBuffer);
      int cameraRotationBufferOffset = binaryData.position();
      binaryData.put(this.cameraRotationBuffer);
      ((Buffer)binaryData).rewind();
      GlTF glTF = new GlTF();
      glTF.setAsset(Utils.configure(new Asset(), assetx -> {
         assetx.setVersion("2.0");
         assetx.setGenerator("ReplayMod v" + ReplayMod.instance.getVersion());
      }));
      glTF.addAnimations(Utils.configure(new Animation(), animation -> {
         animation.addChannels(Utils.configure(new AnimationChannel(), channel -> {
            channel.setTarget(Utils.configure(new AnimationChannelTarget(), target -> {
               target.setNode(0);
               target.setPath("translation");
            }));
            channel.setSampler(0);
         }));
         animation.addChannels(Utils.configure(new AnimationChannel(), channel -> {
            channel.setTarget(Utils.configure(new AnimationChannelTarget(), target -> {
               target.setNode(0);
               target.setPath("rotation");
            }));
            channel.setSampler(1);
         }));
         animation.addSamplers(Utils.configure(new AnimationSampler(), sampler -> {
            sampler.setInput(0);
            sampler.setOutput(1);
         }));
         animation.addSamplers(Utils.configure(new AnimationSampler(), sampler -> {
            sampler.setInput(0);
            sampler.setOutput(2);
         }));
      }));
      glTF.addCameras(Utils.configure(new Camera(), camera -> {
         camera.setType("perspective");
         camera.setPerspective(Utils.configure(new CameraPerspective(), perspective -> {
            float aspectRatio = (float)this.settings.getVideoWidth() / this.settings.getVideoHeight();
            perspective.setAspectRatio(aspectRatio);
            perspective.setYfov((float)Math.toRadians(this.mc.gameSettings.fovSetting));
            perspective.setZnear(0.05F);
            perspective.setZfar(this.mc.gameSettings.renderDistanceChunks * 16.0F * 4.0F);
         }));
      }));
      glTF.addNodes(Utils.configure(new Node(), node -> node.setCamera(0)));
      glTF.addBuffers(Utils.configure(new com.replaymod.lib.de.javagl.jgltf.impl.v2.Buffer(), buffer -> buffer.setByteLength(binaryData.limit())));
      glTF.addBufferViews(Utils.configure(new BufferView(), bufferView -> {
         bufferView.setBuffer(0);
         bufferView.setByteOffset(timeBufferOffset);
         bufferView.setByteLength(timeBufferSize);
      }));
      glTF.addAccessors(Utils.configure(new Accessor(), accessor -> {
         accessor.setBufferView(0);
         accessor.setType("SCALAR");
         accessor.setComponentType(5126);
         accessor.setCount(this.framesDone);
      }));
      glTF.addBufferViews(Utils.configure(new BufferView(), bufferView -> {
         bufferView.setBuffer(0);
         bufferView.setByteOffset(cameraTranslationBufferOffset);
         bufferView.setByteLength(cameraTranslationBufferSize);
      }));
      glTF.addAccessors(Utils.configure(new Accessor(), accessor -> {
         accessor.setBufferView(1);
         accessor.setType("VEC3");
         accessor.setComponentType(5126);
         accessor.setCount(this.framesDone);
      }));
      glTF.addBufferViews(Utils.configure(new BufferView(), bufferView -> {
         bufferView.setBuffer(0);
         bufferView.setByteOffset(cameraRotationBufferOffset);
         bufferView.setByteLength(cameraRotationBufferSize);
      }));
      glTF.addAccessors(Utils.configure(new Accessor(), accessor -> {
         accessor.setBufferView(2);
         accessor.setType("VEC4");
         accessor.setComponentType(5126);
         accessor.setCount(this.framesDone);
      }));
      Path videoPath = this.settings.getOutputFile().toPath();
      Path glbBasePath = Files.isDirectory(videoPath)
         ? videoPath.resolve("camera.glb")
         : videoPath.resolveSibling(FilenameUtils.getBaseName(videoPath.getFileName().toString()) + ".glb");
      Path glbPath = glbBasePath;

      for (int i = 0; Files.exists(glbPath); i++) {
         String baseName = FilenameUtils.getBaseName(glbBasePath.getFileName().toString());
         glbPath = glbBasePath.resolveSibling(baseName + "." + i + ".glb");
      }

      try (OutputStream out = Files.newOutputStream(glbPath)) {
         GltfAssetV2 asset = new GltfAssetV2(glTF, binaryData);
         new GltfAssetWriterV2().writeBinary(asset, out);
      }
   }
}
