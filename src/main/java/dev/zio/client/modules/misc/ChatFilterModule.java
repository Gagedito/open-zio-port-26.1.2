/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;

import dev.zio.client.ClientContext;
import dev.zio.client.events.ClientPacketListenerSendChatEvent;
import dev.zio.client.events.ChatMessageEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.utils.chat.ChatUtils;
import dev.zio.client.events.EventStage;
import dev.zio.client.managers.CommandRegistry;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.BooleanSetting;
import dev.zio.client.settings.Setting;

import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;

public class ChatFilterModule extends Module {
    public final Setting<Boolean> caseSensitiveSetting;
    private final Setting<Boolean> ignoreSelfSetting;
    private final Setting<Boolean> ignorePrivateMessagesSetting;
    private String lastSentMessage;

    public ChatFilterModule() {
        super("ChatFilter", "Filters chat messages based on your filters.", ModuleCategory.MISC, new String[0]);
        caseSensitiveSetting = add(new BooleanSetting("CaseSensitive", false));
        ignoreSelfSetting = add(new BooleanSetting("IgnoreSelf", true));
        Boolean ignorePrivateMessages = Boolean.valueOf(false);
        ignorePrivateMessagesSetting = add(new BooleanSetting("IgnorePM", ignorePrivateMessages));
        lastSentMessage = null;
    }

    @Override
    public void onEnable() {
        if (!isWorldUnavailable() && ClientContext.chatFilterRegistry.isEmpty()) {
            String notice = "You don't have any chat filters set. Use the \"%schatfilter add <id> <filter>\" command to add new filters."
                    .formatted(CommandRegistry.getPrefix());
            ChatUtils.printChat(
                    Component.nullToEmpty(notice),
                    ChatUtils.moduleSignature(this),
                    ChatUtils.MessagePriority.LOW);
        }
        lastSentMessage = null;
    }

    @EventHandler(priority = 200)
    private void onIncomingChat(ChatMessageEvent event) {
        if (event.getStage() != EventStage.Pre
                || event.getMessage() == null) {
            return;
        }
        GuiMessageTag tag = event.getTag();
        if (tag != null && tag != GuiMessageTag.system() && tag != GuiMessageTag.systemSinglePlayer() && tag != GuiMessageTag.chatNotSecure()) {
            return;
        }
        String text = event.getMessage().getString();
        if (text == null) {
            return;
        }
        if (ignoreSelfSetting.getValue() && lastSentMessage != null && text.contains(lastSentMessage)) {
            lastSentMessage = null;
            return;
        }
        if (ignorePrivateMessagesSetting.getValue() && ChatUtils.isIncomingWhisper(text)) {
            return;
        }
        if (!ClientContext.chatFilterRegistry.isEmpty()
                && ClientContext.chatFilterRegistry.matchesMessage(text)) {
            event.cancel();
        }
    }

    @EventHandler
    private void onOutgoingChat(ClientPacketListenerSendChatEvent event) {
        if (!event.isCancelled()
                && event.getMessage() != null
                && ignoreSelfSetting.getValue()) {
            lastSentMessage = event.getMessage();
        }
    }

}
