/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.combat;

import dev.zio.client.utils.inventory.InventoryUtils;
import dev.zio.client.utils.network.InteractPacketUtils;
import dev.zio.client.utils.world.BlockInteractionUtils;


import dev.zio.client.ClientAccess;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerPreTickEvent;
import dev.zio.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.utils.ElapsedTimer;
import dev.zio.mixin.MinecraftAccessor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public class LegacyCrystalModule extends Module {
   private final Setting<Integer> delaySetting;
   private final Setting<Boolean> optimizerSetting;
   private final Map<BlockPos, Long> placementTimes;
   private final ElapsedTimer attackTimer;
   private long attackDelayMillis;

   public LegacyCrystalModule() {
      super("LegacyCrystal", "Legit auto crystal (crystal aura).", ModuleCategory.COMBAT, new String[0]);
      this.delaySetting = this.add(new NumberSetting<>("Delay", 150, 0, 250));
      this.optimizerSetting = this.add(new BooleanSetting("Optimizer", false));
      this.placementTimes = new HashMap<>();
      this.attackTimer = new ElapsedTimer();
      this.attackDelayMillis = 150L;
   }

   @EventHandler
   private void onLocalPlayerTick(LocalPlayerPreTickEvent event) {
      synchronized (this.placementTimes) {
         this.placementTimes.entrySet().removeIf(entry -> {
            Long placedAt = entry.getValue();
            long expiryTime = placedAt;
            expiryTime += 15000L;
            return expiryTime < System.currentTimeMillis();
         });
      }

      if (ClientAccess.zioMinecraft.hitResult instanceof EntityHitResult entityHit) {
         Entity entity = entityHit.getEntity();
         long delay = this.attackDelayMillis;

         if (this.attackTimer.hasElapsed(delay) && entity instanceof EndCrystal) {
            if (this.placementTimes.containsKey(entity.blockPosition().below())) {
               ((MinecraftAccessor)ClientAccess.zioMinecraft).attack();
               double randomDelay = Math.random();

               Integer maximumDelay = this.delaySetting.getValue();
               this.attackDelayMillis = (long)(randomDelay * maximumDelay.intValue());

               this.attackTimer.reset();
               return;
            }
         }
      }
   }

   @EventHandler
   private void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
      ClientLevel level = ClientAccess.zioMinecraft.level;
      BlockHitResult hit = event.getHitResult();

      BlockPos hitPosition = hit.getBlockPos();
      BlockPos abovePosition = hitPosition.above(2);

      BlockState aboveState = level.getBlockState(abovePosition);
      if (aboveState.isAir()) {
         BlockHitResult placementHit = event.getHitResult();
         BlockPos placementPosition = placementHit.getBlockPos();
         boolean canPlaceCrystal = BlockInteractionUtils.canPlaceCrystal(placementPosition, false, false, true, false, false, false);

         ItemStack mainHand = ClientAccess.zioMinecraft.player.getMainHandItem();
         DataComponentType<?> weaponComponent = DataComponents.WEAPON;

         if (mainHand.has(weaponComponent)) {
            Item item = canPlaceCrystal ? Items.END_CRYSTAL : Items.OBSIDIAN;

            int slot = InventoryUtils.findHotbarSlot(item);
            InventoryUtils.selectHotbarSlotForPlacement(slot);
         } else {
            ItemStack heldStack = ClientAccess.zioMinecraft.player.getMainHandItem();
            Item obsidian = Items.OBSIDIAN;

            if (heldStack.is(obsidian) && canPlaceCrystal) {
               Item crystal = Items.END_CRYSTAL;
               int slot = InventoryUtils.findHotbarSlot(crystal);
               InventoryUtils.selectHotbarSlot(slot);
            }
         }
      }
   }

   @EventHandler
   private void onPacketSend(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundUseItemOnPacket useItem) {
         LocalPlayer player = ClientAccess.zioMinecraft.player;

         InteractionHand hand = useItem.getHand();

         ItemStack heldStack = player.getItemInHand(hand);
         Item crystal = Items.END_CRYSTAL;

         if (heldStack.is(crystal)) {
            Map<BlockPos, Long> placements = this.placementTimes;
            BlockHitResult hit = useItem.getHitResult();

            BlockPos position = hit.getBlockPos();
            placements.compute(position, (ignoredKey, ignoredValue) -> System.currentTimeMillis());
         }
      }

      if (event.getPacket() instanceof ServerboundAttackPacket attackPacket) {
         // 26.1.2: ATTACK en packet dedicado (antes ServerboundInteractPacket
         // con action byte/record aplanado).
         if (this.optimizerSetting.getValue()) {
            Entity crystal = InteractPacketUtils.getEntity(attackPacket.entityId());
            if (crystal instanceof EndCrystal) {
               crystal.setRemoved(RemovalReason.KILLED);
            }
         }
      }
   }
}
