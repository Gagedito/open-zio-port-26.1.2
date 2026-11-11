/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.utils.math.MathConstants;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.events.RotationRequestEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.managers.SocialRegistry;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.utils.world.BlockInteractionUtils;
import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;

import java.util.function.Predicate;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class SelfWebModule extends Module {
   private final Setting<Boolean> rotateSetting;
   private final Setting<Boolean> autoDisableSetting;
   private final Setting<Boolean> onlyHoleSetting;
   final Setting<Boolean> smartSetting;
   protected final Setting<Float> enemyRangeSetting;
   protected final Setting<Boolean> aggressiveSetting;

   private Entity findNearestEnemy(Setting<Float> rangeSetting) {
      Stream<Entity> enemyStream = StreamSupport.stream(ClientAccess.zioMinecraft.level.entitiesForRendering().spliterator(), false).filter(candidate -> {
         if (candidate == ClientAccess.zioMinecraft.player) {
            return false;
         }

         float distanceToEnemy = ClientAccess.zioMinecraft.player.distanceTo(candidate);
         Float enemyRange = rangeSetting.getValue();
         if (!(distanceToEnemy <= enemyRange)) {
            return false;
         }
         if (!this.meetsHeightPolicy(candidate) || !(candidate instanceof Player)) {
            return false;
         }
         SocialRegistry friendManagerSnapshot = ClientContext.socialRegistry;
         String candidateName = candidate.getName().getString();

         return !friendManagerSnapshot.isFriendName(candidateName) && candidate.isAlive();
      });
      LocalPlayer comparatorPlayerSnapshot = ClientAccess.zioMinecraft.player;
      Optional<Entity> nearestEnemySnapshot = enemyStream.min(Comparator.comparingDouble((ToDoubleFunction<Entity>)comparatorPlayerSnapshot::distanceToSqr));
      return nearestEnemySnapshot.orElse(null);
   }

   @EventHandler
   private void onPlayerUpdate(RotationRequestEvent event) {
      Setting<Float> rangeSettingSnapshot = this.enemyRangeSetting;

      Entity nearestEnemy = this.findNearestEnemy(rangeSettingSnapshot);

      if (!this.smartSetting.getValue() || nearestEnemy != null) {
         this.placeWeb(event);
      }
   }

   private boolean meetsHeightPolicy(Entity candidate) {
      if (this.aggressiveSetting.getValue()) {
         double inclusiveCandidateY = candidate.getY();
         LocalPlayer inclusivePlayer = ClientAccess.zioMinecraft.player;
         return inclusiveCandidateY >= inclusivePlayer.getY();
      } else {
         double strictCandidateY = candidate.getY();
         LocalPlayer strictPlayer = ClientAccess.zioMinecraft.player;
         return strictCandidateY > strictPlayer.getY();
      }
   }

   private void placeWeb(RotationRequestEvent event) {
      if (ClientAccess.zioMinecraft.player.isPassenger()) {
         return;
      }

      if (!ClientContext.holeManager.isInHole()) {
         if (this.onlyHoleSetting.getValue()) {
            return;
         }
      }

      BlockPos feetPosition = BlockPos.containing(ClientAccess.zioMinecraft.player.position());

      BlockState feetState = ClientAccess.zioMinecraft.level.getBlockState(feetPosition);
      if (!feetState.canBeReplaced()) {
         return;
      }
      if (feetState.liquid()) {
         return;
      }

      if (!ClientAccess.zioMinecraft.player.onGround()) {
         return;
      }
      Item webItemSnapshot = Items.COBWEB;

      int webSlot = InventoryUtils.findHotbarSlot(webItemSnapshot);

      int previousSlot = ClientAccess.zioMinecraft.player.getInventory().getSelectedSlot();
      if (webSlot == -1) {
         String moduleName = this.getName();
         MutableComponent messageBaseSnapshot = Component.literal(moduleName);

         MutableComponent outOfBlocksMessage = messageBaseSnapshot.append(" is out of blocks!");

         ChatUtils.printChat(
            outOfBlocksMessage,
            ChatUtils.createSignature(-2),
            ChatUtils.MessagePriority.HIGH
         );
         this.disable();
         return;
      }

      Direction placementDirection = BlockInteractionUtils.findSupportDirection(feetPosition);
      if (placementDirection == null) {
         return;
      }

      InventoryUtils.selectHotbarSlotForPlacement(webSlot);

      AABB previousBounds = ClientAccess.zioMinecraft.player.getBoundingBox();
      LocalPlayer clearBoundsPlayerSnapshot = ClientAccess.zioMinecraft.player;
      AABB emptyBounds = new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
      clearBoundsPlayerSnapshot.setBoundingBox(emptyBounds);
      InteractionHand placementHand = InteractionHand.MAIN_HAND;
      boolean placementFlag = false;
      Direction placementFace = placementDirection;
      BlockInteractionUtils.placeBlockIgnoringCrystals(
         feetPosition, placementFace, placementFlag, placementHand
      );
      AABB restoredBounds = previousBounds;

      ClientAccess.zioMinecraft.player.setBoundingBox(restoredBounds);

      InventoryUtils.selectHotbarSlotForPlacement(previousSlot);

      if (this.rotateSetting.getValue()) {
         RotationManager rotationManagerSnapshot = ClientContext.rotationManager;

         float[] rotations = new float[2];
         rotations[0] = event.getPlayerYaw();
         rotations[1] = MathConstants.NINETY_DEGREES;

         rotationManagerSnapshot.requestRotation(rotations, 5);
      }

      if (this.autoDisableSetting.getValue()) {
         this.disable();
      }
   }

   public SelfWebModule() {
      super("SelfWeb", "Places a cobweb at your feet.", ModuleCategory.COMBAT, new String[0]);
      this.rotateSetting = this.add(new BooleanSetting("Rotate", true));
      this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", true));
      this.onlyHoleSetting = this.add(new BooleanSetting("OnlyHole", false));
      this.smartSetting = this.add(new BooleanSetting("Smart", false).markVisibilityDependency());
      Predicate enemyRangeVisibility = unused -> this.smartSetting.isExpanded();
      this.enemyRangeSetting = this.add(new NumberSetting<>("EnemyRange", 3.0F, 1.0F, 6.0F, enemyRangeVisibility).withUnit("m"));
      this.aggressiveSetting = this.add(new BooleanSetting("Aggressive", false).dependsOn(this.smartSetting));
   }
}
