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
import dev.zio.client.render.LineMeshRenderer;
import dev.zio.client.utils.math.Vec3Utils;
import dev.zio.client.settings.Setting;
import dev.zio.client.settings.options.TracersHitboxChoice;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.Iterator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

public final class LineTracerRenderer extends TracerRenderer {

   public LineTracerRenderer(TracersModule module) {
      super(module);
   }

   @Override
   public void render(PoseStack poseStack, float unusedTickDelta) {
      LineMeshRenderer.begin(poseStack, this.module.lineWidthSetting.getValue());
      ClientLevel level = ClientAccess.zioMinecraft.level;
      Iterator players = level.players().iterator();

      while (players.hasNext()) {
         AbstractClientPlayer player = (AbstractClientPlayer)players.next();

         if (!this.module.shouldSkip(player)) {
            TracersModule geometryModule = this.module;

            TracerEndpoints endpoints = geometryModule.getEndpoints(player);
            Setting<TracersHitboxChoice> hitboxSetting = this.module.hitboxSetting;
            TracersHitboxChoice hitbox = hitboxSetting.getValue();
            float configuredHitboxFraction = hitbox.getHeightFraction();
            Vec3 start = endpoints.start();
            Vec3 capturedEnd = endpoints.end();
            Vec3 hitboxPosition = Vec3Utils.lerp(
               configuredHitboxFraction, start, capturedEnd
            );
            Color color = this.module.getColor(player, hitboxPosition);
            LineMeshRenderer.addTracer(hitboxPosition, color);
            Setting<Boolean> stemSetting = this.module.stemSetting;
            Boolean drawStem = stemSetting.getValue();
            if (drawStem) {

               Vec3 stemStart = endpoints.start();
               LineMeshRenderer.addLine(
                  stemStart, endpoints.end(), color
               );
            }
         }
      }

      LineMeshRenderer.draw();
   }

}
