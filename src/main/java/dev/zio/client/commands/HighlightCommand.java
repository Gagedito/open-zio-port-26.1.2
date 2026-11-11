/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.commands;

import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.bus.EventBus;
import dev.zio.client.render.WorldBoxRenderer;
import dev.zio.client.utils.math.Vec3Utils;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.render.FeatureBoxRenderer;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.utils.math.MathUtils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.awt.Color;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class HighlightCommand extends Command {
   private static final ColorSetting FILL_SETTING;
   private static final Color TRANSPARENT_OUTLINE;
   private Vec3 trackedPosition;

   static {

      FILL_SETTING = new ColorSetting("Fill", Color.WHITE);
      TRANSPARENT_OUTLINE = new Color(0, 0, 0, 0);

      boolean synced = true;
      ColorSetting fillSetting = FILL_SETTING;
      fillSetting.setSynced(synced);
   }

   @Override
   public void exec(LiteralArgumentBuilder root) {

      PositionArgument positionParser = new PositionArgument();
      RequiredArgumentBuilder positionArgument = Command.argument("pos", positionParser);

      LiteralArgumentBuilder trackBuilder = Command.literal("track");
      com.mojang.brigadier.Command<Object> trackCallback = context -> {

         Class<Vec3> positionType = Vec3.class;
         String positionName = "pos";
         CommandContext argumentContext = context;
         Vec3 position = (Vec3)(argumentContext.getArgument(positionName, positionType));
         this.trackedPosition = position;
         HighlightCommand command = this;
         EventBus eventBus = ClientAccess.eventBus;
         eventBus.subscribe(command);
         return 1;
      };
      ArgumentBuilder trackBranch = trackBuilder.executes(trackCallback);
      RequiredArgumentBuilder highlightBuilder = (RequiredArgumentBuilder)positionArgument.then(trackBranch);
      com.mojang.brigadier.Command<Object> highlightCallback = context -> {

         Class<Vec3> positionType = Vec3.class;
         String positionName = "pos";
         CommandContext argumentContext = context;
         Position argumentPosition = (Position)argumentContext.getArgument(positionName, positionType);
         BlockPos blockPosition = BlockPos.containing(argumentPosition);
         Vec3 blockCenter = blockPosition.getCenter();

         AABB box = AABB.ofSize(blockCenter, 0.5D, 0.5D, 0.5D);
         AABB pointBox = box;
         HighlightCommand pointFeature = this;
         FeatureBoxRenderer pointRenderer = ClientContext.featureBoxRenderer;
         pointRenderer.addBox(pointFeature, pointBox);

         AABB columnBounds = AABB.ofSize(blockCenter, 0.25D, 0.25D, 0.25D);

         double topY = WorldUtils.getTopY();
         columnBounds = columnBounds.setMaxY(topY);
         LevelReader level = ClientAccess.zioMinecraft.level;
         double minY = level.getMinY();
         box = columnBounds.setMinY(minY);
         AABB columnBox = box;
         HighlightCommand columnFeature = this;
         FeatureBoxRenderer columnRenderer = ClientContext.featureBoxRenderer;
         columnRenderer.addBox(columnFeature, columnBox);
         return 1;
      };
      ArgumentBuilder highlightBranch = highlightBuilder.executes(highlightCallback);
      root.then(highlightBranch);
   }

   @EventHandler
   public void onWorldRender(WorldRenderEvent.Main event) {
      if (this.trackedPosition != null) {
         GameRenderer gameRenderer = ClientAccess.zioMinecraft.gameRenderer;
         Camera camera = gameRenderer.getMainCamera();
         Vec3 cameraPosition = camera.position();
         Vec3 targetPosition = this.trackedPosition;

         float distance = Vec3Utils.horizontalDistance((cameraPosition), (targetPosition));

         if (distance <= 2.0F) {
            this.trackedPosition = null;
            HighlightCommand command = this;
            EventBus eventBus = ClientAccess.eventBus;
            eventBus.unsubscribe(command);
         } else {
            Vec3 renderPosition = this.trackedPosition;

            if (distance >= 1.2e+02F) {
               targetPosition = this.trackedPosition;

               Vec3 rotationTarget = targetPosition;
               Vec3 rotationOrigin = cameraPosition;
               float[] rotations = RotationManager.calculateRotations(
                  rotationOrigin, rotationTarget
               );
               double cameraX = cameraPosition.x();

               float xRadius = 1e+02F;
               double xYaw = rotations[0] + MathConstants.NINETY_DEGREES;
               double xYawRadians = Math.toRadians(xYaw);
               double renderX = cameraX + xRadius * (float)Math.cos(xYawRadians);
               double renderY = this.trackedPosition.y;
               double cameraZ = cameraPosition.z();

               double zYaw = rotations[0] + MathConstants.NINETY_DEGREES;
               double zYawRadians = Math.toRadians(zYaw);
               renderPosition = new Vec3(renderX, renderY, cameraZ + (1e+02F) * (float)Math.sin(zYawRadians));
            }

            Object[] labelArguments = new Object[1];
            labelArguments[0] = distance;
            String distanceLabel = ("[%.0fm]").formatted((labelArguments));

            float fadeAmount = distance - 2.0F;

            fadeAmount = Mth.clamp(fadeAmount, 0.0F, (9.0F));

            float opacity = fadeAmount / 9.0F;

            AABB column = AABB.ofSize(renderPosition, 0.25D, 0.25D, 0.25D);

            double topY = WorldUtils.getTopY();
            AABB topBoundedColumn = column.setMaxY(topY);
            LevelReader level = ClientAccess.zioMinecraft.level;
            double minY = level.getMinY();
            column = topBoundedColumn.setMinY(minY);
            ColorSetting colorSetting = FILL_SETTING;
            Color configuredFillColor = colorSetting.getColor();

            Color fillColor = ColorUtils.withAlpha(
               configuredFillColor, opacity * 0.3F
            );
            Color labelColor = ColorUtils.withAlpha(Color.white, opacity);
            WorldRenderEvent.Main boxRenderEvent = event;
            WorldBoxRenderer.fillBox(
               boxRenderEvent.getPoseStack(), column, fillColor
            );
            RenderUtils labelRenderer = RenderUtils.INSTANCE;
            PoseStack poseStack = event.getPoseStack();

            Vec3 labelPosition = renderPosition;
            labelRenderer.drawWorldText(
               poseStack,
               distanceLabel,
               renderPosition,
               MathUtils.getDistanceScale(labelPosition, 1.7000000476837158D),
               labelColor,
               true
            );
         }
      }
   }

   public HighlightCommand() {
      super("highlight");
      FeatureBoxRenderer capturedRenderer = ClientContext.featureBoxRenderer;
      FeatureBoxRenderer.RenderConfiguration configuration = new FeatureBoxRenderer.RenderConfiguration(
         this,
         () -> {

            ColorSetting fillSetting = FILL_SETTING;
            Color fillColor = fillSetting.getColor();

            return ColorUtils.withAlpha(
               fillColor, 0.5F
            );
         },
         () -> TRANSPARENT_OUTLINE,
         () -> {

            return (1.0F);
         },
         () -> {

            return (5.0F);
         },
         () -> {
            return true;
         },
         () -> {
            return true;
         },
         configurationFadeTimeScale()
      );
      capturedRenderer.register(configuration);
   }

   private static int configurationFadeTimeScale() {

      return 1000;
   }

}
