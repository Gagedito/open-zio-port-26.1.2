/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.media;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

final class MacJnaRuntime implements MacApplicationMediaSource.RuntimeApi {
    private static final String FOUNDATION="/System/Library/Frameworks/Foundation.framework/Foundation";
    private static final String APPKIT="/System/Library/Frameworks/AppKit.framework/AppKit";
    private static final String APPLE_EVENTS="/System/Library/Frameworks/CoreServices.framework/Frameworks/AE.framework/AE";

    private final NativeLibrary foundation,appkit,appleEvents,objc;
    private final Function objcClass,selector,message,permission;
    private final Thread owner=Thread.currentThread();
    private boolean scopeActive;

    MacJnaRuntime() throws IOException {

        if(Native.POINTER_SIZE!=8)throw new IOException("Unsupported Cocoa pointer ABI");
        foundation=NativeLibrary.getInstance(FOUNDATION);appkit=NativeLibrary.getInstance(APPKIT);appleEvents=NativeLibrary.getInstance(APPLE_EVENTS);
        objc=NativeLibrary.getInstance("objc",Map.of(Library.OPTION_STRING_ENCODING,StandardCharsets.UTF_8.name()));
        objcClass=objc.getFunction("objc_getClass");selector=objc.getFunction("sel_registerName");message=objc.getFunction("objc_msgSend");permission=appleEvents.getFunction("AEDeterminePermissionToAutomateTarget");
    }
    @Override public MacApplicationMediaSource.Scope begin() throws IOException {
        requireOwner();if(scopeActive)throw new IOException("Nested Cocoa media read scope");
        Pointer pool=required(pointer(type("NSAutoreleasePool"),"new"),"autorelease pool");scopeActive=true;
        AtomicBoolean drained=new AtomicBoolean();
        return ()->{requireOwner();if(drained.compareAndSet(false,true)){try{voidMessage(pool,"drain");}finally{scopeActive=false;}}};
    }
    private void requireOwner() throws IOException {if(Thread.currentThread()!=owner)throw new IOException("Cocoa media objects are worker-owned");}
    private void requireScope()throws IOException {requireOwner();if(!scopeActive)throw new IOException("Cocoa media read scope absent");if(Thread.currentThread().isInterrupted())throw new IOException("Cocoa media read interrupted");}
    @Override public List<MacApplicationMediaSource.Application> alreadyRunning()throws IOException {
        requireScope();List<MacApplicationMediaSource.Application> result=new ArrayList<>();
        for(String id:MacApplicationMediaSource.SUPPORTED_APPLICATIONS) {
            Pointer apps=required(pointer(type("NSRunningApplication"),"runningApplicationsWithBundleIdentifier:",nsString(id)),"running app list");
            long count = longMessage(apps,"count");if(count<0||count>256)throw new IOException("Unexpected supported-app process count");
            for(long index = 0;index<count;index++) {
                Pointer app=required(pointer(apps,"objectAtIndex:",index),"running app");
                if(boolMessage(app,"isTerminated")||!boolMessage(app,"isFinishedLaunching"))continue;
                int pid = intMessage(app,"processIdentifier");
                if(pid<=0||!id.equals(text(pointer(app,"bundleIdentifier"))))continue;
                Pointer bundle=required(pointer(type("NSBundle"),"bundleWithURL:",required(pointer(app,"bundleURL"),"app bundle URL")),"app bundle");
                String resources=text(pointer(bundle,"resourcePath"));
                String definition=text(pointer(bundle,"objectForInfoDictionaryKey:",nsString("OSAScriptingDefinition")));
                result.add(new MacApplicationMediaSource.Application(pid,id,resources,definition));
            }
        }
        return List.copyOf(result);
    }
    @Override public byte[] dictionary(MacApplicationMediaSource.Application application)throws IOException {
        requireScope();checkPid(application);
        return MacScriptingDictionary.readVendorFile(application.resourceDirectory(),application.dictionaryFile());
    }
    @Override public int permissionForGetData(MacApplicationMediaSource.Application application,boolean askUserIfNeeded)throws IOException {
        requireScope();if(askUserIfNeeded)throw new IOException("Media provider never requests Automation consent");checkPid(application);
        Pointer target=pidDescriptor(application.pid());Pointer address=required(pointer(target,"aeDesc"),"PID address descriptor");
        return permission.invokeInt(new Object[]{address,code("core"),code("getd"),(byte)0});
    }
    @Override public MacApplicationMediaSource.Session openPidTarget(MacApplicationMediaSource.Application application,int sendMode,long replyTimeoutTicks)throws IOException {
        requireScope();checkPid(application);
        if(sendMode!=MacApplicationMediaSource.READ_SEND_MODE||replyTimeoutTicks!=MacApplicationMediaSource.REPLY_TIMEOUT_TICKS)throw new IOException("Unreviewed Apple event options");
        Pointer target=pidDescriptor(application.pid());
        return new MacApplicationMediaSource.Session() {
            private Pointer query(List<Integer> propertyPath)throws IOException {
                requireScope();checkPid(application);
                MacGetDataRequest request=MacGetDataRequest.create(application.pid(),propertyPath,sendMode,replyTimeoutTicks);
                Pointer container=required(pointer(type("NSAppleEventDescriptor"),"nullDescriptor"),"null container");
                for(int property:request.propertyPath()) {
                    Pointer record=required(pointer(type("NSAppleEventDescriptor"),"recordDescriptor"),"property specifier record");
                    voidMessage(record,"setDescriptor:forKeyword:",typeDescriptor(code("prop")),code("want"));
                    voidMessage(record,"setDescriptor:forKeyword:",container,code("from"));
                    voidMessage(record,"setDescriptor:forKeyword:",enumDescriptor(code("prop")),code("form"));
                    voidMessage(record,"setDescriptor:forKeyword:",typeDescriptor(property),code("seld"));
                    container=required(pointer(record,"coerceToDescriptorType:",code("obj ")),"property specifier");
                }
                Pointer event=required(pointer(type("NSAppleEventDescriptor"),"appleEventWithEventClass:eventID:targetDescriptor:returnID:transactionID:",request.eventClass(),request.eventId(),target,(short)-1,0),"get-data event");
                voidMessage(event,"setParamDescriptor:forKeyword:",container,code("----"));
                PointerByReference error=new PointerByReference();
                Pointer reply=pointer(event,"sendEventWithOptions:timeout:error:",(long)request.sendMode(),request.timeoutSeconds(),error);
                if(error.getValue()!=null)throw new IOException("Mac media get-data unavailable: "+MacJnaRuntime.this.text(pointer(error.getValue(),"localizedDescription")));
                required(reply,"get-data reply");
                Pointer errorNumber=pointer(reply,"paramDescriptorForKeyword:",code("errn"));
                if(errorNumber!=null&&intMessage(errorNumber,"int32Value")!=0)throw new IOException("Mac media app rejected get-data: "+intMessage(errorNumber,"int32Value"));
                return required(pointer(reply,"paramDescriptorForKeyword:",code("----")),"get-data value");
            }
            @Override public int enumValue(int property)throws IOException {
                Pointer value=query(List.of(property));if(intMessage(value,"descriptorType")!=code("enum"))throw new IOException("Vendor player state is not an enumeration");return intMessage(value,"enumCodeValue");
            }
            @Override public String text(List<Integer> propertyPath)throws IOException {
                Pointer value=query(propertyPath);int descriptorType = intMessage(value,"descriptorType");
                if(!List.of(code("TEXT"),code("cstr"),code("utxt"),code("ut16"),code("utf8")).contains(descriptorType))throw new IOException("Vendor media property is not text");
                return MacJnaRuntime.this.text(required(pointer(value,"stringValue"),"media string"));
            }
            @Override public Double number(List<Integer> propertyPath)throws IOException {
                Pointer value=query(propertyPath);int descriptorType = intMessage(value,"descriptorType");
                if(!List.of(code("shor"),code("long"),code("magn"),code("comp"),code("ucom"),code("sing"),code("doub")).contains(descriptorType))throw new IOException("Vendor media property is not numeric");
                return doubleMessage(value,"doubleValue");
            }
        };
    }
    private void checkPid(MacApplicationMediaSource.Application application)throws IOException {
        if(!MacApplicationMediaSource.SUPPORTED_APPLICATIONS.contains(application.bundleId())||application.pid()<=0)throw new IOException("Unsupported target app");
        Pointer app=required(pointer(type("NSRunningApplication"),"runningApplicationWithProcessIdentifier:",application.pid()),"current PID app");
        if(boolMessage(app,"isTerminated")||!boolMessage(app,"isFinishedLaunching")||!application.bundleId().equals(text(pointer(app,"bundleIdentifier"))))throw new IOException("Media app is no longer the same running PID target");
    }
    private Pointer pidDescriptor(int pid)throws IOException {return required(pointer(type("NSAppleEventDescriptor"),"descriptorWithProcessIdentifier:",pid),"PID target");}
    private Pointer typeDescriptor(int code)throws IOException {return required(pointer(type("NSAppleEventDescriptor"),"descriptorWithTypeCode:",code),"type descriptor");}
    private Pointer enumDescriptor(int code)throws IOException {return required(pointer(type("NSAppleEventDescriptor"),"descriptorWithEnumCode:",code),"enum descriptor");}
    private Pointer type(String name)throws IOException {return required(objcClass.invokePointer(new Object[]{name}),"Cocoa class "+name);}
    private Pointer nsString(String text)throws IOException {return required(pointer(type("NSString"),"stringWithUTF8String:",text),"NSString");}
    private static Pointer required(Pointer pointer,String name)throws IOException {if(pointer==null||Pointer.nativeValue(pointer)==0)throw new IOException("Unavailable "+name);return pointer;}
    private Object[] arguments(Pointer receiver,String name,Object...arguments) {Object[] all=new Object[arguments.length+2];all[0]=receiver;all[1]=selector.invokePointer(new Object[]{name});System.arraycopy(arguments,0,all,2,arguments.length);return all;}
    private Pointer pointer(Pointer receiver,String name,Object...arguments) {return message.invokePointer(arguments(receiver,name,arguments));}
    private int intMessage(Pointer receiver,String name,Object...arguments) {return message.invokeInt(arguments(receiver,name,arguments));}
    private long longMessage(Pointer receiver,String name,Object...arguments) {return message.invokeLong(arguments(receiver,name,arguments));}
    private double doubleMessage(Pointer receiver,String name,Object...arguments) {return message.invokeDouble(arguments(receiver,name,arguments));}
    private boolean boolMessage(Pointer receiver,String name) {return ((Number)message.invoke(Byte.class,arguments(receiver,name))).byteValue()!=0;}
    private void voidMessage(Pointer receiver,String name,Object...arguments) {message.invokeVoid(arguments(receiver,name,arguments));}
    private static int code(String value)throws IOException {return MacScriptingDictionary.code(value);}
    private String text(Pointer string)throws IOException {
        if(string==null)return "";
        if(!boolMessageWithClass(string,"NSString"))throw new IOException("Unexpected Cocoa string class");
        long length = longMessage(string,"length");if(length<0||length>1048576)throw new IOException("Cocoa string exceeds bounded size");if(length==0)return "";
        try(Memory units=new Memory(length*2)) {
            voidMessage(string,"getCharacters:range:",units,new Range(0,length));
            char[] characters=new char[(int)length];for(int n = 0;n<characters.length;n++)characters[n]=(char)(units.getShort(n*2L)&65535);
            return new String(characters);
        }
    }
    private boolean boolMessageWithClass(Pointer object,String name)throws IOException {return ((Number)message.invoke(Byte.class,arguments(object,"isKindOfClass:",type(name)))).byteValue()!=0;}
    @Structure.FieldOrder({"location","length"})
    public static final class Range extends Structure implements Structure.ByValue {
        public long location,length;
        public Range(){ }
        Range(long location,long length){this.location=location;this.length=length;}
    }
}
