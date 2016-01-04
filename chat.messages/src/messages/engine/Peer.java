/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 *
 * @author Mickaël
 */
public class Peer implements AcceptCallback, ConnectCallback, DeliverCallback {

    private Engine m_engine;

    public Peer(Engine m_engine) {
        this.m_engine = m_engine;
    }

    @Override
    public void accepted(Server server, Channel channel) {
        System.out.println("I accepted");
    }

    @Override
    public void closed(Channel channel) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void connected(Channel channel) {
        System.out.println("I'm connected");
    }

    @Override
    public void deliver(Channel channel, byte[] bytes) {
        System.out.println("J'ai deliver");
    }

}
