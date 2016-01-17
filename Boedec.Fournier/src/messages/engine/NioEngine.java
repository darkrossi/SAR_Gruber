/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
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

    private Selector m_selector; // Le Selector qui va être créé dans le constructeur de classe pour manager les channels
    ServerSocketChannel m_sch; // Channel d'écoute de connexion
    int m_port_listening; // The port d'écoute

    boolean thread_launched = false;
    InetAddress m_localhost = InetAddress.getByName("localhost");

    boolean m_has_accept = false;

    BroadcastThread broadcast_thread;
    int paquet_size;

    /**
     * Constructor
     *
     * @throws IOException
     */
    NioEngine(int paquet_size) throws IOException {
        m_selector = SelectorProvider.provider().openSelector();
        this.paquet_size = paquet_size;

    }

    /**
     * Allows to register a selectable channel with the NIO selector.
     *
     * @param ch
     * @param callbacks
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

    /**
     * Cette boucle infinie permet de gérer les SelectionKey du Selector
     */
    @Override
    public void mainloop() {
        try {
            long delay = 0;
            while (m_running) {
                getM_selector().select(delay);
                Iterator<?> selectedKeys = this.getM_selector().selectedKeys().iterator();
                if (selectedKeys.hasNext()) {
                    SelectionKey key = (SelectionKey) selectedKeys.next();
                    selectedKeys.remove();
                    if (!key.isValid()) {
                        continue;
                    } else if (key.isAcceptable()) {
                        acceptCount++;
                        m_has_accept = true;

                        /**
                         * Si on accepte une connexion alors on crée un
                         * SocketChannel associé, on l'enregistre dans le
                         * Selector et on appelle le deliver
                         */
                        ServerSocketChannel channel_server = (ServerSocketChannel) key.channel();

                        // Création de la SocketChannel
                        SocketChannel channel = channel_server.accept(); // On crée un channel avec celui qui arrive
                        channel.configureBlocking(false);
                        channel.socket().setTcpNoDelay(true);

                        // On enregistre ce SocketChannel dans le Selector
                        Peer peer = (Peer) key.attachment();
                        SelectionKey sk = register(channel, peer, SelectionKey.OP_READ);

                        // On crée un Channel associé à la connexion et on fait appel au Deliver
                        InetSocketAddress isa = (InetSocketAddress) channel.getRemoteAddress();
                        NioChannel nio_channel = new NioChannel(channel, peer, sk, isa, peer);
                        peer.accepted(null, nio_channel);

                    } else if (key.isReadable()) {
                        /**
                         * Si le channel est prêt à lire du contenu alors on
                         * appelle le Peer pour qu'il prenne le relais
                         */
                        SocketChannel m_ch = (SocketChannel) key.channel();
                        InetSocketAddress isa = (InetSocketAddress) m_ch.getRemoteAddress();

                        // Le Peer prend le relais
                        Peer peer = (Peer) key.attachment();
                        boolean isOk = peer.read(isa);

                        /**
                         * Si jamais il y a un problème de connexion alors on
                         * supprime le channel
                         */
                        if (!isOk) {
                            m_selector.selectedKeys().remove(key);
                        }

                    } else if (key.isWritable()) {
                        /**
                         * Si le channel est prêt à écrire du contenu alors on
                         * appelle le Peer pour qu'il prenne le relais
                         */

                        // Le Peer prend le relais
                        Peer peer = (Peer) key.attachment();
                        synchronized (System.out) {
                            peer.send();
                        }

                    } else if (key.isConnectable()) {
                        connectCount++;
                        /**
                         * Si on se connecte à un peer alors on crée un
                         * SocketChannel et on appelle le Peer pour qu'il prenne
                         * le relais
                         */

                        // On crée le SocketChannel
                        SocketChannel ch = (SocketChannel) key.channel();
                        ch.configureBlocking(false);
                        ch.socket().setTcpNoDelay(true);
                        try {
                            ch.finishConnect();

                            // On set le statut de la SelectionKey
                            key.interestOps(SelectionKey.OP_READ);

                            // On appelle le Peer qui prend le relais
                            Peer peer = (Peer) key.attachment();
                            InetSocketAddress isa = (InetSocketAddress) ch.getRemoteAddress();
                            NioChannel nio_channel = new NioChannel(ch, peer, key, isa, peer);
                            peer.connected(nio_channel);

                        } catch (ConnectException ex) {
                            /**
                             * Si jamais il y a un problème de connexion alors
                             * on supprime le channel
                             */
                        }
                    }
                }
            }
            broadcast_thread.m_running = false;
        } catch (IOException ex) {
            System.err.println("NioEngine got an exeption: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(-1);
        }
    }

    /**
     * Crée une socket d'écoute
     *
     * @param port
     * @param callback
     * @return
     * @throws IOException
     */
    @Override
    public Server listen(int port, AcceptCallback callback) throws IOException {

        m_port_listening = port;

        /**
         * On crée le fichier de Stat associé.
         */
        File f = new File(String.valueOf("Stats" + port + ".txt"));
        try {
            this.pw = new PrintWriter(new BufferedWriter(new FileWriter(f)));
        } catch (IOException exception) {
            System.out.println("Erreur lors de la lecture : " + exception.getMessage());
            System.exit(-1);
        }
        startTime = System.currentTimeMillis();

        /**
         * On peut lancer le thread d'écoute des stats.
         */
        super.startEcho();

        /**
         * On crée une socket d'écoute
         */
        m_sch = ServerSocketChannel.open();
        m_sch.configureBlocking(false);
        InetSocketAddress isa = new InetSocketAddress(m_localhost, m_port_listening);
        m_sch.socket().bind(isa);

        // On enregistre cette Socket dans le Selector
        register(m_sch, callback, SelectionKey.OP_ACCEPT);

        NioServer nio_server = new NioServer(m_port_listening);
        return nio_server;
    }

    /**
     *
     * @param hostAddress
     * @param port
     * @param callback
     * @throws UnknownHostException
     * @throws SecurityException
     * @throws ClosedChannelException
     * @throws IOException
     */
    @Override
    public void connect(InetAddress hostAddress, int port, ConnectCallback callback) throws UnknownHostException, SecurityException, ClosedChannelException, IOException {
        /**
         * On tente de se connecter sur un peer à l'adresse hostAddress:port
         */
        SocketChannel m_ch = SocketChannel.open();
        m_ch.configureBlocking(false);
        m_ch.socket().setTcpNoDelay(true);
        m_ch.connect(new InetSocketAddress(hostAddress, port));

        // On enregistre le SocketChannel associé dans le Selector
        register(m_ch, callback, SelectionKey.OP_CONNECT);

    }

    /**
     * @return the m_selector
     */
    public Selector getM_selector() {
        return m_selector;
    }
}
