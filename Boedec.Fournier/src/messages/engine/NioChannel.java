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

    byte m_seqno;

    private static final int DISCONNECTED = 0;
    private static final int ACCEPTING = 1;
    private static final int READING_LENGTH = 2;
    private static final int READING_BYTES = 3;
    private static final int CONNECTING = 4;
    private static final int CONNECTED = 5;
    private static final int SENDING = 6;

    private Peer m_peer;

    /**
     * Constructor
     *
     * @param m_ch
     * @param deliver
     * @param remoteAddress
     */
    public NioChannel(SocketChannel m_ch, DeliverCallback deliver, SelectionKey key, InetSocketAddress isa, Peer peer) {
        this.m_ch = m_ch;
        this.m_deliver = deliver;
//        this.m_remoteAddress = m_ch.socket().get;
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

    @Override
    public void send(byte[] bytes, int offset, int length) {
        assert (m_state_write == CONNECTED);
        m_state_write = SENDING;

        /**
         * On a un message [timestamp | id | type | data] [1 | 4 | 1 | ?]
         */
        byte timestamp_message_sent = bytes[0];
        byte type_message_sent = bytes[5];

        ByteBuffer m_buf = ByteBuffer.allocate(4 + bytes.length);
        m_buf.putInt(bytes.length);
        m_buf.put(bytes, 0, bytes.length);
        m_buf.position(0);

        synchronized (System.out) {
            System.out.print("SENT \t");
        }
        for (int i = 0; i < bytes.length; i++) {
            System.out.print("\t" + bytes[i]);
        }
        System.out.println();
        System.out.flush();

        try {
            m_ch.write(m_buf);
        } catch (IOException ex) {
            Logger.getLogger(NioChannel.class.getName()).log(Level.SEVERE, null, ex);
        }

        /**
         * Si c'est de la data qui a été envoyé alors on envoie aussi un ack de
         * taille 10 (2 pour type | timestamp_ack et 8 pour IP | port)
         */
        if (type_message_sent == 0) {

            m_deliver.deliver(this, bytes);

            byte bytes2[] = new byte[2 + 8];
            bytes2[1] = timestamp_message_sent;
            bytes2[0] = 1;
            System.arraycopy(bytes, 1, bytes2, 6, 4); // On copie le port à la fin

            InetAddress ia = null;
            try {
                ia = ((InetSocketAddress) this.m_ch.getLocalAddress()).getAddress();
            } catch (IOException ex) {
                Logger.getLogger(NioChannel.class.getName()).log(Level.SEVERE, null, ex);
            }

            byte[] tabIa = ia.getAddress();
            try {
                System.arraycopy(tabIa, 0, bytes2, 2, 4); // On copie l'IP
            } catch (ArrayIndexOutOfBoundsException ex) {
                System.out.println("Here");
            }

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

    @Override
    public void read() {
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

                    synchronized (System.out) {
                        System.out.print("RECEIVED");
                    }
                    for (int i = 0; i < bytes.length; i++) {
                        System.out.print("\t" + bytes[i]);
                    }
                    System.out.println();
                    System.out.flush();

                    m_deliver.deliver(this, bytes);

//                    System.out.println("MESSAGE RECEIVED");
                    byte timestamp_message_received = bytes[0];
                    byte type_message_received = bytes[5];
                    byte[] id_tab = new byte[4];
                    try {
                        System.arraycopy(bytes, 1, id_tab, 0, 4);
                    } catch (ArrayIndexOutOfBoundsException ex) {
                        System.out.println("Here");
                    }

                    /**
                     * Si c'est de la data qui a été envoyé alors on envoie
                     * aussi un ack de taille 10 (2 pour type | timestamp_ack et
                     * 8 pour IP | port)
                     */
                    if (type_message_received == 0) {
//                        System.out.println("ACK REMOTE_MESSAGE");

                        byte bytes2[] = new byte[2 + 8];
                        bytes2[1] = timestamp_message_received;
                        bytes2[0] = 1;
                        System.arraycopy(id_tab, 0, bytes2, 6, 4); // On copie le port à la fin

                        InetAddress ia = null;
                        try {
                            ia = ((InetSocketAddress) this.m_ch.getLocalAddress()).getAddress();
                        } catch (IOException ex) {
                            Logger.getLogger(NioChannel.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        byte[] tabIa = ia.getAddress();
                        try {
                            System.arraycopy(tabIa, 0, bytes2, 2, 4); // On copie l'IP
                        } catch (ArrayIndexOutOfBoundsException ex) {
                            System.out.println("Here");
                        }

                        m_peer.addMessageToSend(bytes2);
                        m_peer.send();
                    }

                    m_key.interestOps(SelectionKey.OP_READ);
            }
        } catch (IOException ex) {
            Logger.getLogger(NioChannel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void sending() {
        m_key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

}
