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

    private Peer m_peer; // Représente la VM et contient les channels associés aux personnes auxquelle elle est connectée

    InetAddress m_localhost = InetAddress.getByName("localhost");
    ServerSocketChannel m_sch; // Channel d'écoute de connexion
    int m_port_listening;

    Server m_s;

    private static final int DISCONNECTED = 0;
    private static final int ACCEPTING = 1;
    private static final int READING_LENGTH = 2;
    private static final int READING_BYTES = 3;
    private static final int CONNECTING = 4;
    private static final int CONNECTED = 5;
    private static final int SENDING = 6;

    private int m_state;
    private ByteBuffer m_buf;
    byte m_seqno;

    int nb_msg = 0;

    List<SelectionKey> m_sk_l;

    /**
     * Constructor
     *
     * @throws IOException
     */
    NioEngine() throws IOException {
        m_selector = SelectorProvider.provider().openSelector();
        m_peer = new Peer();
        m_state = DISCONNECTED;
        m_sk_l = new ArrayList<>();
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

                        NioChannel nio_channel = new NioChannel(channel, null, null); // On crée un NIO channel associé au channel de connexion

                        AcceptCallback acceptor = (AcceptCallback) key.attachment();

                        /**
                         *
                         */
                        Deliver deliver = new Deliver();
                        SelectionKey sk = register(channel, deliver, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

                        m_sk_l.add(sk);

                        getM_peer().add(channel);
                        m_buf = ByteBuffer.allocate(4);
                        m_state = CONNECTED;

                        acceptor.accepted(m_s, nio_channel);
                    } else if (key.isReadable()) {
                        // a channel is ready for reading
                        SocketChannel m_ch = (SocketChannel) key.channel();
                        int len, count = 0;
                        switch (getM_state()) {
                            case CONNECTED:
                                m_state = READING_LENGTH;
                            case READING_LENGTH:
                                count = m_ch.read(getM_buf());
                                if (count == -1) {
                                    System.err.println("End of stream!");
                                    System.exit(-1);
                                }
                                if (getM_buf().hasRemaining()) {
                                    return;
                                }
                                m_state = READING_BYTES;
                                getM_buf().position(0);
                                len = getM_buf().getInt();
                                m_buf = ByteBuffer.allocate(len);
                            case READING_BYTES:
                                count = m_ch.read(getM_buf());
                                if (count == -1) {
                                    System.err.println("End of stream!");
                                    System.exit(-1);
                                }
                                if (getM_buf().hasRemaining()) {
                                    return;
                                }

                                getM_buf().position(0);
                                byte bytes[] = new byte[getM_buf().remaining()];
                                getM_buf().get(bytes);

                                m_state = READING_LENGTH;
                                m_buf = ByteBuffer.allocate(4);

                                for (int i = 0; i < bytes.length; i++) {
                                    assert (m_seqno++ == bytes[i]);
                                }

                                System.out.print("Réception de " + m_ch.getRemoteAddress());
                                for (int i = 0; i < bytes.length; i++) {
                                    System.out.print("\t" + bytes[i]);
                                }
                                System.out.println();
                                System.out.flush();
                                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        }

                    } else if (key.isWritable()) {
                        // a channel is ready for writing

                        int length = 3;
                        byte bytes[] = new byte[length];
                        for (int i = 0; i < length; i++) {
                            bytes[i] = (byte) (i + 8 * (m_port_listening - 2005));
                        }

                        assert (getM_state() == CONNECTED);
                        m_state = SENDING;

                        List<SocketChannel> channels = getM_peer().getChannels();
                        for (SocketChannel channel : channels) {
                            Deliver deliver = (Deliver) key.attachment();
                            NioChannel nio_channel = new NioChannel(channel, deliver, null);
                            nio_channel.send(bytes, 0, length);
                        }

                        m_state = CONNECTED;
                        key.interestOps(SelectionKey.OP_READ);
                    } else if (key.isConnectable()) {
                        SocketChannel ch = (SocketChannel) key.channel();
                        ch.configureBlocking(false);
                        ch.socket().setTcpNoDelay(true);
                        ch.finishConnect();

                        ConnectCallback callbacks = (ConnectCallback) key.attachment();

                        // always set the READ interest.
                        m_sk_l.add(key);
                        m_peer.add(ch);
                        Deliver deliver = new Deliver();
                        key.attach(deliver);
                        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

//                        BroadcastThread broadcast_thread = new BroadcastThread(m_sk_l);
//                        broadcast_thread.start();

                        m_buf = ByteBuffer.allocate(4);
                        m_state = CONNECTED;
                        callbacks.connected(null);
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

        m_state = ACCEPTING;

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
        assert (getM_state() == DISCONNECTED);
        m_state = CONNECTING;

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

    /**
     * @return the m_peer
     */
    public Peer getM_peer() {
        return m_peer;
    }

    /**
     * @return the m_state
     */
    public int getM_state() {
        return m_state;
    }

    /**
     * @return the m_buf
     */
    public ByteBuffer getM_buf() {
        return m_buf;
    }

}
