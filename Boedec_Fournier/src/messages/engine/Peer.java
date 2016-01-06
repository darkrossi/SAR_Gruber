/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import sun.java2d.pipe.hw.AccelDeviceEventListener;

/**
 * Classe permettant de regrouper les channels associés aux peers connectés
 *
 * @author Mickaël
 */
public class Peer implements AcceptCallback, ConnectCallback, DeliverCallback {

    private List<Channel> m_channels;

    /**
     * Constructor
     */
    public Peer() {
        this.m_channels = new ArrayList<>();
    }

    /**
     * Ajout d'un channel à la liste
     *
     * @param channel
     */
    public void add(Channel channel) {
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
    public List<Channel> getChannels() {
        return m_channels;
    }

    /**
     * @param channels the channels to set
     */
    public void setChannels(List<Channel> channels) {
        this.m_channels = channels;
    }

    @Override
    public void accepted(Server server, Channel channel) {
        System.out.println("Je viens d'accepter.");
        this.m_channels.add(channel);
    }

    @Override
    public void closed(Channel channel) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void connected(Channel channel) {
        System.out.println("Je viens de me connecter.");
        this.m_channels.add(channel);

    }

    @Override
    public void deliver(Channel channel, byte[] bytes) {
//        System.out.print("Réception de " + ch.getRemoteAddress());
        System.out.println("Je viens d'envoyer ce message : ");
        for (int i = 0; i < bytes.length; i++) {
            System.out.print("\t" + bytes[i]);
        }
        System.out.println();
        System.out.flush();

    }

}
