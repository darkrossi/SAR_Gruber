/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TreeSet;

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

    private int m_timestamp;

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

//        MonitorMessagesToSend thread = new MonitorMessagesToSend(this, m_engine);
//        thread.start();
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
        byte type_message = bytes[5];

        /**
         * On a un message [timestamp (4) | id (4) | type (1) | data (?)] ou
         * [timestamp (4) | id (4) | type (1) | timestamp_ack (4) | IP (4) |
         * port (4)]
         */
        int port = 0;
        int indice_port = 4;
        if (type_message == 1) {
            indice_port = 17;
        }
        byte[] id_tab = new byte[4];
        System.arraycopy(bytes, indice_port, id_tab, 0, 4);
        port = ByteBuffer.wrap(id_tab).getInt();

        /**
         * Normalement l'InetAdress devrait être prise des bytes d'IP si ACK
         * Mais comme on est en local ça ne change rien
         */
        InetSocketAddress isa2 = new InetSocketAddress(channel.getRemoteAddress().getAddress(), port);
        Message message = new Message(isa2, bytes);

        /**
         * On set le timestamp
         */
        byte time_stamp_message = bytes[0];
        if (time_stamp_message > this.m_timestamp) {
            this.m_timestamp = time_stamp_message;
        }
        this.m_timestamp++;  // on a reçu un ACK ou un message de data

        InetSocketAddress isa = null;
        Message message_to_compare = null;

        /**
         * Si c'est de la data
         */
        if (type_message == 0) {
            this.m_messages.add(message);
        } else {
            synchronized (this.m_messages) {
                if (this.m_messages.contains(message)) {
                    Message final_message = this.getMessage(message);
                    final_message.increaseNumAck();
                }
            }
        }

        ArrayList<Message> listeMessages = new ArrayList<>(this.m_messages);
        int indice = 0;
        while (indice < listeMessages.size()) {
            Message msg_to_monitor = listeMessages.get(indice);
            if (msg_to_monitor.isReadyToDeliver(this.getNbPeers())) {
                byte[] byte_to_deliver = msg_to_monitor.getM_content();
                System.out.print("DELIVERED : \t");
                for (int i = 0; i < byte_to_deliver.length; i++) {
                    System.out.print("\t" + byte_to_deliver[i]);
                }
                System.out.println();
                System.out.flush();

                m_messages.remove(msg_to_monitor);
                indice++;

            } else {
                break;
            }
        }

//        this.m_engine.getM_selector().wakeup();
    }

    void read(InetSocketAddress isa) {
        Channel channel = this.getM_channels().get(isa);
        channel.read();
    }

    void send() {
        if (!this.m_message_to_send.isEmpty()) {
            byte[] msg_to_send = null;
            msg_to_send = this.m_message_to_send.removeFirst();

            /**
             * msg_to_send est de type [type (1) | data ()]
             */
            byte type_message_sent = msg_to_send[0];

            /**
             * On rajoute 8 cases pour le timestamp et l'id
             */
            byte finalBytes[] = new byte[msg_to_send.length + 8];

            /**
             * Ajout du timestamp
             */
            ByteBuffer b = ByteBuffer.allocate(4);
            b.putInt(this.m_timestamp);
            byte[] result = b.array();
            System.arraycopy(result, 0, finalBytes, 0, result.length);

            /**
             * Ajout de l'id
             */
            b = ByteBuffer.allocate(4);
            b.putInt(this.m_engine.m_port_listening);
            result = b.array();
            System.arraycopy(result, 0, finalBytes, 4, result.length);
            System.arraycopy(msg_to_send, 0, finalBytes, 8, msg_to_send.length);

            /**
             * On a créé un message [timestamp | id | type | data]
             */
            List<Channel> channels = new ArrayList<>(this.getM_channels().values());
            for (Channel channel : channels) {
                channel.send(finalBytes, 0, finalBytes.length);
            }
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

            }
        }
        return result;
    }

}
