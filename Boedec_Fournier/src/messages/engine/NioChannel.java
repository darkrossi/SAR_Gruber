/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.io.IOException;
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

    SocketChannel m_ch;
    DeliverCallback m_deliver;
    InetSocketAddress m_remoteAddress;

    ByteBuffer m_buf_read, m_buf_write;
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

    /**
     * Constructor
     *
     * @param m_ch
     * @param deliver
     * @param remoteAddress
     */
    public NioChannel(SocketChannel m_ch, DeliverCallback deliver, SelectionKey key) {
        this.m_ch = m_ch;
        this.m_deliver = deliver;
//        this.m_remoteAddress = m_ch.socket().get;
        this.m_key = key;
        m_key.interestOps(SelectionKey.OP_READ);
        m_state_read = READING_LENGTH;
        m_buf_read = ByteBuffer.allocate(4);
        m_state_write = CONNECTED;
        m_buf_write = ByteBuffer.allocate(4);
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

        ByteBuffer m_buf = ByteBuffer.allocate(4 + bytes.length);
        m_buf.putInt(bytes.length);
        m_buf.put(bytes, 0, bytes.length);
        m_buf.position(0);

        try {
            m_ch.write(m_buf);
        } catch (IOException ex) {
            Logger.getLogger(NioChannel.class.getName()).log(Level.SEVERE, null, ex);
        }

        m_state_write = CONNECTED;
        m_key.interestOps(SelectionKey.OP_READ);

        m_deliver.deliver(this, bytes);
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

                    System.out.println("Je viens de recevoir ce message : ");
                    for (int i = 0; i < bytes.length; i++) {
                        System.out.print("\t" + bytes[i]);
                    }
                    System.out.println();
                    System.out.flush();
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
