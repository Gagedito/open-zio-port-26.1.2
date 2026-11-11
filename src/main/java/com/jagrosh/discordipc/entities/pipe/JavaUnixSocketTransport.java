package com.jagrosh.discordipc.entities.pipe;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;

final class JavaUnixSocketTransport implements AutoCloseable {
    private final SocketChannel socket;
    private final Object writeLock = new Object();

    JavaUnixSocketTransport(SocketChannel socket) { this.socket = java.util.Objects.requireNonNull(socket); }

    static JavaUnixSocketTransport connect(Path endpoint) throws IOException {
        return new JavaUnixSocketTransport(SocketChannel.open(UnixDomainSocketAddress.of(endpoint)));
    }

    IpcFrameCodec.Frame read() throws IOException { return IpcFrameCodec.read(socket); }

    void write(byte[] packet) throws IOException {
        synchronized (writeLock) { IpcFrameCodec.writeFully(socket, packet); }
    }

    @Override public void close() throws IOException { socket.close(); }
}
