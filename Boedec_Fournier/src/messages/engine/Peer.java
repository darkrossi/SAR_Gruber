/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    private TreeSet<Message> m_messages;

    private byte m_timestamp;

    private LinkedList<byte[]> m_message_to_send;

    NioEngine m_engine;

    /**
     * Constructor
     */
    public Peer(NioEngine engine) {
        this.m_channels = new HashMap<>();
        this.m_messages = new TreeSet<>();
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
        InetSocketAddress isa2 = new InetSocketAddress(channel.getRemoteAddress().getAddress(), channel.m_translation_port);
        Message message = new Message(isa2, bytes);
        Message final_message = null;

        byte type_message = message.getType();

        byte time_stamp_message = bytes[0];
        if (time_stamp_message > this.m_timestamp) {
            this.m_timestamp = time_stamp_message;
        }
        this.m_timestamp++;

        /**
         * Si c'est de la data
         */
        if (type_message == 0) {
            this.m_messages.add(message);
            final_message = getMessage(message);
        } else {
            byte timestamp_ack = bytes[2];

            byte[] ipAddr = new byte[]{bytes[3], bytes[4], bytes[5], bytes[6]};
            InetAddress addr = null;
            try {
                addr = InetAddress.getByAddress(ipAddr);
            } catch (UnknownHostException ex) {
                Logger.getLogger(Peer.class.getName()).log(Level.SEVERE, null, ex);
            }

            byte[] bytesInt = {bytes[7], bytes[8], bytes[9], bytes[10]};
            int port = ByteBuffer.wrap(bytesInt).getInt();
            InetSocketAddress isa = new InetSocketAddress(addr, port);

            Message message_to_compare = new Message(isa, bytes);

            synchronized (this.m_messages) {
                if (this.m_messages.contains(message_to_compare)) {
                    final_message = this.getMessage(message_to_compare);
                    final_message.increaseNumAck();
                }
            }
        }

//        ArrayList<Message> listeMessages = new ArrayList<>(this.m_messages);
//        int indice = 0;
//        while (indice < listeMessages.size()) {
//            Message msg_to_monitor = listeMessages.get(indice);
//            if (msg_to_monitor.isReadyToDeliver(this.getNbPeers())) {
//                byte[] byte_to_deliver = msg_to_monitor.getM_content();
//                for (int i = 0; i < byte_to_deliver.length; i++) {
//                    System.out.print("\t" + byte_to_deliver[i]);
//                }
//                System.out.println();
//                System.out.flush();
//
//                m_messages.remove(msg_to_monitor);
//                indice++;
//
//            } else {
//                break;
//            }
//        }
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

        if (type_message_sent == 0) {
            m_timestamp++;
        }
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

    public Message getMessage(Message msg) {
        Message result = null;
        synchronized (this.m_messages) {
            ArrayList<Message> listeMessages = new ArrayList<>(this.m_messages);
            int index = listeMessages.indexOf(msg);
            try {
                result = listeMessages.get(index);
            } catch (ArrayIndexOutOfBoundsException ex) {
                System.out.println("Here");
            }
        }
        return result;
    }

}
