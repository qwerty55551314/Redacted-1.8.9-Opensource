package com.replaymod.replay.camera;

import com.replaymod.core.KeyBindingRegistry;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.SettingsRegistry;
import com.replaymod.core.events.KeyBindingEventCallback;
import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.core.events.PreRenderHandCallback;
import com.replaymod.core.events.SettingsChangedCallback;
import com.replaymod.core.utils.Utils;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.PreTickCallback;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.Setting;
import com.replaymod.replay.events.RenderHotbarCallback;
import com.replaymod.replay.events.RenderSpectatorCrosshairCallback;
import com.replaymod.replay.mixin.EntityPlayerAccessor;
import com.replaymod.replay.mixin.FirstPersonRendererAccessor;
import com.replaymod.replaystudio.util.Location;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.World;

public class CameraEntity extends EntityPlayerSP {
   private static final UUID CAMERA_UUID = UUID.nameUUIDFromBytes("ReplayModCamera".getBytes(StandardCharsets.UTF_8));
   public float roll;
   private CameraController cameraController;
   private long lastControllerUpdate = System.currentTimeMillis();
   private Entity lastHandRendered = null;
   private CameraEntity.EventHandler eventHandler = new CameraEntity.EventHandler();
   private final InventoryPlayer originalInventory = this.inventory;

   public CameraEntity(Minecraft mcIn, World worldIn, NetHandlerPlayClient netHandlerPlayClient, StatFileWriter statisticsManager) {
      super(mcIn, worldIn, netHandlerPlayClient, statisticsManager);
      this.entityUniqueID = CAMERA_UUID;
      this.eventHandler.register();
      if (ReplayModReplay.instance.getReplayHandler().getSpectatedUUID() == null) {
         this.cameraController = ReplayModReplay.instance.createCameraController(this);
      } else {
         this.cameraController = new SpectatorCameraController(this);
      }
   }

   public CameraController getCameraController() {
      return this.cameraController;
   }

   public void setCameraController(CameraController cameraController) {
      this.cameraController = cameraController;
   }

   public void moveCamera(double x, double y, double z) {
      this.setCameraPosition(this.posX + x, this.posY + y, this.posZ + z);
   }

   public void setCameraPosition(double x, double y, double z) {
      this.lastTickPosX = this.prevPosX = x;
      this.lastTickPosY = this.prevPosY = y;
      this.lastTickPosZ = this.prevPosZ = z;
      Entity self = this;
      self.posX = x;
      self.posY = y;
      self.posZ = z;
      this.updateBoundingBox();
   }

   public void setCameraRotation(float yaw, float pitch, float roll) {
      this.prevRotationYaw = yaw;
      this.prevRotationPitch = pitch;
      this.rotationYaw = yaw;
      this.rotationPitch = pitch;
      this.roll = roll;
   }

   public void setCameraPosRot(Location pos) {
      this.setCameraRotation(pos.getYaw(), pos.getPitch(), this.roll);
      this.setCameraPosition(pos.getX(), pos.getY(), pos.getZ());
   }

   public void setCameraPosRot(Entity to) {
      if (to != this) {
         float yOffset = 0.0F;
         this.prevPosX = to.prevPosX;
         this.prevPosY = to.prevPosY + yOffset;
         this.prevPosZ = to.prevPosZ;
         this.prevRotationYaw = to.prevRotationYaw;
         this.prevRotationPitch = to.prevRotationPitch;
         Entity self = this;
         self.posX = to.posX;
         self.posY = to.posY;
         self.posZ = to.posZ;
         this.rotationYaw = to.rotationYaw;
         this.rotationPitch = to.rotationPitch;
         this.lastTickPosX = to.lastTickPosX;
         this.lastTickPosY = to.lastTickPosY + yOffset;
         this.lastTickPosZ = to.lastTickPosZ;
         this.wrapArmYaw();
         this.updateBoundingBox();
      }
   }

   private void updateBoundingBox() {
      this.setEntityBoundingBox(
         new AxisAlignedBB(
            this.posX - this.width / 2.0F,
            this.posY,
            this.posZ - this.width / 2.0F,
            this.posX + this.width / 2.0F,
            this.posY + this.height,
            this.posZ + this.width / 2.0F
         )
      );
   }

   public void onUpdate() {
      Entity view = this.mc.getRenderViewEntity();
      if (view != null) {
         UUID spectating = ReplayModReplay.instance.getReplayHandler().getSpectatedUUID();
         World cameraWorld = this.worldObj;
         World viewWorld = view.worldObj;
         if (spectating != null && (view.getUniqueID() != spectating || viewWorld != cameraWorld) || cameraWorld.getEntityByID(view.getEntityId()) != view) {
            if (spectating == null) {
               ReplayModReplay.instance.getReplayHandler().spectateEntity(this);
               return;
            }

            view = cameraWorld.getPlayerEntityByUUID(spectating);
            if (view == null) {
               this.mc.setRenderViewEntity(this);
               return;
            }

            this.mc.setRenderViewEntity(view);
         }

         if (view != this) {
            this.setCameraPosRot(view);
         }
      }
   }

