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
import java.util.logging.Level;
import java.util.logging.Logger;

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
        if (type_message_sent == 0) {

            m_deliver.deliver(this, bytes);

            byte bytes2[] = new byte[13];
            bytes2[0] = 1;
            System.arraycopy(bytes, 0, bytes2, 1, 4); // On copie le timestamp
            System.arraycopy(bytes, 4, bytes2, 9, 4); // On copie le port à la fin

            InetAddress ia = null;
            try {
                ia = ((InetSocketAddress) this.m_ch.getLocalAddress()).getAddress();
            } catch (IOException ex) {
                Logger.getLogger(NioChannel.class.getName()).log(Level.SEVERE, null, ex);
            }

            byte[] tabIa = ia.getAddress();
            System.arraycopy(tabIa, 0, bytes2, 5, 4); // On copie l'IP

            m_peer.addMessageToSend(bytes2);
            m_peer.send();
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

                    /**
                     * On a reçu un message de la forme [timestamp (4) | id (4)
                     * | type (1) | data (?)] si data et [timestamp (4) | id (4)
                     * | type (1) | timestamp_ack (4) | IP (4) | port (4)] si
                     * ACK
                     */
                    m_deliver.deliver(this, bytes);

                    byte type_message_received = bytes[8];

                    /**
                     * Si c'est de la data qui a été envoyé alors on envoie
                     * aussi un ack de taille 13 [ type (1) | timestamp_ack (4)
                     * | IP (4) | port (4)]
                     */
                    if (type_message_received == 0) {
//                        System.out.println("ACK REMOTE_MESSAGE");

                        byte bytes2[] = new byte[13];
                        bytes2[0] = 1;
                        System.arraycopy(bytes, 0, bytes2, 1, 4); // On copie le timestamp
                        System.arraycopy(bytes, 4, bytes2, 9, 4); // On copie le port à la fin

                        InetAddress ia = null;
                        try {
                            ia = ((InetSocketAddress) this.m_ch.getLocalAddress()).getAddress();
                        } catch (IOException ex) {
                            Logger.getLogger(NioChannel.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        byte[] tabIa = ia.getAddress();
                        System.arraycopy(tabIa, 0, bytes2, 5, 4); // On copie l'IP

                        m_peer.addMessageToSend(bytes2);
                        m_peer.send();
                    }

                    m_key.interestOps(SelectionKey.OP_READ);
            }
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
