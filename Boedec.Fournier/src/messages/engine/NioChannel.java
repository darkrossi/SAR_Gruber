/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Mickaël
 */
public class NioChannel extends Channel {

    int m_state_read, m_state_write;
    SelectionKey m_key;
    private Peer m_peer;

    byte m_seqno;

    private static final int READING_LENGTH = 2;
    private static final int READING_BYTES = 3;
    private static final int CONNECTED = 5;
    private static final int SENDING = 6;

    /**
     * Constructeur
     *
     * @param m_ch
     * @param deliver
     * @param key
     * @param isa
     * @param peer
     */
    public NioChannel(SocketChannel m_ch, DeliverCallback deliver, SelectionKey key, InetSocketAddress isa, Peer peer) {
        this.m_ch = m_ch;
        this.m_deliver = deliver;
        this.m_key = key;
        m_key.interestOps(SelectionKey.OP_READ);
        m_state_read = READING_LENGTH;
        m_buf_read = ByteBuffer.allocate(4);
        m_state_write = CONNECTED;
        m_remoteAddress = isa;
        m_peer = peer;

    }

    @Override
    public void setDeliverCallback(DeliverCallback callback) {
        this.m_deliver = callback;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return this.m_remoteAddress;
    }

    /**
     * On envoie un tableau de bytes au remote peer
     *
     * @param bytes
     * @param offset
     * @param length
     */
    @Override
    public void send(byte[] bytes, int offset, int length) {
        assert (m_state_write == CONNECTED);
        m_peer.m_engine.writeCount++;
        m_state_write = SENDING;

        /**
         * On a un message [timestamp (4) | id (4) | type (1) | data (?)]
         */
        byte type_message_sent = bytes[8];

        /**
         * On ajout la taille du message en tête
         */
        ByteBuffer m_buf = ByteBuffer.allocate(4 + bytes.length);
        m_buf.putInt(bytes.length);
        m_buf.put(bytes, 0, bytes.length);
        m_buf.position(0);

        try {
            m_ch.write(m_buf);
        } catch (IOException ex) {
            m_peer.imDead(this);
            return;
        }

        /**
         * Si c'est de la data qui a été envoyé alors on envoie aussi un ack de
         * taille 13 [ type (1) | timestamp_ack (4) | IP (4) | port (4)]
         */
        if (type_message_sent == 0 || type_message_sent == 3 || type_message_sent == 1) {
            m_deliver.deliver(this, bytes);
        } else {
//            Message message = new Message(null, bytes);
//            System.out.print("SENT : \t\t");
//            System.out.print("\t" + message);
//            System.out.println();
//            System.out.flush();
        }

        m_state_write = CONNECTED;
        m_key.interestOps(SelectionKey.OP_READ);

    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Permet de lire un tableau de bytes entrant : d'abord la longeur puis le
     * contenu
     */
    @Override
    public void read() {
        m_peer.m_engine.readCount++;
        try {
            int len, count = 0;
            switch (m_state_read) {
                case READING_LENGTH:
                    count = m_ch.read(m_buf_read);
                    if (count == -1) {
                        System.err.println("End of stream!");
                        System.exit(-1);
                    }
                    if (m_buf_read.hasRemaining()) {
                        return;
                    }
                    m_state_read = READING_BYTES;
                    m_buf_read.position(0);
                    len = m_buf_read.getInt();
                    m_buf_read = ByteBuffer.allocate(len);
                case READING_BYTES:
                    count = m_ch.read(m_buf_read);
                    if (count == -1) {
                        System.err.println("End of stream!");
                        System.exit(-1);
                    }
                    if (m_buf_read.hasRemaining()) {
                        return;
                    }

                    m_buf_read.position(0);
                    byte bytes[] = new byte[m_buf_read.remaining()];
                    m_buf_read.get(bytes);

                    m_state_read = READING_LENGTH;
                    m_buf_read = ByteBuffer.allocate(4);

                    for (int i = 0; i < bytes.length; i++) {
                        assert (m_seqno++ == bytes[i]);
                    }

                    byte type_message_received = bytes[8];

                    /**
                     * On a reçu un message de la forme [timestamp (4) | id (4)
                     * | type (1) | data (?)] si data [timestamp (4) | id (4) |
                     * type (1) | timestamp_ack (4) | IP (4) | port (4)] si ACK
                     */
                    // SI ce n'est pas un HELLO1 ni un EXISTING_PEERS ni un EXISTING_MESSAGES alors on deliver
                    if (type_message_received != 2 && type_message_received != 4 && type_message_received != 5) {
                        m_deliver.deliver(this, bytes);
                    }

                    Message message;
                    /**
                     * Si c'est de la data qui a été envoyé alors on envoie
                     * aussi un ack de taille 13 [ type (1) | timestamp_ack (4)
                     * | IP (4) | port (4)]
                     */
                    switch (type_message_received) {
                        case 0:
                        case 3:
//                        System.out.println("ACK REMOTE_MESSAGE");

                            byte bytes2[] = new byte[9];
                            bytes2[0] = 1;
                            System.arraycopy(bytes, 0, bytes2, 1, 4); // On copie le timestamp
                            System.arraycopy(bytes, 4, bytes2, 5, 4); // On copie le port à la fin

                            m_peer.addMessageToSend(bytes2);
                            m_peer.send();
                            break;
                        case 2:

                            /**
                             * Si on reçoit un message de type 2 alors il faut
                             * envoyer un message de type 3 à tout le monde
                             */
                            message = new Message(null, bytes);

//                            System.out.print("RECEIVED : \t");
//                            System.out.print("\t" + message);
//                            System.out.println();
//                            System.out.flush();

                            /**
                             * Il faut envoyer un message hello2
                             */
                            /**
                             * Ajout de l'id
                             */
                            int length = 4;
                            bytes = new byte[length];
                            ByteBuffer b = ByteBuffer.allocate(4);
                            b.putInt(Integer.parseInt(message.getData()));
                            bytes = b.array();

                            /**
                             * On ajoute en tête du message le type de message 0
                             * = DATA, 1 = ACK, 2 = HELLO1, 3 = HELLO2
                             */
                            byte finalBytes[] = new byte[bytes.length + 1];
                            finalBytes[0] = 3;
                            System.arraycopy(bytes, 0, finalBytes, 1, bytes.length);

                            m_peer.addMessageToSend(finalBytes);
                            m_peer.send();
                            break;
                        case 4:
                            message = new Message(null, bytes);

//                            System.out.print("RECEIVED : \t");
//                            System.out.print("\t" + message);
//                            System.out.println();
//                            System.out.flush();

                            List<Message> list_messages = message.getExistingMessages();
//                            list_messages = new ArrayList<>(); // ligne debug
                            if (list_messages != null && !list_messages.isEmpty()) {
                                for (Message message_var : list_messages) {
                                    m_peer.m_messages.add(message_var);
                                }
                            }

                            break;
                        case 5:
                            message = new Message(null, bytes);

//                            System.out.print("RECEIVED : \t");
//                            System.out.print("\t" + message);
//                            System.out.println();
//                            System.out.flush();

                            String id_peers = message.getData();
                            String[] tab_id_peers = id_peers.split(" - ");

                            int time_stamp_message = message.getM_timestamp();
                            if (time_stamp_message > this.m_peer.m_timestamp) {
                                this.m_peer.m_timestamp = time_stamp_message;
                            }

                            /**
                             * -1 car on ne prend pas le dernier
                             */
                            for (int i = 0; i < tab_id_peers.length; i++) {
                                int id_peer = Integer.parseInt(tab_id_peers[i]);
                                if (id_peer != m_peer.m_engine.m_port_listening) {
                                    InetAddress m_localhost = InetAddress.getByName("localhost");
//                                    System.out.println("On tente de se connecter");
                                    m_peer.m_engine.connect(m_localhost, id_peer, m_peer);
                                }
                            }
                            m_peer.m_engine.broadcast_thread = new BroadcastThread(this.m_peer, this.m_peer.m_engine, this.m_peer.m_engine.paquet_size);
                            m_peer.m_engine.broadcast_thread.start();
                            this.m_peer.m_engine.thread_launched = true;
                            break;
                    }
                    break;
            }
            m_key.interestOps(SelectionKey.OP_READ);
        } catch (IOException ex) {
            m_peer.imDead(this);
        }
    }

    /**
     * Set le statut de la SelectionKey associée
     */
    @Override
    public void sending() {
        m_key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

}
