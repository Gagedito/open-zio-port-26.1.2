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
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.LocalPlayerTickEvent;
import dev.zio.client.events.PlayerIsStayingOnGroundSurfaceEvent;
import dev.zio.client.utils.entity.EntityUtils;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.NumberSetting;
import dev.zio.client.settings.Setting;
import dev.zio.client.utils.ElapsedTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class SafeWalkModule extends Module {
    private final Setting<Boolean> sneakSetting;
    private final Setting<Integer> delaySetting;
    private final ElapsedTimer edgeDelayTimer;

    public SafeWalkModule() {
        super("SafeWalk", "Helps not to fall from blocks if you're bad.", ModuleCategory.MOVEMENT, new String[0]);
        BooleanSetting sneak = new BooleanSetting("Sneak", false);
        sneak.markVisibilityDependency();
        sneakSetting = add(sneak);

        NumberSetting<Integer> delay = new NumberSetting<>("Delay", 0, 0, 1000);
        delay.withUnit("ms");
        delaySetting = add(delay.dependsOn(sneakSetting));
        edgeDelayTimer = new ElapsedTimer();
    }

    @Override
    public void onDisable() {
        if (sneakSetting.getValue()) ClientAccess.zioMinecraft.options.keyShift.setDown(false);
    }

    @EventHandler
    private void onPlayerTick(LocalPlayerTickEvent event) {
        if (!sneakSetting.getValue()) return;


        BlockPos below = BlockPos.containing(ClientAccess.zioMinecraft.player.position().add(0.0, -0.1F, 0.0));
        BlockState block = ClientAccess.zioMinecraft.level.getBlockState(below);
        if (shouldSneakAtEdge(block)) {
            edgeDelayTimer.reset();
            ClientContext.inputOverrides.requestSneak(true);
        } else if (!edgeDelayTimer.hasElapsed((delaySetting.getValue()).longValue())) {
            ClientContext.inputOverrides.requestSneak(true);
        }
    }

    @EventHandler
    private void onGroundSurfaceQuery(PlayerIsStayingOnGroundSurfaceEvent event) {
        if (!ClientAccess.zioMinecraft.player.isSwimming()
                && !ClientAccess.zioMinecraft.player.isInLava()
                && !ClientAccess.zioMinecraft.player.isInWater()
                && !sneakSetting.getValue()) event.cancel();
    }

    private boolean shouldSneakAtEdge(BlockState block) {
        return EntityUtils.hasCollisionBelow(ClientAccess.zioMinecraft.player)
                && !ClientAccess.zioMinecraft.player.isInWater()
                && !ClientAccess.zioMinecraft.player.isInLava()
                && isUnsupportedBlock(block);
    }

    private boolean isUnsupportedBlock(BlockState block) {

        return block.is(Blocks.AIR) || block.getCollisionShape(ClientAccess.zioMinecraft.level, BlockPos.ZERO).isEmpty();
    }
}
