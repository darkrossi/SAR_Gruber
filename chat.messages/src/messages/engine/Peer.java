/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe permettant de regrouper les channels associés aux peers connectés
 * @author Mickaël
 */
public class Peer {

    private List<SocketChannel> channels;

    /**
     * Constructor
     */
    public Peer() {
        this.channels = new ArrayList<>();
    }

    /**
     * Ajout d'un channel à la liste
     * @param channel 
     */
    public void add(SocketChannel channel) {
        getChannels().add(channel);
    }

    /**
     * 
     * @return le nombre de clients connectés
     */
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
