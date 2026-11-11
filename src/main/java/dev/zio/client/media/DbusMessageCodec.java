/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DbusMessageCodec {
    static final int MAX_MESSAGE_BYTES = 16 * 1024 * 1024;
    record Variant(String signature, Object value) {}
    record Layout(ByteOrder order, int type, int fieldsBytes, int bodyBytes) {
        int paddedFieldsBytes() { return (fieldsBytes + 7) & ~7; }
    }
    record Message(int type, long replySerial, Map<Integer, Variant> headers, List<Object> arguments) {}

    static Layout layout(byte[] fixed) throws IOException {
        if (fixed.length != 16) throw invalid("Fixed header length");
        ByteOrder order = fixed[0] == 'l' ? ByteOrder.LITTLE_ENDIAN : fixed[0] == 'B' ? ByteOrder.BIG_ENDIAN : null;
        if (order == null || fixed[3] != 1 || (fixed[2] & ~7) != 0) throw invalid("Version, flags or byte order");
        ByteBuffer b = ByteBuffer.wrap(fixed).order(order);
        int body = b.getInt(4), serial = b.getInt(8), fields = b.getInt(12);
        if (serial == 0 || body < 0 || fields < 0 || fields > MAX_MESSAGE_BYTES - 23 || body > MAX_MESSAGE_BYTES - 23 - fields) throw invalid("Message size or serial");
        return new Layout(order, fixed[1] & 255, fields, body);
    }

    static Message decode(Layout layout, byte[] paddedFields, byte[] body) throws IOException {
        if (paddedFields.length != layout.paddedFieldsBytes() || body.length != layout.bodyBytes()) throw invalid("Message lengths");
        Reader fields = new Reader(paddedFields, layout.order());
        LinkedHashMap<Integer, Variant> headers = new LinkedHashMap<>();
        while (fields.position < layout.fieldsBytes()) {
            fields.align(8);
            if (fields.position >= layout.fieldsBytes()) throw invalid("Trailing header alignment");
            int id = fields.byteValue();
            Variant value = fields.variant(0);
            if (id == 0 || headers.putIfAbsent(id, value) != null || fields.position > layout.fieldsBytes()) throw invalid("Header field");
            String expected = switch (id) { case 1 -> "o"; case 2, 3, 4, 6, 7 -> "s"; case 5, 9 -> "u"; case 8 -> "g"; default -> null; };
            if (expected != null && !expected.equals(value.signature())) throw invalid("Header field type");
        }
        fields.align(8);
        if (fields.position != paddedFields.length) throw invalid("Header padding");
        if (headers.containsKey(9) && (Long) headers.get(9).value() != 0) throw invalid("Unix FD passing not negotiated");
        boolean hasPath = headers.containsKey(1), hasMember = headers.containsKey(3);
        if (layout.type() == 1 && (!hasPath || !hasMember) || layout.type() == 4 && (!hasPath || !hasMember || !headers.containsKey(2))) throw invalid("Required request/signal headers");
        if ((layout.type() == 2 || layout.type() == 3) && !headers.containsKey(5) || layout.type() == 3 && !headers.containsKey(4)) throw invalid("Required reply headers");
        String signature = headers.containsKey(8) ? (String) headers.get(8).value() : "";
        Reader reader = new Reader(body, layout.order());
        List<Object> arguments = new ArrayList<>();
        for (String type : types(signature)) arguments.add(reader.value(type, 0));
        if (reader.position != body.length) throw invalid("Body signature/length");
        long replySerial = headers.containsKey(5) ? (Long) headers.get(5).value() : 0;
        return new Message(layout.type(), replySerial, Collections.unmodifiableMap(headers), List.copyOf(arguments));
    }

    static byte[] methodCall(int serial, String destination, String path, String iface, String member, List<String> arguments) throws IOException {
        if (serial == 0) throw invalid("Zero serial");
        Writer fields = new Writer(), body = new Writer();
        fields.field(1, "o", path); fields.field(2, "s", iface); fields.field(3, "s", member); fields.field(6, "s", destination);
        String signature = "s".repeat(arguments.size());
        if (!signature.isEmpty()) fields.field(8, "g", signature);
        for (String argument : arguments) body.string(argument);
        int fieldsBytes = fields.bytes.size(); fields.align(8);
        ByteBuffer fixed = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);

        fixed.put((byte)'l').put((byte)1).put((byte)2).put((byte)1).putInt(body.bytes.size()).putInt(serial).putInt(fieldsBytes);
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.writeBytes(fixed.array()); all.writeBytes(fields.bytes.toByteArray()); all.writeBytes(body.bytes.toByteArray());
        if (all.size() > MAX_MESSAGE_BYTES) throw invalid("Request too large");
        return all.toByteArray();
    }

    private static List<String> types(String signature) throws IOException {
        if (signature.length() > 255) throw invalid("Signature length");
        List<String> result = new ArrayList<>();
        for (int at = 0; at < signature.length();) { int end = typeEnd(signature, at, 0, false); result.add(signature.substring(at, end)); at = end; }
        return result;
    }
    private static int typeEnd(String signature, int at, int depth, boolean dictionaryAllowed) throws IOException {
        if (depth > 32 || at >= signature.length()) throw invalid("Signature nesting");
        char c = signature.charAt(at++);
        if ("ybnqiuxtdsoghv".indexOf(c) >= 0) return at;
        if (c == 'a') return typeEnd(signature, at, depth + 1, true);
        if (c == '(' || c == '{' && dictionaryAllowed) {
            int start = at, count = 0; char close = c == '(' ? ')' : '}';
            while (at < signature.length() && signature.charAt(at) != close) { at = typeEnd(signature, at, depth + 1, false); count++; }
            if (at >= signature.length() || at == start || c == '{' && (count != 2 || "ybnqiuxtdsogh".indexOf(signature.charAt(start)) < 0)) throw invalid("Container signature");
            return at + 1;
        }
        throw invalid("Unsupported signature type");
    }
    private static int alignment(String type) throws IOException {
        return switch (type.charAt(0)) { case 'y', 'g', 'v' -> 1; case 'n', 'q' -> 2; case 'b', 'i', 'u', 's', 'o', 'a', 'h' -> 4; case 'x', 't', 'd', '(', '{' -> 8; default -> throw invalid("Type alignment"); };
    }
    private static IOException invalid(String detail) { return new IOException("Invalid D-Bus message: " + detail); }

    private static final class Reader {
        private final byte[] bytes;
        private final ByteBuffer buffer;
        int position;
        Reader(byte[] bytes, ByteOrder order) { this.bytes = bytes; buffer = ByteBuffer.wrap(bytes).order(order); }
        void require(int count) throws IOException { if (count < 0 || count > bytes.length - position) throw invalid("Truncated value"); }
        void align(int alignment) throws IOException { int next = (position + alignment - 1) & -alignment; require(next - position); while (position < next) if (bytes[position++] != 0) throw invalid("Nonzero padding"); }
        int byteValue() throws IOException { require(1); return bytes[position++] & 255; }
        long unsignedInt() throws IOException { require(4); long n = Integer.toUnsignedLong(buffer.getInt(position)); position += 4; return n; }
        int size() throws IOException { long n = unsignedInt(); if (n > MAX_MESSAGE_BYTES) throw invalid("Value too large"); return (int)n; }
        String text(boolean signature) throws IOException {
            int count = signature ? byteValue() : size(); require(count + 1);
            String text;
            try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, position, count)).toString(); }
            catch (CharacterCodingException malformed) { throw invalid("Invalid UTF-8"); }
            position += count;
            if (byteValue() != 0 || text.indexOf(0) >= 0) throw invalid("String terminator");
            if (signature) types(text);
            return text;
        }
        Variant variant(int depth) throws IOException {
            String signature = text(true);
            if (types(signature).size() != 1) throw invalid("Variant must contain one type");
            return new Variant(signature, value(signature, depth + 1));
        }
        Object value(String type, int depth) throws IOException {
            if (depth > 64) throw invalid("Value nesting");
            align(alignment(type));
            return switch (type.charAt(0)) {
                case 'y' -> byteValue();
                case 'b' -> { long n = unsignedInt(); if (n != 0 && n != 1) throw invalid("Boolean value"); yield n == 1; }
                case 'n' -> { require(2); short n = buffer.getShort(position); position += 2; yield n; }
                case 'q' -> { require(2); int n = Short.toUnsignedInt(buffer.getShort(position)); position += 2; yield n; }
                case 'i' -> { require(4); int n = buffer.getInt(position); position += 4; yield n; }
                case 'u' -> unsignedInt();
                case 'x', 't' -> { require(8); long n = buffer.getLong(position); position += 8; yield n; }
                case 'd' -> { require(8); double n = buffer.getDouble(position); position += 8; yield n; }
                case 's', 'o' -> text(false);
                case 'g' -> text(true);
                case 'v' -> variant(depth);
                case 'h' -> throw invalid("Unix FD type not negotiated");
                case 'a' -> {
                    int length = size(); String element = type.substring(1); align(alignment(element)); int end = position + length;
                    if (end < position || end > bytes.length) throw invalid("Array length");
                    List<Object> values = new ArrayList<>();
                    while (position < end) { values.add(value(element, depth + 1)); if (position > end) throw invalid("Array element length"); }
                    if (element.charAt(0) != '{') yield List.copyOf(values);
                    LinkedHashMap<Object,Object> dictionary = new LinkedHashMap<>();
                    for (Object value : values) { List<?> pair = (List<?>) value; if (dictionary.putIfAbsent(pair.get(0), pair.get(1)) != null) throw invalid("Duplicate dictionary key"); }
                    yield Collections.unmodifiableMap(dictionary);
                }
                case '(', '{' -> {
                    List<Object> members = new ArrayList<>();
                    for (String member : types(type.substring(1, type.length() - 1))) members.add(value(member, depth + 1));
                    yield List.copyOf(members);
                }
                default -> throw invalid("Value type");
            };
        }
    }

    private static final class Writer {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        void align(int alignment) { while ((bytes.size() & (alignment - 1)) != 0) bytes.write(0); }
        void integer(int value) { align(4); for (int shift = 0; shift < 32; shift += 8) bytes.write(value >>> shift); }
        void string(String value) throws IOException {
            if (value == null || value.indexOf(0) >= 0) throw invalid("Outgoing string");
            byte[] utf8 = value.getBytes(StandardCharsets.UTF_8); integer(utf8.length); bytes.writeBytes(utf8); bytes.write(0);
        }
        void signature(String value) throws IOException { types(value); bytes.write(value.length()); bytes.writeBytes(value.getBytes(StandardCharsets.US_ASCII)); bytes.write(0); }
        void field(int code, String type, String value) throws IOException { align(8); bytes.write(code); signature(type); if (type.equals("g")) signature(value); else string(value); }
    }
}