   public void preparePlayerToSpawn() {
      if (this.mc.theWorld != null) {
         this.worldObj = this.mc.theWorld;
      }

      super.preparePlayerToSpawn();
   }

   public void setRotation(float yaw, float pitch) {
      if (this.mc.getRenderViewEntity() == this) {
         super.setRotation(yaw, pitch);
      }
   }

   public boolean isEntityInsideOpaqueBlock() {
      return this.falseUnlessSpectating(Entity::isEntityInsideOpaqueBlock);
   }

   public boolean isInsideOfMaterial(Material materialIn) {
      return this.falseUnlessSpectating(e -> e.isInsideOfMaterial(materialIn));
   }

   public boolean isInLava() {
      return this.falseUnlessSpectating(Entity::isInLava);
   }

   public boolean isInWater() {
      return this.falseUnlessSpectating(Entity::isInWater);
   }

   public boolean isBurning() {
      return this.falseUnlessSpectating(Entity::isBurning);
   }

   private boolean falseUnlessSpectating(Function<Entity, Boolean> property) {
      Entity view = this.mc.getRenderViewEntity();
      return view != null && view != this ? property.apply(view) : false;
   }

   public boolean canBePushed() {
      return false;
   }

   protected void createRunningParticles() {
   }

   public boolean canBeCollidedWith() {
      return false;
   }

   public boolean isSpectator() {
      ReplayHandler replayHandler = ReplayModReplay.instance.getReplayHandler();
      return replayHandler == null || replayHandler.isCameraView();
   }

   public boolean shouldRenderInPass(int pass) {
      return false;
   }

   public float getFovModifier() {
      Entity view = this.mc.getRenderViewEntity();
      return view != this && view instanceof AbstractClientPlayer ? ((AbstractClientPlayer)view).getFovModifier() : 1.0F;
   }

   public boolean isInvisible() {
      Entity view = this.mc.getRenderViewEntity();
      return view != this ? view.isInvisible() : super.isInvisible();
   }

   public ResourceLocation getLocationSkin() {
      Entity view = this.mc.getRenderViewEntity();
      return view != this && view instanceof AbstractClientPlayer ? ((AbstractClientPlayer)view).getLocationSkin() : super.getLocationSkin();
   }

   public String getSkinType() {
      Entity view = this.mc.getRenderViewEntity();
      return view != this && view instanceof AbstractClientPlayer ? ((AbstractClientPlayer)view).getSkinType() : super.getSkinType();
   }

   public boolean isWearing(EnumPlayerModelParts modelPart) {
      Entity view = this.mc.getRenderViewEntity();
      return view != this && view instanceof EntityPlayer ? ((EntityPlayer)view).isWearing(modelPart) : super.isWearing(modelPart);
   }

   public float getSwingProgress(float renderPartialTicks) {
      Entity view = this.mc.getRenderViewEntity();
      return view != this && view instanceof EntityPlayer ? ((EntityPlayer)view).getSwingProgress(renderPartialTicks) : 0.0F;
   }

   public MovingObjectPosition rayTrace(double p_174822_1_, float p_174822_3_) {
      MovingObjectPosition pos = super.rayTrace(p_174822_1_, 1.0F);
      if (pos != null && pos.typeOfHit == MovingObjectType.BLOCK) {
         pos.typeOfHit = MovingObjectType.MISS;
      }

      return pos;
   }

   public void openGui(Object mod, int modGuiId, World world, int x, int y, int z) {
   }

   public void setDead() {
      super.setDead();
      if (this.eventHandler != null) {
         this.eventHandler.unregister();
         this.eventHandler = null;
      }
   }

