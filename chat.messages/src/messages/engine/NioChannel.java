/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Mickaël
 */
public class NioChannel extends Channel {

    SocketChannel m_ch;
    DeliverCallback deliver;
    InetSocketAddress remoteAddress;

    /**
     * Constructor
     * @param m_ch
     * @param deliver
     * @param remoteAddress 
     */
    public NioChannel(SocketChannel m_ch, DeliverCallback deliver, InetSocketAddress remoteAddress) {
        this.m_ch = m_ch;
        this.deliver = deliver;
        this.remoteAddress = remoteAddress;
    }

    @Override
    public void setDeliverCallback(DeliverCallback callback) {
        this.deliver = callback;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return this.remoteAddress;
    }

    @Override
    public void send(byte[] bytes, int offset, int length) {
        ByteBuffer m_buf = ByteBuffer.allocate(4 + bytes.length);
        m_buf.putInt(bytes.length);
        m_buf.put(bytes, 0, bytes.length);
        m_buf.position(0);

        try {
            m_ch.write(m_buf);
        } catch (IOException ex) {
            Logger.getLogger(NioChannel.class.getName()).log(Level.SEVERE, null, ex);
        }

        deliver.deliver(this, bytes);
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
