/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;

import dev.zio.client.ClientAccess;
import dev.zio.client.events.ChatMessageEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.events.MinecraftDisconnectEvent;
import dev.zio.client.access.VisibleChatLinesAccess;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.events.EventStage;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.multiplayer.chat.GuiMessage.Line;
import net.minecraft.network.chat.Component;

public class AntiSpamModule extends Module {
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://)?([\\w\\-])+\\.{1}([a-zA-Z]{2,63})([/\\w-]*)*/?\\??([^#\\n\\r]*)?#?([^\\n\\r]*)");
    private static final Pattern INVITE_PATTERN = Pattern.compile("gg/([a-zA-Z0-9]*)");
    private final Map<String, MessageStack> messageStacks = new HashMap<>();
    public final Setting<Boolean> indicatorSetting;
    private final Setting<Boolean> linksSetting;
    private String pendingMessage;

    public AntiSpamModule() {
        super("AntiSpam", "Stacks similar chat messages with each other.", ModuleCategory.MISC);
        indicatorSetting = add(new BooleanSetting("Indicator", true));
        linksSetting = add(new BooleanSetting("Links", false));
        setDrawn(false);
    }

    @Override
    public void onToggle() {
        messageStacks.clear();
        pendingMessage = null;
    }

    @EventHandler
    private void onDisconnect(MinecraftDisconnectEvent event) {
        messageStacks.clear();
        pendingMessage = null;
    }

    @EventHandler(priority = 200)
    private void onChatLine(ChatMessageEvent event) {
        if (event.getStage() == EventStage.Pre) {
            if (event.getSignature() != null && event.getSignature().asByteBuffer().getInt() < 0) {
                return;
            }
            if (event.getTag()
                    == ChatUtils.MESSAGE_TAG) {
                return;
            }
            pendingMessage = event.getMessage().getString().toLowerCase(Locale.ROOT);
            if (linksSetting.getValue() && containsLink(pendingMessage)) {
                event.cancel();
                return;
            }
            if (indicatorSetting.getValue()) {
                messageStacks.computeIfPresent(pendingMessage, (message, stack) -> {
                    ((VisibleChatLinesAccess) ClientAccess.zioMinecraft.gui.getChat()).getVisible().remove(stack.line());
                    return stack;
                });
                messageStacks.putIfAbsent(pendingMessage, new MessageStack(1L, event.getLine()));
                long count = messageStacks.get(pendingMessage).count();
                if (count > 1L) {
                    event.setMessage(Component.empty()
                            .append(event.getMessage())
                            .append(Component.literal(" [x" + count + "]")));
                }
            }
        } else if (pendingMessage != null && indicatorSetting.getValue()) {
            messageStacks.computeIfPresent(pendingMessage, (message, stack) -> new MessageStack(stack.count() + 1L,
                    event.getLine()));
        }
    }

    private boolean containsLink(String message) {
        return URL_PATTERN.matcher(message).find() || INVITE_PATTERN.matcher(message).find();
    }

    private record MessageStack(long count, Line line) {}
}
