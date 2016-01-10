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
import sun.nio.ch.SelChImpl;

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
         * On a un message [timestamp | type | data]
         */
        byte type_message_sent = bytes[1];
        byte timestamp_message_sent = bytes[0];

        ByteBuffer m_buf = ByteBuffer.allocate(4 + bytes.length);
        m_buf.putInt(bytes.length);
        m_buf.put(bytes, 0, bytes.length);
        m_buf.position(0);

        try {
//            System.out.println("MESSAGE SENT");
            m_ch.write(m_buf);
        } catch (IOException ex) {
            Logger.getLogger(NioChannel.class.getName()).log(Level.SEVERE, null, ex);
        }

        /**
         * Si c'est de la data qui a été envoyé alors on envoie aussi un ack
         */
        if (type_message_sent == 0) {
            System.out.println("ACK LOCAL_MESSAGE");
            byte bytes2[] = new byte[11];
            bytes2[2] = timestamp_message_sent;
            timestamp_message_sent++;
            bytes2[1] = 1;
            bytes2[0] = timestamp_message_sent;

            InetAddress ia = null;
            int port = 0;
            try {
                ia = ((InetSocketAddress) this.m_ch.getLocalAddress()).getAddress();
                port = ((InetSocketAddress) this.m_ch.getLocalAddress()).getPort();
            } catch (IOException ex) {
                Logger.getLogger(NioChannel.class.getName()).log(Level.SEVERE, null, ex);
            }
            byte[] tabIa = ia.getAddress();
            for (int i = 0; i < tabIa.length; i++) {
                bytes2[3 + i] = tabIa[i];
            }

            ByteBuffer b = ByteBuffer.allocate(4);
            b.putInt(port);
            byte[] result = b.array();
            for (int i = 0; i < result.length; i++) {
                byte c = result[i];
                bytes2[7 + i] = c;
            }

            this.send(bytes2, 0, bytes2.length);
        } else {
            System.out.println("ACK SENT");

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

//                    System.out.println("MESSAGE RECEIVED");
                    /**
                     * Si c'était de la data alors on envoie un ACK [timestamp |
                     * type | timestamp_ack | address]
                     */
                    byte type_message_received = bytes[1];
                    byte timestamp_message_received = bytes[0];
                    if (type_message_received == 0) {
                        System.out.println("ACK REMOTE_MESSAGE");

                        byte bytes2[] = new byte[10];
                        bytes2[1] = timestamp_message_received;
                        bytes2[0] = 1;

                        InetAddress ia = this.m_remoteAddress.getAddress();
                        byte[] tabIa = ia.getAddress();
                        for (int i = 0; i < tabIa.length; i++) {
                            bytes2[2 + i] = tabIa[i];
                        }

                        int port = this.m_remoteAddress.getPort();

                        ByteBuffer b = ByteBuffer.allocate(4);
                        b.putInt(port);
                        byte[] result = b.array();
                        for (int i = 0; i < result.length; i++) {
                            byte c = result[i];
                            bytes2[6 + i] = c;
                        }

                        m_peer.addMessageToSend(bytes2);

                        m_peer.send();
                    }

                    m_deliver.deliver(this, bytes);

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
