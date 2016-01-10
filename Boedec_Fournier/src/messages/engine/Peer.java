/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import sun.java2d.pipe.hw.AccelDeviceEventListener;

/**
 * Classe permettant de regrouper les channels associés aux peers connectés
 *
 * @author Mickaël
 */
public class Peer implements AcceptCallback, ConnectCallback, DeliverCallback {

    private HashMap<InetSocketAddress, Channel> m_channels;

    /**
     * Les messages reçus mais non délivrés sont représentés par une clé qui est
     * le timestamp
     */
    private TreeMap<Byte, Message> m_messages;

    private byte m_timestamp;

    private LinkedList<byte[]> m_message_to_send;

    private NioEngine m_engine;

    /**
     * Constructor
     */
    public Peer(NioEngine engine) {
        this.m_channels = new HashMap<>();
        this.m_messages = new TreeMap<>();
        this.m_timestamp = 0;
        this.m_message_to_send = new LinkedList<>();
        this.m_engine = engine;
    }

    /**
     * Ajout d'un channel à la liste
     *
     * @param channel
     */
    public void add(Channel channel) {
        this.getM_channels().put(channel.getRemoteAddress(), channel);
    }

    /**
     *
     * @return le nombre de clients connectés
     */
    public int getNbPeers() {
        return this.getM_channels().size();
    }

    @Override
    public void accepted(Server server, Channel channel) {
        System.out.println("Je viens d'accepter.");
        this.getM_channels().put(channel.getRemoteAddress(), channel);
    }

    @Override
    public void closed(Channel channel) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void connected(Channel channel) {
        System.out.println("Je viens de me connecter.");
        this.getM_channels().put(channel.getRemoteAddress(), channel);

    }

    /**
     * Il faut le premier élément du paramètre bytes soit le timestamp
     *
     * @param channel
     * @param bytes
     */
    @Override
    public void deliver(Channel channel, byte[] bytes) {
        Message message = new Message(channel.getRemoteAddress(), 0, bytes);
        Message final_message = null;

        byte type_message = message.getType();
        byte timestamp_message = message.getM_timestamp();
        byte timestamp_ack = bytes[2];
        /**
         * Si c'est de la data
         */
        if (type_message == 0) {
            this.m_messages.put(bytes[0], message);
            final_message = this.getMessage(timestamp_message);
        } else if (this.m_messages.containsKey(timestamp_ack)) {
            final_message = this.getMessage(timestamp_ack);
            final_message.increaseNumAck();
        } else {
            // Never happens if it works well
        }

        /**
         * final_message est forcément initialisé en arrivant ici
         */
        if (final_message != null && final_message.isReadyToDeliver(this.getNbPeers())) {
            byte[] byte_to_deliver = final_message.getM_content();
            for (int i = 0; i < byte_to_deliver.length; i++) {
                System.out.print("\t" + byte_to_deliver[i]);
            }
            System.out.println();
            System.out.flush();
        }
//        System.out.print("On reçoit : ");
//        for (int i = 0; i < bytes.length; i++) {
//            System.out.print("\t" + bytes[i]);
//        }
//        System.out.println();
//        System.out.flush();

    }

    void read(InetSocketAddress isa) {
        Channel channel = this.getM_channels().get(isa);
        channel.read();
    }

    void send() {
        m_timestamp++;
        byte[] msg_to_send = this.m_message_to_send.removeFirst();
        byte type_message_sent = msg_to_send[0];

        byte finalBytes[] = new byte[msg_to_send.length + 1];
        finalBytes[0] = m_timestamp;
        System.arraycopy(msg_to_send, 0, finalBytes, 1, msg_to_send.length);

        /**
         * On a créé un message [timestamp | type | data]
         */
        List<Channel> channels = new ArrayList<>(this.getM_channels().values());
        for (Channel channel : channels) {
            channel.send(finalBytes, 0, finalBytes.length);
        }
        
        if(type_message_sent == 0) m_timestamp++;
    }

    /**
     * @return the m_channels
     */
    public HashMap<InetSocketAddress, Channel> getM_channels() {
        return m_channels;
    }

    public void addMessageToSend(byte[] bytes) {
        this.m_message_to_send.addLast(bytes);
    }

    public Message getMessage(byte timestamp) {
        Message message = this.m_messages.get(timestamp);
        return message;
    }

}
