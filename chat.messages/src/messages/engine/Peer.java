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
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Mickaël
 */
public class Peer {

    private List<SocketChannel> channels;

    public Peer() {
        this.channels = new ArrayList<>();
    }

    public void add(SocketChannel channel) {
        getChannels().add(channel);
    }

    public int getNbPeers() {
        return getChannels().size();
    }

    /**
     * @return the channels
     */
    public List<SocketChannel> getChannels() {
        return channels;
    }

    /**
     * @param channels the channels to set
     */
    public void setChannels(List<SocketChannel> channels) {
        this.channels = channels;
    }

}
