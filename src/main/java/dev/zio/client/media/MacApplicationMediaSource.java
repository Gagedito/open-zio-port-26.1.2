/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.media;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MacApplicationMediaSource implements MediaSessionSource {
    public static final Set<String> SUPPORTED_APPLICATIONS = Set.of("com.apple.Music","com.spotify.client");

    static final int READ_SEND_MODE = 131219;
    static final long REPLY_TIMEOUT_TICKS = 120L;
    record Application(int pid,String bundleId,String resourceDirectory,String dictionaryFile) {}
    interface Scope extends AutoCloseable { @Override void close() throws IOException; }
    interface Session {
        int enumValue(int property) throws IOException;
        String text(List<Integer> propertyPath) throws IOException;
        Double number(List<Integer> propertyPath) throws IOException;
    }
    interface RuntimeApi {
        Scope begin() throws IOException;
        List<Application> alreadyRunning() throws IOException;
        byte[] dictionary(Application application) throws IOException;
        int permissionForGetData(Application application,boolean askUserIfNeeded) throws IOException;
        Session openPidTarget(Application application,int sendMode,long replyTimeoutTicks) throws IOException;
    }
    private final RuntimeApi runtime;
    private volatile boolean closed;
    private Thread activeReader;
    private Integer selectedPid;
    MacApplicationMediaSource(RuntimeApi runtime) { this.runtime=Objects.requireNonNull(runtime); }
    public static MacApplicationMediaSource connect() throws IOException { return new MacApplicationMediaSource(new MacJnaRuntime()); }
    @Override public MediaSnapshot read() throws IOException {
        synchronized(this){if(closed||Thread.currentThread().isInterrupted())throw new IOException("Mac media source closed");if(activeReader!=null)throw new IOException("Concurrent Mac media reads are not supported");activeReader=Thread.currentThread();}
        try(Scope scope=runtime.begin()) {
            List<Application> applications=new ArrayList<>();
            for(Application app:runtime.alreadyRunning())if(SUPPORTED_APPLICATIONS.contains(app.bundleId())&&app.pid()>0)applications.add(app);
            applications.sort(Comparator.comparing(Application::bundleId).thenComparingInt(Application::pid));
            if(selectedPid!=null)for(int n = 0;n<applications.size();n++)if(applications.get(n).pid()==selectedPid){applications.add(0,applications.remove(n));break;}
            MediaSnapshot fallback=null;Integer fallbackPid=null;IOException unavailable=null;
            for(Application application:applications) {
                if(closed||Thread.currentThread().isInterrupted())throw new IOException("Mac media source closed");
                try {
                    MacScriptingDictionary.Schema schema=MacScriptingDictionary.parse(runtime.dictionary(application));

                    int permission = runtime.permissionForGetData(application,false);
                    if(permission!=0)throw new IOException("Mac media Automation permission unavailable: "+permission);
                    Session session=runtime.openPidTarget(application,READ_SEND_MODE,REPLY_TIMEOUT_TICKS);
                    int state = session.enumValue(schema.playerState());
                    int status = state==schema.playing()?4:schema.paused()!=null&&state==schema.paused()?5:schema.stopped()!=null&&state==schema.stopped()?3:0;
                    String title="",artist="";long position = 0,duration=0;
                    if(status==4||status==5) {
                        title=session.text(List.of(schema.currentTrack(),schema.title()));
                        artist=session.text(List.of(schema.currentTrack(),schema.artist()));
                        if(schema.position()!=null)position = MacScriptingDictionary.millis(session.number(List.of(schema.position().code())),schema.position());
                        if(schema.duration()!=null)duration=MacScriptingDictionary.millis(session.number(List.of(schema.currentTrack(),schema.duration().code())),schema.duration());
                    }
                    MediaSnapshot snapshot=new MediaSnapshot(status,artist,title,position,duration);
                    if(status==4){selectedPid=application.pid();return snapshot;}
                    if(fallback==null){fallback=snapshot;fallbackPid=application.pid();}
                } catch(IOException problem) { if(unavailable==null)unavailable=problem;else unavailable.addSuppressed(problem); }
            }
            selectedPid=fallbackPid;
            if(fallback!=null)return fallback;
            if(unavailable!=null)throw unavailable;
            return MediaSnapshot.empty();
        } finally {synchronized(this){activeReader=null;}}
    }
    @Override public void close() {Thread reader;synchronized(this){closed = true;reader=activeReader;}if(reader!=null)reader.interrupt();}
}
