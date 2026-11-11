/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.modules.misc;


import dev.zio.client.events.ClientPacketListenerSendChatEvent;
import dev.zio.client.events.PacketEvent;
import dev.zio.client.events.EventHandler;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.modules.Module;
import dev.zio.client.modules.ModuleCategory;
import dev.zio.client.settings.EnumSetting;
import dev.zio.client.settings.NamedEntry;
import dev.zio.client.settings.Setting;

import dev.zio.mixin.ServerboundChatCommandPacketAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

public class AntiCyrillicModule extends Module {
    private static final Map<Character, String> TRANSLITERATIONS = createTransliterations();
    private static final int MESSAGE_LENGTH_LIMIT = 256;
    private final Setting<ModeChoice> modeSetting;

    public AntiCyrillicModule() {
        super("AntiCyrillic", "Replaces cyrillic letters in your messages with latin equivalents.", ModuleCategory.MISC);
        modeSetting = add(new EnumSetting<>("Mode", ModeChoice.CHAT));
    }

    @Override
    public String getInfo() {
        try {
            return FontRenderer.capitalize(modeSetting.getValue());
        } catch (Exception ignored) {
            return null;
        }
    }

    @EventHandler
    private void onCommandPacket(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundChatCommandPacket packet
                && modeSetting.getValue() != ModeChoice.CHAT) {
            ((ServerboundChatCommandPacketAccessor) (Object) packet).setCommand(normalizeMessage(packet.command()));
        }
    }

    @EventHandler
    private void onChatMessage(ClientPacketListenerSendChatEvent event) {
        if (modeSetting.getValue() != ModeChoice.COMMANDS) {
            String message = event.getMessage();
            event.setMessage(normalizeMessage(message));
        }
    }

    private String normalizeMessage(String message) {
        String normalized = message;
        if (!normalized.startsWith("/")) {
            normalized = transliterate(normalized);
        } else {
            String[] words = normalized.split(" ");
            if (words.length > 1) {
                StringBuilder command = new StringBuilder();
                for (int index = words.length - 1; index >= 0; index--) {
                    String word = index == 0 ? words[0] : transliterate(words[index]);
                    command.insert(0, word + " ");
                }
                normalized = command.toString();
            }
        }
        return normalized.length() > MESSAGE_LENGTH_LIMIT ? normalized.substring(0, MESSAGE_LENGTH_LIMIT) : normalized;
    }

    private String transliterate(String message) {
        String translated = message;
        for (int index = translated.length() - 1; index >= 0; index--) {
            char character = translated.charAt(index);
            if (TRANSLITERATIONS.containsKey(character)) {
                String prefix = translated.substring(0, index);
                String replacement = TRANSLITERATIONS.get(character);
                String suffix = translated.substring(index + 1);
                translated = joinTransliterationParts(prefix, replacement, suffix);
            }
        }
        return translated;
    }

    private static String joinTransliterationParts(String prefix, String replacement, String suffix) {
        String recipe = "\u0001\u0001\u0001";
        for (String part : new String[]{prefix, replacement, suffix}) {
            recipe = recipe.replaceFirst("\u0001", Matcher.quoteReplacement(part));
        }
        return recipe;
    }

    private static Map<Character, String> createTransliterations() {
        Map<Character, String> replacements = new HashMap<>();
        replacements.put('\u0401', "E");
        replacements.put('\u0410', "A");
        replacements.put('\u0411', "6");
        replacements.put('\u0412', "B");
        replacements.put('\u0413', "r");
        replacements.put('\u0414', "D");
        replacements.put('\u0415', "E");
        replacements.put('\u0416', ")I(");
        replacements.put('\u0417', "3");
        replacements.put('\u0418', "U");
        replacements.put('\u0419', "u'");
        replacements.put('\u041a', "K");
        replacements.put('\u041b', "JI");
        replacements.put('\u041c', "M");
        replacements.put('\u041d', "H");
        replacements.put('\u041e', "O");
        replacements.put('\u041f', "II");
        replacements.put('\u0420', "P");
        replacements.put('\u0421', "C");
        replacements.put('\u0422', "T");
        replacements.put('\u0423', "y");
        replacements.put('\u0424', "qp");
        replacements.put('\u0425', "X");
        replacements.put('\u0426', "U,");
        replacements.put('\u0427', "4");
        replacements.put('\u0428', "LLI");
        replacements.put('\u0429', "LLI,");
        replacements.put('\u042a', "'b");
        replacements.put('\u042b', "bI");
        replacements.put('\u042c', "b");
        replacements.put('\u042d', "3");
        replacements.put('\u042e', "IO");
        replacements.put('\u042f', "9I");
        replacements.put('\u0430', "a");
        replacements.put('\u0431', "6");
        replacements.put('\u0432', "B");
        replacements.put('\u0433', "r");
        replacements.put('\u0434', "D");
        replacements.put('\u0435', "e");
        replacements.put('\u0436', ")I(");
        replacements.put('\u0437', "3");
        replacements.put('\u0438', "u");
        replacements.put('\u0439', "u'");
        replacements.put('\u043a', "K");
        replacements.put('\u043b', "JI");
        replacements.put('\u043c', "M");
        replacements.put('\u043d', "H");
        replacements.put('\u043e', "o");
        replacements.put('\u043f', "n");
        replacements.put('\u0440', "p");
        replacements.put('\u0441', "c");
        replacements.put('\u0442', "T");
        replacements.put('\u0443', "y");
        replacements.put('\u0444', "qp");
        replacements.put('\u0445', "X");
        replacements.put('\u0446', "u,");
        replacements.put('\u0447', "4");
        replacements.put('\u0448', "LLI");
        replacements.put('\u0449', "LLI,");
        replacements.put('\u044a', "'b");
        replacements.put('\u044b', "bI");
        replacements.put('\u044c', "b");
        replacements.put('\u044d', "3");
        replacements.put('\u044e', "IO");
        replacements.put('\u044f', "9I");
        replacements.put('\u0451', "E");
        return replacements;
    }

    public enum ModeChoice implements NamedEntry {
        CHAT("Chat"), COMMANDS("Commands"), BOTH("Both");

        private final String displayName;

        ModeChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return displayName;
        }
    }
}
