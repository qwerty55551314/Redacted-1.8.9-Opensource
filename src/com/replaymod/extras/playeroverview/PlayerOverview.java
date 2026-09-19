package com.replaymod.extras.playeroverview;

import com.google.common.base.Predicate;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.events.PreRenderHandCallback;
import com.replaymod.core.utils.Utils;
import com.replaymod.extras.Extra;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.replay.events.ReplayClosedCallback;
import com.replaymod.replay.events.ReplayOpenedCallback;
import com.replaymod.replaystudio.lib.guava.base.Optional;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class PlayerOverview extends EventRegistrations implements Extra {
   private ReplayModReplay module;
   private final Set<UUID> hiddenPlayers = new HashSet<>();
   private boolean savingEnabled;

   public PlayerOverview() {
      this.on(ReplayOpenedCallback.EVENT, this::onReplayOpen);
      this.on(ReplayClosedCallback.EVENT, this::onReplayClose);
      this.on(PreRenderHandCallback.EVENT, this::shouldHideHand);
   }

   @Override
   public void register(ReplayMod mod) throws Exception {
      this.module = ReplayModReplay.instance;
      mod.getKeyBindingRegistry().registerKeyBinding("replaymod.input.playeroverview", 48, new Runnable() {
         @Override
         public void run() {
            if (PlayerOverview.this.module.getReplayHandler() != null) {
               List<EntityPlayer> players = mod.getMinecraft().theWorld.getPlayers(EntityPlayer.class, new Predicate() {
                  public boolean apply(Object input) {
                     return !(input instanceof CameraEntity);
                  }
               });
               if (!Utils.isCtrlDown()) {
                  Iterator<EntityPlayer> iter = players.iterator();

                  while (iter.hasNext()) {
                     UUID uuid = iter.next().getGameProfile().getId();
                     if (uuid != null && uuid.version() == 2) {
                        iter.remove();
                     }
                  }
               }

               new PlayerOverviewGui(PlayerOverview.this, players).display();
            }
         }
      }, true);
      this.register();
   }

   public boolean isHidden(UUID uuid) {
      return this.hiddenPlayers.contains(uuid);
   }

   public void setHidden(UUID uuid, boolean hidden) {
      if (hidden) {
         this.hiddenPlayers.add(uuid);
      } else {
         this.hiddenPlayers.remove(uuid);
      }
   }

   private void onReplayOpen(ReplayHandler replayHandler) throws IOException {
      Optional<Set<UUID>> savedData = replayHandler.getReplayFile().getInvisiblePlayers();
      if (savedData.isPresent()) {
         this.hiddenPlayers.addAll(savedData.get());
         this.savingEnabled = true;
      } else {
         this.savingEnabled = false;
      }
   }

   private void onReplayClose(ReplayHandler replayHandler) {
      this.hiddenPlayers.clear();
   }

   private boolean shouldHideHand() {
      Entity view = this.module.getCore().getMinecraft().getRenderViewEntity();
      return view != null && this.isHidden(view.getUniqueID());
   }

   public boolean isSavingEnabled() {
      return this.savingEnabled;
   }

   public void setSavingEnabled(boolean savingEnabled) {
      this.savingEnabled = savingEnabled;
   }

   public void saveHiddenPlayers() {
      if (this.savingEnabled) {
         try {
            this.module.getReplayHandler().getReplayFile().writeInvisiblePlayers(this.hiddenPlayers);
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
   }
}
