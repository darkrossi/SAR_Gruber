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

/**
 *
 * @author Mickaël
 */
public class NioChannel extends Channel {
    
    SocketChannel m_ch;

    public NioChannel(SocketChannel channel) {
        this.m_ch = channel;
    }

    @Override
    public void setDeliverCallback(DeliverCallback callback) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void send(byte[] bytes, int offset, int length) {
//        assert (m_state == CONNECTED);

        ByteBuffer m_buf = ByteBuffer.allocate(4 + bytes.length);
        m_buf.putInt(bytes.length);
        m_buf.put(bytes, 0, bytes.length);
        m_buf.position(0);
        
        try {
            m_ch.write(m_buf);
//        m_state = SENDING;
        } catch (IOException ex) {
            Logger.getLogger(NioChannel.class.getName()).log(Level.SEVERE, null, ex);
        }

        // set the WRITE interest to be called back when writable,
        // but always leave the READ interest to avoid deadlocks.
        
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
