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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author Mickaël
 */
public class NioEngine extends Engine {

    private Selector m_selector;

    InetAddress m_localhost = InetAddress.getByName("localhost");
    ServerSocketChannel m_sch; // Channel d'écoute de connexion
    int m_port_listening;

    Server m_s;

    boolean thread_launched = false;

    /**
     * Constructor
     *
     * @throws IOException
     */
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
        key = ch.register(getM_selector(), interests);
        key.attach(callbacks);
        return key;
    }

    @Override
    public void mainloop() {
        long delay = 0;
        try {
            // On capture le thread principal dans une boucle infinie
            for (;;) {
                getM_selector().select(delay);
                Iterator<?> selectedKeys = this.getM_selector().selectedKeys().iterator();
                if (selectedKeys.hasNext()) {
                    SelectionKey key = (SelectionKey) selectedKeys.next();
                    selectedKeys.remove();
                    if (!key.isValid()) {
                        continue;
                    } else if (key.isAcceptable()) {
                        ServerSocketChannel channel_server = (ServerSocketChannel) key.channel(); // On récupère son socket de connexion

                        SocketChannel channel = channel_server.accept(); // On crée un channel avec celui qui arrive
                        channel.configureBlocking(false);
                        channel.socket().setTcpNoDelay(true);

                        Peer peer = (Peer) key.attachment();
                        SelectionKey sk = register(channel, peer, SelectionKey.OP_READ);
                        NioChannel nio_channel = new NioChannel(channel, peer, sk); // On crée un NIO channel associé au channel de connexion

                        peer.accepted(null, nio_channel);

                        if (!thread_launched) {
                            BroadcastThread broadcast_thread = new BroadcastThread(peer, this);
                            broadcast_thread.start();
                            thread_launched = true;
                        }

                    } else if (key.isReadable()) {
                        // a channel is ready for reading
                        SocketChannel m_ch = (SocketChannel) key.channel();

                        Peer peer = (Peer) key.attachment();

                        NioChannel nio_channel = new NioChannel(m_ch, peer, key);
                        nio_channel.read();

                    } else if (key.isWritable()) {
                        // a channel is ready for writing

                        int length = 3;
                        byte bytes[] = new byte[length];
                        for (int i = 0; i < length; i++) {
                            bytes[i] = (byte) (i + 8 * (m_port_listening - 2005));
                        }

                        Peer peer = (Peer) key.attachment();
                        List<Channel> channels = peer.getChannels();
                        for (Channel channel : channels) {
                            channel.send(bytes, 0, length);
                        }

                    } else if (key.isConnectable()) {
                        SocketChannel ch = (SocketChannel) key.channel();
                        ch.configureBlocking(false);
                        ch.socket().setTcpNoDelay(true);
                        ch.finishConnect();

                        Peer peer = (Peer) key.attachment();

                        NioChannel nio_channel = new NioChannel(ch, peer, key);

                        peer.connected(nio_channel);

                        key.interestOps(SelectionKey.OP_READ);

                        if (!thread_launched) {
                            BroadcastThread broadcast_thread = new BroadcastThread(peer, this);
                            broadcast_thread.start();
                            thread_launched = true;
                        }
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

        m_port_listening = port;

//        m_state = ACCEPTING;
        // create a new non-blocking server socket channel
        m_sch = ServerSocketChannel.open();
        m_sch.configureBlocking(false);
        // bind the server socket to the specified address and port
        InetSocketAddress isa = new InetSocketAddress(m_localhost, m_port_listening);
        m_sch.socket().bind(isa);

        register(m_sch, callback, SelectionKey.OP_ACCEPT);

        NioServer nio_server = new NioServer(m_port_listening);
        m_s = nio_server;
        return nio_server;
    }

    @Override
    public void connect(InetAddress hostAddress, int port, ConnectCallback callback) throws UnknownHostException, SecurityException, IOException {
        // create a non-blocking socket channel
//        assert (getM_state() == DISCONNECTED);
//        m_state = CONNECTING;

        SocketChannel m_ch = SocketChannel.open();
        m_ch.configureBlocking(false);
        m_ch.socket().setTcpNoDelay(true);

        // be notified when the connection to the server will be accepted
        register(m_ch, callback, SelectionKey.OP_CONNECT);

        // request to connect to the server
        m_ch.connect(new InetSocketAddress(hostAddress, port));
    }

    /**
     * @return the m_selector
     */
    public Selector getM_selector() {
        return m_selector;
    }

    /**
     * @param m_selector the m_selector to set
     */
    public void setM_selector(Selector m_selector) {
        this.m_selector = m_selector;
    }

}