   private void update() {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.theWorld != this.worldObj) {
         if (this.eventHandler != null) {
            this.eventHandler.unregister();
            this.eventHandler = null;
         }
      } else {
         long now = System.currentTimeMillis();
         long timePassed = now - this.lastControllerUpdate;
         this.cameraController.update((float)timePassed / 50.0F);
         this.lastControllerUpdate = now;
         this.handleInputEvents();
         Map<String, KeyBindingRegistry.Binding> keyBindings = ReplayMod.instance.getKeyBindingRegistry().getBindings();
         if (keyBindings.get("replaymod.input.rollclockwise").keyBinding.isKeyDown()) {
            this.roll = (float)(this.roll + (Utils.isCtrlDown() ? 0.2 : 1.0));
         }

         if (keyBindings.get("replaymod.input.rollcounterclockwise").keyBinding.isKeyDown()) {
            this.roll = (float)(this.roll - (Utils.isCtrlDown() ? 0.2 : 1.0));
         }

         this.noClip = this.isSpectator();
         this.syncInventory();
      }
   }

   private void syncInventory() {
      Entity view = this.mc.getRenderViewEntity();
      EntityPlayer viewPlayer = view != this && view instanceof EntityPlayer ? (EntityPlayer)view : null;
      EntityPlayerAccessor cameraA = (EntityPlayerAccessor)this;
      EntityPlayerAccessor viewPlayerA = (EntityPlayerAccessor)viewPlayer;
      ItemStack empty = null;
      this.inventory = viewPlayer != null ? viewPlayer.inventory : this.originalInventory;
      cameraA.setItemInUse(viewPlayer != null ? viewPlayer.getItemInUse() : empty);
      cameraA.setItemInUseCount(viewPlayer != null ? viewPlayer.getItemInUseCount() : 0);
   }

   private void handleInputEvents() {
      if ((this.mc.gameSettings.keyBindAttack.isPressed() || this.mc.gameSettings.keyBindUseItem.isPressed())
         && this.mc.currentScreen == null
         && this.canSpectate(this.mc.pointedEntity)) {
         ReplayModReplay.instance.getReplayHandler().spectateEntity(this.mc.pointedEntity);

         while (this.mc.gameSettings.keyBindSneak.isPressed()) {
         }
      }
   }

   private void updateArmYawAndPitch() {
      this.prevRenderArmYaw = this.renderArmYaw;
      this.prevRenderArmPitch = this.renderArmPitch;
      this.renderArmPitch = this.renderArmPitch + (this.rotationPitch - this.renderArmPitch) * 0.5F;
      this.renderArmYaw = this.renderArmYaw + wrapDegrees(this.rotationYaw - this.renderArmYaw) * 0.5F;
      this.wrapArmYaw();
   }

   private void wrapArmYaw() {
      this.renderArmYaw = wrapDegreesTo(this.renderArmYaw, this.rotationYaw);
      this.prevRenderArmYaw = wrapDegreesTo(this.prevRenderArmYaw, this.renderArmYaw);
   }

   private static float wrapDegreesTo(float value, float towardsValue) {
      while (towardsValue - value < -180.0F) {
         value -= 360.0F;
      }

      while (towardsValue - value >= 180.0F) {
         value += 360.0F;
      }

      return value;
   }

   private static float wrapDegrees(float value) {
      value %= 360.0F;
      return wrapDegreesTo(value, 0.0F);
   }

   public boolean canSpectate(Entity e) {
      return e != null && !e.isInvisible();
   }

   public void addChatMessage(IChatComponent message) {
      super.addChatMessage(message);
   }

   private class EventHandler extends EventRegistrations {
      private final Minecraft mc = MCVer.getMinecraft();

      private EventHandler() {
         this.on(PreTickCallback.EVENT, this::onPreClientTick);
         this.on(PreRenderCallback.EVENT, this::onRenderUpdate);
         this.on(KeyBindingEventCallback.EVENT, (KeyBindingEventCallback)() -> CameraEntity.this.handleInputEvents());
         this.on(RenderSpectatorCrosshairCallback.EVENT, this::shouldRenderSpectatorCrosshair);
         this.on(RenderHotbarCallback.EVENT, this::shouldRenderHotbar);
         this.on(SettingsChangedCallback.EVENT, this::onSettingsChanged);
         this.on(PreRenderHandCallback.EVENT, this::onRenderHand);
      }

      private void onPreClientTick() {
         CameraEntity.this.updateArmYawAndPitch();
      }

      private void onRenderUpdate() {
         CameraEntity.this.update();
      }

      private Boolean shouldRenderSpectatorCrosshair() {
         return CameraEntity.this.canSpectate(this.mc.pointedEntity);
      }

      private Boolean shouldRenderHotbar() {
         return false;
      }

      private void onSettingsChanged(SettingsRegistry registry, SettingsRegistry.SettingKey<?> key) {
         if (key == Setting.CAMERA) {
            if (ReplayModReplay.instance.getReplayHandler().getSpectatedUUID() == null) {
               CameraEntity.this.cameraController = ReplayModReplay.instance.createCameraController(CameraEntity.this);
            } else {
               CameraEntity.this.cameraController = new SpectatorCameraController(CameraEntity.this);
            }
         }
      }

      private boolean onRenderHand() {
         Entity view = this.mc.getRenderViewEntity();
         if (view != CameraEntity.this && view instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)view;
            if (CameraEntity.this.lastHandRendered != player) {
               CameraEntity.this.lastHandRendered = player;
               FirstPersonRendererAccessor acc = (FirstPersonRendererAccessor)this.mc.entityRenderer.itemRenderer;
               acc.setPrevEquippedProgress(1.0F);
               acc.setEquippedProgress(1.0F);
               acc.setItemToRender(player.inventory.getCurrentItem());
               acc.setEquippedItemSlot(player.inventory.currentItem);
               this.mc.thePlayer.renderArmYaw = this.mc.thePlayer.prevRenderArmYaw = player.rotationYaw;
               this.mc.thePlayer.renderArmPitch = this.mc.thePlayer.prevRenderArmPitch = player.rotationPitch;
            }

            return false;
         } else {
            return true;
         }
      }

   }
}
