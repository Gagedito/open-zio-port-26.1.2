/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

final class MacScriptingDictionary {
    enum TimeUnit { SECONDS, MILLISECONDS }
    record TimeProperty(int code, TimeUnit unit) {}
    record Schema(int playerState, int currentTrack, int title, int artist,
                  int playing, Integer paused, Integer stopped,
                  TimeProperty position, TimeProperty duration) {}
    static byte[] readVendorFile(String resourceDirectory, String dictionaryFile) throws IOException {
        if (resourceDirectory.isBlank() || dictionaryFile.isBlank()) throw new IOException("Installed app does not expose a static scripting dictionary");
        Path resources = Path.of(resourceDirectory).toRealPath();
        Path relative = Path.of(dictionaryFile);
        if (relative.isAbsolute()) throw new IOException("Dictionary path must be within app resources");
        for (Path part : relative) {
            if (part.toString().equals("..")) throw new IOException("Dictionary path cannot escape app resources");
        }
        Path lexical = resources.resolve(relative).normalize();
        if (!lexical.startsWith(resources)) throw new IOException("Dictionary path cannot escape app resources");
        Path file = lexical.toRealPath();
        if (!file.startsWith(resources) || !file.getFileName().toString().endsWith(".sdef") || !Files.isRegularFile(file)) throw new IOException("Unsupported app scripting dictionary path");
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(2097153);
            if (bytes.length > 2097152) throw new IOException("Vendor dictionary exceeds bounded size");
            return bytes;
        }
    }
    static Schema parse(byte[] definition) throws IOException {
        if (definition.length == 0 || definition.length > 2 * 1024 * 1024) throw new IOException("Unsupported scripting dictionary size");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false); factory.setXIncludeAware(false);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId,systemId) -> new InputSource(new StringReader("")));
            var document = builder.parse(new ByteArrayInputStream(definition));
            if (!document.getDocumentElement().getTagName().equals("dictionary")) throw new IOException("Invalid vendor scripting dictionary root");
            NodeList properties = document.getElementsByTagName("property");
            Element state = uniqueProperty(properties, "player state", null, true);
            Element track = uniqueProperty(properties, "current track", null, true);
            Element title = uniqueProperty(properties, "name", "text", true);
            Element artist = uniqueProperty(properties, "artist", "text", true);
            Element enumDefinition = null;
            NodeList enums = document.getElementsByTagName("enumeration");
            for (int n = 0;n<enums.getLength();n++) {
                Element value=(Element)enums.item(n);
                if (value.getAttribute("name").equals(state.getAttribute("type"))) {
                    if (enumDefinition!=null) throw new IOException("Ambiguous vendor player-state enum");
                    enumDefinition=value;
                }
            }
            if(enumDefinition==null)throw new IOException("Vendor player-state enum unavailable");
            Integer playing = enumerator(enumDefinition,"playing"),paused=enumerator(enumDefinition,"paused"),stopped=enumerator(enumDefinition,"stopped");
            if(playing==null)throw new IOException("Vendor playing enum unavailable");
            return new Schema(code(state.getAttribute("code")),code(track.getAttribute("code")),code(title.getAttribute("code")),code(artist.getAttribute("code")),playing,paused,stopped,
                    timeProperty(uniqueProperty(properties,"player position",null,false)),timeProperty(uniqueProperty(properties,"duration",null,false)));
        } catch(IOException expected) { throw expected; }
        catch(Exception malformed) { throw new IOException("Unsupported vendor scripting dictionary",malformed); }
    }
    private static Element uniqueProperty(NodeList properties,String name,String type,boolean required)throws IOException {
        Element found=null;Set<Integer> codes=new LinkedHashSet<>();
        for(int n = 0;n<properties.getLength();n++) {
            Element value=(Element)properties.item(n);
            if(!value.getAttribute("name").equals(name))continue;
            if(type!=null&&!value.getAttribute("type").equals(type))continue;
            codes.add(code(value.getAttribute("code")));if(found==null)found=value;
        }
        if(codes.size()>1)throw new IOException("Ambiguous vendor property: "+name);
        if(found==null&&required)throw new IOException("Vendor property unavailable: "+name);
        return found;
    }
    private static Integer enumerator(Element definition,String name)throws IOException {
        Integer found=null;NodeList values=definition.getElementsByTagName("enumerator");
        for(int n = 0;n<values.getLength();n++){Element value=(Element)values.item(n);if(value.getAttribute("name").equals(name)){int code = code(value.getAttribute("code"));if(found!=null&&found!=code)throw new IOException("Ambiguous vendor enum value");found=code;}}
        return found;
    }
    private static TimeProperty timeProperty(Element property)throws IOException {
        if(property==null)return null;
        String description=(property.getAttribute("description")+" "+property.getTextContent()).toLowerCase(Locale.ROOT);
        boolean milliseconds = description.contains("millisecond"),seconds=description.replace("millisecond", "").contains("second");
        if(milliseconds==seconds)return null;
        if(!Set.of("real","integer","number").contains(property.getAttribute("type")))return null;
        return new TimeProperty(code(property.getAttribute("code")),milliseconds?TimeUnit.MILLISECONDS:TimeUnit.SECONDS);
    }
    static int code(String value)throws IOException {
        if(value.length()!=4)throw new IOException("Vendor property requires a four-character code");
        int code = 0;
        for(int n = 0;n<4;n++){char c = value.charAt(n);if(c<32||c>126)throw new IOException("Unsupported vendor four-character code");code = (code<<8)|c;}
        return code;
    }
    static long millis(Double value,TimeProperty property)throws IOException {
        if(value==null||property==null)return 0;
        if(!Double.isFinite(value)||value<0)throw new IOException("Invalid vendor media time");
        double millis = property.unit()==TimeUnit.SECONDS?value*1000:value;
        if(millis>Long.MAX_VALUE)throw new IOException("Vendor media time exceeds long range");
        return (long)millis;
    }
}
