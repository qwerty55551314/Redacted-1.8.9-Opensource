package com.replaymod.replay.camera;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MathHelper;

public class ClassicCameraController implements CameraController {
   private static final double LOWER_SPEED = 0.2;
   private static final double UPPER_SPEED = 200.0;
   private static final double SPEED_CHANGE = 0.00999;
   private final CameraEntity camera;
   private double MAX_SPEED = 10.0;
   private double THRESHOLD = this.MAX_SPEED / 20.0;
   private double DECAY = this.MAX_SPEED / 3.0;
   private Vector3f direction;
   private Vector3f dirBefore;
   private double motion;
   private long lastCall = System.currentTimeMillis();
   private boolean speedup = false;

   public ClassicCameraController(CameraEntity camera) {
      this.camera = camera;
   }

   @Override
   public void update(float partialTicksPassed) {
      boolean forward = false;
      boolean backward = false;
      boolean left = false;
      boolean right = false;
      boolean up = false;
      boolean down = false;
      this.speedup = false;

      for (KeyBinding kb : Minecraft.getMinecraft().gameSettings.keyBindings) {
         if (kb.isKeyDown()) {
            if (kb.getKeyDescription().equals("key.forward")) {
               forward = true;
               this.speedup = true;
            }

            if (kb.getKeyDescription().equals("key.back")) {
               backward = true;
               this.speedup = true;
            }

            if (kb.getKeyDescription().equals("key.jump")) {
               up = true;
               this.speedup = true;
            }

            if (kb.getKeyDescription().equals("key.left")) {
               left = true;
               this.speedup = true;
            }

            if (kb.getKeyDescription().equals("key.right")) {
               right = true;
               this.speedup = true;
            }

            if (kb.getKeyDescription().equals("key.sneak")) {
               down = true;
               this.speedup = true;
            }
         }
      }

      this.forwardCameraMovement(forward, backward, left, right, up, down);
      this.updateMovement();
   }

   @Override
   public void increaseSpeed() {
      this.setCameraMaximumSpeed(this.MAX_SPEED + 0.00999);
   }

   @Override
   public void decreaseSpeed() {
      this.setCameraMaximumSpeed(this.MAX_SPEED - 0.00999);
   }

   private void setCameraMaximumSpeed(double maxSpeed) {
      if (!(maxSpeed < 0.2) && !(maxSpeed > 200.0)) {
         this.MAX_SPEED = maxSpeed;
         this.THRESHOLD = this.MAX_SPEED / 20.0;
         this.DECAY = 5.0;
      }
   }

   private void forwardCameraMovement(boolean forward, boolean backward, boolean left, boolean right, boolean up, boolean down) {
      if (forward && !backward) {
         this.setMovement(ClassicCameraController.MoveDirection.FORWARD);
      } else if (backward && !forward) {
         this.setMovement(ClassicCameraController.MoveDirection.BACKWARD);
      }

      if (left && !right) {
         this.setMovement(ClassicCameraController.MoveDirection.LEFT);
      } else if (right && !left) {
         this.setMovement(ClassicCameraController.MoveDirection.RIGHT);
      }

      if (up && !down) {
         this.setMovement(ClassicCameraController.MoveDirection.UP);
      } else if (down && !up) {
         this.setMovement(ClassicCameraController.MoveDirection.DOWN);
      }
   }

   private void updateMovement() {
      long frac = System.currentTimeMillis() - this.lastCall;
      if (frac != 0L) {
         double decFac = Math.max(0.0, 1.0 - this.DECAY * (frac / 1000.0));
         if (this.speedup) {
            if (this.motion < this.THRESHOLD) {
               this.motion = this.THRESHOLD;
            }

            this.motion /= decFac;
         } else {
            this.motion *= decFac;
         }

         this.motion = Math.min(this.motion, this.MAX_SPEED);
         this.lastCall = System.currentTimeMillis();
         if (this.direction != null && this.direction.lengthSquared() != 0.0F && !(this.motion < this.THRESHOLD)) {
            Vector3f movement = this.direction.normalise(null);
            double factor = this.motion * (frac / 1000.0);
            this.camera.moveCamera(movement.x * factor, movement.y * factor, movement.z * factor);
         }
      }
   }

   private void setMovement(ClassicCameraController.MoveDirection dir) {
      float rotationPitch = this.camera.rotationPitch;
      float rotationYaw = this.camera.rotationYaw;
      switch (dir) {
         case BACKWARD:
            this.direction = this.getVectorForRotation(-rotationPitch, rotationYaw - 180.0F);
            break;
         case DOWN:
            this.direction = this.getVectorForRotation(90.0F, 0.0F);
            break;
         case FORWARD:
            this.direction = this.getVectorForRotation(rotationPitch, rotationYaw);
            break;
         case LEFT:
            this.direction = this.getVectorForRotation(0.0F, rotationYaw - 90.0F);
            break;
         case RIGHT:
            this.direction = this.getVectorForRotation(0.0F, rotationYaw + 90.0F);
            break;
         case UP:
            this.direction = this.getVectorForRotation(-90.0F, 0.0F);
      }

      Vector3f dbf = this.direction;
      if (this.dirBefore != null) {
         this.dirBefore.normalise(this.dirBefore);
         Vector3f.add(this.direction, this.dirBefore, this.dirBefore);
         this.direction = this.dirBefore;
      }

      this.dirBefore = dbf;
      this.updateMovement();
   }

   private Vector3f getVectorForRotation(float pitch, float yaw) {
      float f2 = MathHelper.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
      float f3 = MathHelper.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
      float f4 = -MathHelper.cos(-pitch * (float) (Math.PI / 180.0));
      float f5 = MathHelper.sin(-pitch * (float) (Math.PI / 180.0));
      return new Vector3f(f3 * f4, f5, f2 * f4);
   }

   public enum MoveDirection {
      UP,
      DOWN,
      LEFT,
      RIGHT,
      FORWARD,
      BACKWARD;
   }
}
