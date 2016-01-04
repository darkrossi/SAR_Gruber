/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;

/**
 *
 * @author Mickaël
 */
public class NioEngine extends Engine {

    Selector m_selector;

    InetAddress m_localhost;
    ServerSocketChannel m_sch;
    SocketChannel m_ch;

    Server m_s;

    NioEngine() throws IOException {
        m_selector = SelectorProvider.provider().openSelector();
    }

    /**
     * Allows to register a selectable channel with the NIO selector.
     *
     * @param ch
     * @param interests
     * @return
     * @throws ClosedChannelException
     */
    public SelectionKey register(SelectableChannel ch, Object callbacks, int interests) throws ClosedChannelException {
        SelectionKey key;
        key = ch.register(m_selector, interests);
        key.attach(callbacks);
        return key;
    }

    @Override
    public void mainloop() {

        // Création thread ?
        long delay = 0;
        try {
            for (;;) {
                m_selector.select(delay);
                Iterator<?> selectedKeys = this.m_selector.selectedKeys().iterator();
                if (selectedKeys.hasNext()) {
                    SelectionKey key = (SelectionKey) selectedKeys.next();
                    selectedKeys.remove();
                    if (!key.isValid()) {
                        continue;
                    } else if (key.isAcceptable()) {
                        // a connection was accepted by a ServerSocketChannel.
                        Peer callbacks = (Peer) key.attachment();
                        ServerSocketChannel channel = (ServerSocketChannel) key.channel();

                        SocketChannel m_ch = channel.accept();
                        NioChannel nio_channel = new NioChannel(m_ch);
                        m_ch.configureBlocking(false);
                        m_ch.socket().setTcpNoDelay(true);
                        SelectionKey m_key = register(m_ch, callbacks, SelectionKey.OP_READ);
//                        ByteBuffer m_buf = ByteBuffer.allocate(4);

                        callbacks.accepted(m_s, nio_channel);
                    } else if (key.isReadable()) {
                        // a channel is ready for reading
                        SocketChannel ch = (SocketChannel) key.channel();
                        ByteBuffer m_buf = ByteBuffer.allocate(4);
//                        m_ch.read(m_buf);

//                        NioCallbacks callbacks = (NioCallbacks) key.attachment();
//                        callbacks.handleReadable(key);
                    } else if (key.isWritable()) {
                        // a channel is ready for writing
                        Peer callbacks = (Peer) key.attachment();
                        SocketChannel channel = (SocketChannel) key.channel();
                        NioChannel nio_channel = new NioChannel(m_ch);

                        int length = 1;
                        byte bytes[] = new byte[length];
                        for (byte i = 0; i < length; i++) {
                            bytes[i] = (byte) (i);
                        }

                        nio_channel.send(bytes, 0, length);
                        callbacks.deliver(nio_channel, bytes);

                        key.interestOps(SelectionKey.OP_READ);
                    } else if (key.isConnectable()) {
                        // a connection was established with a remote server.

                        SocketChannel ch = (SocketChannel) key.channel();

                        ch.configureBlocking(false);
                        ch.socket().setTcpNoDelay(true);
                        ch.finishConnect();

                        // always set the READ interest.
                        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

                        ConnectCallback callbacks = (ConnectCallback) key.attachment();
                        callbacks.connected(null);

                        register(m_ch, callbacks, SelectionKey.OP_WRITE);
                    }
                }
            }
        } catch (IOException ex) {
            System.err.println("NioEngine got an exeption: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(-1);
        }
    }

    @Override
    public Server listen(int port, AcceptCallback callback) throws IOException {

        // create a new non-blocking server socket channel
        m_sch = ServerSocketChannel.open();
        m_sch.configureBlocking(false);

        // bind the server socket to the specified address and port
        m_localhost = InetAddress.getByName("localhost");
        InetSocketAddress isa = new InetSocketAddress(m_localhost, port);
        m_sch.socket().bind(isa);

        register(m_sch, callback, SelectionKey.OP_ACCEPT);

        NioServer nio_server = new NioServer(port);
        m_s = nio_server;
        return nio_server;
    }

    @Override
    public void connect(InetAddress hostAddress, int port, ConnectCallback callback) throws UnknownHostException, SecurityException, IOException {
        // create a non-blocking socket channel
        m_ch = SocketChannel.open();
        m_ch.configureBlocking(false);
        m_ch.socket().setTcpNoDelay(true);

        // be notified when the connection to the server will be accepted
        // m_key = m_ch.register(m_selector, SelectionKey.OP_CONNECT);
        register(m_ch, callback, SelectionKey.OP_CONNECT);

        // request to connect to the server
        m_ch.connect(new InetSocketAddress(hostAddress, port));
    }

}
