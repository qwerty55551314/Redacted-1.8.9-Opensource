package com.replaymod.core.utils;

import com.replaymod.replaystudio.data.ModInfo;
import java.util.Collection;
import java.util.Collections;

class ModInfoGetter {
   static Collection<ModInfo> getInstalledNetworkMods() {
      /* 
       * Without Forge there are no mods to enumerate; the recording simply carries no mod list.
       */
      return Collections.emptyList();
   }
}
