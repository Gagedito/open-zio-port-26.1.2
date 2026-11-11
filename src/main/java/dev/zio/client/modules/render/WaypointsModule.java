/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.render;


import java.util.function.Predicate;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.render.LineMeshRenderer;
import dev.zio.client.render.WorldBoxRenderer;
import dev.zio.client.utils.world.Waypoint;
import dev.zio.client.managers.WaypointRegistry;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.settings.SettingLabelCondition;
import dev.zio.client.utils.world.WorldUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.ColorSetting;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.utils.math.MathUtils;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WaypointsModule extends Module {
   private final Setting<Float> distanceSetting;
   private final Setting<Float> lineWidthSetting;
   private final Setting<Boolean> boxSetting;
   private final Setting<Boolean> beamSetting;
   private final Setting<Boolean> tracersSetting;
   final Setting<Boolean> nameSetting;
   private final Setting<InfoChoice> infoSetting;
   private final Setting<Float> textScaleSetting;
   private final Setting<Boolean> textBackgroundSetting;
   private final Setting<Boolean> eyesAlignSetting;
   private final Setting<Color> colorSetting;
   private final Setting<Color> textSetting;
   private final Setting<Integer> beamAlphaSetting;
   private final CopyOnWriteArrayList<Waypoint> visibleWaypoints;

   private static final int DISTANCE_UNITS_PER_KILOMETER = 1_000;
   private static final double FAR_WAYPOINT_DISTANCE = 120.0D;
   private static final double FAR_WAYPOINT_RADIUS = 100.0D;
   private static final double WAYPOINT_HALF_SIZE = 0.25D;
   private static final int TEXT_BACKGROUND_ALPHA = 100;

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      LocalPlayer player = ClientAccess.zioMinecraft.player;
      if (player.tickCount % 2 == 0) {
         return;
      }

      WaypointRegistry registry = ClientContext.waypointRegistry;
      List<Waypoint> registeredWaypoints = (List<Waypoint>)registry.getRegistry();
      List<Waypoint> invalidWaypoints = new ArrayList<>();
      for (Waypoint waypoint : registeredWaypoints) {
         if (waypoint != null && (waypoint.getServer() == null
            || waypoint.getDimension() == null || waypoint.getName() == null)) {
            invalidWaypoints.add(waypoint);
         }
      }
      for (Waypoint invalidWaypoint : invalidWaypoints) {
         registry.unregisterWaypoint(invalidWaypoint);
      }

      this.visibleWaypoints.clear();
      Minecraft minecraft = ClientAccess.zioMinecraft;
      String currentServer = minecraft.getCurrentServer() == null
         ? null
         : minecraft.getCurrentServer().ip;
      for (Waypoint waypoint : registeredWaypoints) {
         if (waypoint == null || !waypoint.isToggled()) {
            continue;
         }

         String waypointServer = waypoint.getServer();
         boolean serverMatches = currentServer == null
            ? waypointServer.equalsIgnoreCase("singleplayer")
            : waypointServer.equalsIgnoreCase(currentServer);
         if (serverMatches) {
            this.visibleWaypoints.add(waypoint);
         }
      }

      this.visibleWaypoints.sort(Comparator.comparingDouble(
         waypoint -> player.distanceToSqr(waypoint.getPosition())
      ));
   }

   public static Vec3 getPositionInCurrentDimension(Waypoint waypoint) {
      Vec3 position = waypoint.getPosition();
      String currentDimension = WorldUtils.getDimension().getDisplayName().toLowerCase(Locale.ROOT);
      String waypointDimension = waypoint.getDimension();

      if (waypointDimension.equals("overworld") && currentDimension.equals("nether")) {
         return position.multiply(0.125D, 1.0D, 0.125D);
      }
      if (waypointDimension.equals("nether") && currentDimension.equals("overworld")) {
         return position.multiply(8.0D, 1.0D, 8.0D);
      }
      return waypointDimension.equals(currentDimension) ? position : null;
   }

   private Vec3 getRenderPosition(Vec3 cameraPosition, Vec3 waypointPosition) {
      double dx = waypointPosition.x - cameraPosition.x;
      double dz = waypointPosition.z - cameraPosition.z;
      double horizontalDistance = Math.hypot(dx, dz);
      if (horizontalDistance < FAR_WAYPOINT_DISTANCE) {
         return waypointPosition;
      }

      double distanceScale = FAR_WAYPOINT_RADIUS / horizontalDistance;
      double x = cameraPosition.x + dx * distanceScale;
      double y = Mth.lerp(distanceScale, cameraPosition.y, waypointPosition.y);
      double z = cameraPosition.z + dz * distanceScale;
      return new Vec3(x, y, z);
   }

   private void renderWaypoint(PoseStack poseStack, Vec3 cameraPosition, Waypoint waypoint) {
      Vec3 waypointPosition = getPositionInCurrentDimension(waypoint);
      if (waypointPosition == null) {
         return;
      }

      float maximumDistance = this.distanceSetting.getValue();
      if (!this.distanceSetting.isAtMaximum()
         && waypointPosition.distanceTo(cameraPosition) > maximumDistance * DISTANCE_UNITS_PER_KILOMETER) {
         return;
      }

      Vec3 renderPosition = this.getRenderPosition(cameraPosition, waypointPosition);
      if (this.beamSetting.getValue()) {
         AABB beamBounds = around(renderPosition, WAYPOINT_HALF_SIZE);
         double worldTop = WorldUtils.getTopY();
         LevelReader world = ClientAccess.zioMinecraft.level;
         beamBounds = beamBounds.setMaxY(worldTop).setMinY(world.getMinY());
         Color beamColor = this.colorSetting.getValue();
         int beamAlpha = this.beamAlphaSetting.getValue();
         WorldBoxRenderer.fillBox(poseStack, beamBounds, ColorUtils.withAlpha(beamColor, beamAlpha));
      }

      if (this.boxSetting.getValue()) {
         AABB box = around(waypointPosition, WAYPOINT_HALF_SIZE);
         Color color = this.colorSetting.getValue();
         float lineWidth = this.lineWidthSetting.getValue();
         WorldBoxRenderer.outlineBox(poseStack, box, color, lineWidth);
      }

      if (this.tracersSetting.getValue()) {
         LineMeshRenderer.addTracer(renderPosition, this.colorSetting.getValue());
      }

      if (this.nameSetting.getValue()) {
         this.renderWaypointName(poseStack, cameraPosition, renderPosition, waypointPosition, waypoint);
      }
   }

   private void renderWaypointName(
      PoseStack poseStack, Vec3 cameraPosition, Vec3 renderPosition,
      Vec3 waypointPosition, Waypoint waypoint
   ) {
      if (this.eyesAlignSetting.getValue()) {
         renderPosition = renderPosition.with(Axis.Y, cameraPosition.y);
      }

      String label = waypoint.getName() + " "
         + (this.infoSetting.getValue()).formatInfo(waypointPosition);
      double textScale = MathUtils.getDistanceScale(
         cameraPosition, renderPosition, this.textScaleSetting.getValue()
      );
      float textWidth = FontRenderer.INSTANCE.getWidth(label);

      if (this.textBackgroundSetting.getValue()) {
         float textHeight = FontRenderer.INSTANCE.getHeight() + 1.0F;
         RenderUtils.INSTANCE.drawWorldRect(
            poseStack, renderPosition, -0.03F, (float)WAYPOINT_HALF_SIZE,
            -textWidth, textHeight, textScale, new Color(0, 0, 0, TEXT_BACKGROUND_ALPHA)
         );
      }

      RenderUtils.INSTANCE.drawWorldText(
         poseStack, label, renderPosition, 0.0F, 0.23F, -textWidth / 2.0F,
         0.0F, textScale, this.textSetting.getValue(), true
      );
   }

   private static AABB around(Vec3 position, double halfSize) {
      return new AABB(
         position.add(-halfSize, -halfSize, -halfSize),
         position.add(halfSize, halfSize, halfSize)
      );
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      if (ClientAccess.zioMinecraft.options.hideGui) {
         return;
      }

      PoseStack poseStack = event.getPoseStack();
      Vec3 cameraPosition = ClientAccess.zioMinecraft.gameRenderer.getMainCamera().position();
      LineMeshRenderer.begin(event, this.lineWidthSetting.getValue());
      for (Waypoint waypoint : this.visibleWaypoints) {
         if (waypoint != null && waypoint.getDimension() != null
            && waypoint.getName() != null && waypoint.getServer() != null) {
            this.renderWaypoint(poseStack, cameraPosition, waypoint);
         }
      }
      LineMeshRenderer.draw();
   }

   public WaypointsModule() {
      super("Waypoints", "Renders certain places you mark using ;waypoints.", ModuleCategory.RENDER, new String[0]);
      this.distanceSetting = this.add(new NumberSetting<>("Distance", 101.0F, 0.1F, 101.0F).withUnit("k"));
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F));
      this.boxSetting = this.add(new BooleanSetting("Box", true));
      this.beamSetting = this.add(new BooleanSetting("Beam", false));
      this.tracersSetting = this.add(new BooleanSetting("Tracers", false));
      this.nameSetting = this.add(new BooleanSetting("Name", true));
      Predicate<Object> infoVisibility = ignored -> this.nameSetting.getValue();
      this.infoSetting = this.add(new EnumSetting<>("Info", WaypointsModule.InfoChoice.NONE, infoVisibility));
      this.textScaleSetting = this.add(new NumberSetting<>("TextScale", 1.0F, 0.5F, 3.0F, (ignored -> this.nameSetting.getValue())));
      this.textBackgroundSetting = this.add(new BooleanSetting("TextBackground", true, (ignored -> this.nameSetting.getValue())));
      this.eyesAlignSetting = this.add(new BooleanSetting("EyesAlign", false).dependsOn(this.nameSetting));
      this.colorSetting = this.add(new ColorSetting("Color", new Color(-457336321, true)));
      this.textSetting = this.add(new ColorSetting("Text", new Color(-1, true)));
      this.beamAlphaSetting = this.add(new NumberSetting<>("BeamAlpha", 40, 0, 255));
      this.visibleWaypoints = new CopyOnWriteArrayList<>();
      Setting<Float> distanceSettingForLabel = this.distanceSetting;

      SettingLabelCondition unlimitedBoundary = SettingLabelCondition.MAX;
      distanceSettingForLabel.setBoundaryLabel("Unlimited", unlimitedBoundary);

      this.setDrawn(false);
   }

   private enum InfoChoice implements NamedEntry {
      NONE("None"),
      COORDS("Coords") {
         @Override
         public String formatInfo(Vec3 position) {
            Locale locale = Locale.US;
            return String.format(locale, "X: %.1f, Y: %.1f, Z: %.1f", position.x(), position.y(), position.z());
         }
      },
      DISTANCE("Distance") {
         @Override
         public String formatInfo(Vec3 position) {
            return String.format("%.1fm", position.distanceTo(ClientAccess.zioMinecraft.player.position()));
         }
      };

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      InfoChoice(String displayName) {
         this.displayName = displayName;
      }

      public String formatInfo(Vec3 position) {
         return "";
      }

   }
}
