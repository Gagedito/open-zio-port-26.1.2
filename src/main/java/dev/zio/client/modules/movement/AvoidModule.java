/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.movement;


import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.AbstractClientPlayerTickEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.managers.TickRateController;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class AvoidModule extends Module {
   private final Setting<Boolean> unloadSetting;
   private final Setting<Float> timerSetting;
   private final Setting<Boolean> voidSetting;
   private final Setting<ModeChoice> modeSetting;
   private final Setting<Integer> heightSetting;
   @Override
   public String getInfo() {
      if (!this.voidSetting.getValue()) {
         return null;
      }
      return FontRenderer.capitalize(this.modeSetting.getValue());
   }

   @Override
   public void onDisable() {
      ClientContext.tickRateController.releaseMultiplier(this);
   }

   @EventHandler
   private void onClientPlayerTick(AbstractClientPlayerTickEvent event) {
      if (this.unloadSetting.getValue() && !this.areNearbyChunksLoaded()) {
         TickRateController timer = ClientContext.tickRateController;
         Float multiplier = this.timerSetting.getValue();
         timer.requestMultiplier(this, multiplier);
      } else {
         TickRateController timer = ClientContext.tickRateController;
         timer.releaseMultiplier(this);
      }
   }

   private boolean areNearbyChunksLoaded() {
      ClientChunkCache chunks = ClientAccess.zioMinecraft.level.getChunkSource();
      ChunkPos playerChunk = ClientAccess.zioMinecraft.player.chunkPosition();
      for (int x = -1; x <= 1; x++) {
         for (int z = -1; z <= 1; z++) {
            if (!chunks.hasChunk(playerChunk.x() + x, playerChunk.z() + z)) {
               return false;
            }
         }
      }
      return true;
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerTickEvent event) {
      if (this.voidSetting.getValue()) {
         this.preventVoidFall();
      }
   }

   private void preventVoidFall() {
      if (ClientAccess.zioMinecraft.player.onGround()) {
         return;
      }
      if (PlayerStateUtils.getTrackedFallDistance() <= 0.0F) {
         return;
      }
      ClientLevel rayLevel = ClientAccess.zioMinecraft.level;
      ClipBlockStateContext rayContext = new ClipBlockStateContext(
         ClientAccess.zioMinecraft.player.position(),
         new Vec3(ClientAccess.zioMinecraft.player.getX(), ClientAccess.zioMinecraft.level.getMinY() - 1, ClientAccess.zioMinecraft.player.getZ()),
         state -> state.isSolid()
      );
      if (rayLevel.isBlockInLine(rayContext).getType() != Type.MISS) {
         return;
      }
      int minimumY = ClientAccess.zioMinecraft.level.getMinY();
      Integer configuredHeight = this.heightSetting.getValue();
      double triggerY = minimumY + configuredHeight;
      if (triggerY < ClientAccess.zioMinecraft.player.getY()) {
         return;
      }
      if (this.modeSetting.getValue() == AvoidModule.ModeChoice.TELEPORT) {
         ClientAccess.zioMinecraft.player.setDeltaMovement(0.0, 10.0D, 0.0);
      } else {
         LocalPlayer stopPlayer = ClientAccess.zioMinecraft.player;
         Vec3 stoppedVelocity = stopPlayer.getDeltaMovement().with(Axis.Y, 0.0);
         stopPlayer.setDeltaMovement(stoppedVelocity);
         ClientAccess.zioMinecraft.player.setOnGround(true);
      }
      return;
   }
   public AvoidModule() {
      super("Avoid", "Prevents you from getting into dangerous situations.", ModuleCategory.MOVEMENT, "AntiVoid");
      this.unloadSetting = this.add(new BooleanSetting("Unload", false).markVisibilityDependency());
      this.timerSetting = this.add(new NumberSetting<>("Timer", 0.25F, 0.1F, 0.5F).dependsOn(this.unloadSetting));
      this.voidSetting = this.add(new BooleanSetting("Void", true).markVisibilityDependency());
      this.modeSetting = this.add(new EnumSetting<>("Mode", AvoidModule.ModeChoice.CANCEL).dependsOn(this.voidSetting));
      this.heightSetting = this.add(new NumberSetting<>("Height", 30, 1, 100).withUnit("m").dependsOn(this.voidSetting));
      this.setDrawn(false);
   }
   public enum ModeChoice implements NamedEntry {
      TELEPORT("Teleport"),
      CANCEL("Cancel");
      private final String displayName;
      @Override
      public String getName() {
         return this.displayName;
      }
      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
