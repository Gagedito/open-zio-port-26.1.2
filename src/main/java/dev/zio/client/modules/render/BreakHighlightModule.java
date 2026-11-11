/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.render;

import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.utils.world.BlockBreakTracker;
import dev.zio.client.render.WorldBoxRenderer;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.utils.world.BlockBreakEntry;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.utils.math.MathUtils;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BreakHighlightModule extends Module {
   private final Setting<Double> rangeSetting;
   private final Setting<Boolean> friendsSetting;
   private final Setting<Boolean> enemyColorSetting;
   private final Setting<Color> fillSetting;
   private final Setting<Color> outlineSetting;

   private AABB scaleBoxFromCenter(AABB box, float progress) {
      float scale = Mth.clamp(progress, 0.0F, 1.0F);
      double signedWidth = box.getXsize();
      double width = Math.abs(signedWidth);
      double height = Math.abs(box.getYsize());
      double depth = Math.abs(box.getZsize());
      Vec3 center = box.getCenter();
      return new AABB(
         center.subtract(scale * width / 2.0D, scale * height / 2.0D, scale * depth / 2.0D),
         center.add(scale * width / 2.0D, scale * height / 2.0D, scale * depth / 2.0D)
      );
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      BlockBreakTracker trackerSnapshot = ClientContext.blockBreakTracker;
      for (BlockBreakEntry entry : trackerSnapshot.getEntries()) {
         BlockPos positionForDistance = entry.getPosition();
         double distance = MathUtils.distanceToBlockCenter(positionForDistance);
         Setting<Double> rangeSetting = this.rangeSetting;
         Double range = rangeSetting.getValue();
         if (distance > range) {
            continue;
         }
         AABB boxForVisibility = new AABB(entry.getPosition());
         if (!RotationManager.isVisible(boxForVisibility)) {
            continue;
         }
         BlockPos positionForMining = entry.getPosition();
         if (!WorldUtils.canMineBlock(positionForMining)) {
            continue;
         }
         Color fillColor = this.fillSetting.getValue();
         Color outlineColor = this.outlineSetting.getValue();
         ClientLevel stateLevel = ClientAccess.zioMinecraft.level;
         BlockPos shapeStatePosition = entry.getPosition();
         BlockState blockState = stateLevel.getBlockState(shapeStatePosition);
         ClientLevel shapeLevel = ClientAccess.zioMinecraft.level;
         BlockPos shapePosition = entry.getPosition();
         VoxelShape shape = blockState.getShape(shapeLevel, shapePosition);
         float partialTickSnapshot = event.getPartialTick();
         float progress = entry.getInterpolatedProgress(partialTickSnapshot);
         Entity breaker = entry.getEntity();
         AABB localBounds;
         if (shape.isEmpty()) {
            localBounds = new AABB(0.0, 0.0, 0.0, 1.0D, 1.0D, 1.0D);
         } else {
            localBounds = shape.bounds();
         }
         AABB box = this.scaleBoxFromCenter(localBounds, progress);
         BlockPos offsetPosition = entry.getPosition();
         box = box.move(offsetPosition);
         if (breaker instanceof Player player) {
            SocialRegistry registrySnapshot = ClientContext.socialRegistry;
            GameProfile profile = player.getGameProfile();
            String playerNameSnapshot = profile.name();
            Color socialColor = registrySnapshot.getColor(
               playerNameSnapshot, null
            );
            Setting<Boolean> friendsSetting = this.friendsSetting;
            Boolean renderFriends = friendsSetting.getValue();
            if (!renderFriends) {
               SocialRegistry friendRegistry = ClientContext.socialRegistry;
               if (friendRegistry.isFriendPlayer(player)) {
                  continue;
               }
            }
            if (socialColor != null) {
               SocialRegistry enemyRegistrySnapshot = ClientContext.socialRegistry;
               GameProfile enemyProfile = player.getGameProfile();
               String enemyNameSnapshot = enemyProfile.name();
               if (!enemyRegistrySnapshot.isEnemyName(enemyNameSnapshot)
                  || this.enemyColorSetting.getValue()) {
                  Color configuredFill = this.fillSetting.getValue();
                  fillColor = ColorUtils.withAlpha(
                     socialColor, configuredFill.getAlpha()
                  );
                  Color configuredOutline = this.outlineSetting.getValue();
                  outlineColor = ColorUtils.withAlpha(
                     socialColor, configuredOutline.getAlpha()
                  );
               }
            }
         }
         PoseStack fillPoseStack = event.getPoseStack();
         WorldBoxRenderer.fillBox(
            fillPoseStack, this.scaleBoxFromCenter(box, progress), fillColor
         );
         PoseStack outlinePoseStack = event.getPoseStack();
         AABB scaledOutlineBox = this.scaleBoxFromCenter(box, progress);
         WorldBoxRenderer.outlineBox(
            outlinePoseStack, scaledOutlineBox, outlineColor,1.0F);
      }
   }

   public BreakHighlightModule() {
      super("BreakHighlight", "Highlights the blocks that are being broken.", ModuleCategory.RENDER, new String[0]);
      this.rangeSetting = this.add(new NumberSetting<>("Range", 10.0D, 1.0D, 25.0D).withUnit("m"));
      this.friendsSetting = this.add(new BooleanSetting("Friends", true));
      this.enemyColorSetting = this.add(new BooleanSetting("EnemyColor", true));
      this.fillSetting = this.add(new ColorSetting("Fill", new Color(279542783, true)));
      this.outlineSetting = this.add(new ColorSetting("Outline", new Color(12425727, true)));
   }
}
