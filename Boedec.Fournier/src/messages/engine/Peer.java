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
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Classe permettant de regrouper les channels associés aux peers connectés
 *
 * @author Mickaël
 */
public class Peer implements AcceptCallback, ConnectCallback, DeliverCallback {

    private HashMap<InetSocketAddress, Channel> m_channels;
    /**
     * Les messages reçus mais non délivrés
     */
    SortedSet<Message> m_messages;
    private LinkedList<byte[]> m_message_to_send;

    NioEngine m_engine;
    int m_timestamp;

    FileThread m_file_thread;
    Channel m_new_peer_channel;

    /**
     * Constructeur
     *
     * @param engine
     */
    public Peer(NioEngine engine, FileThread file_thread) {
        this.m_channels = new HashMap<>();
        this.m_messages = new TreeSet<>(new MessageComparator());
        this.m_timestamp = 0;
        this.m_message_to_send = new LinkedList<>();
        this.m_engine = engine;

        this.m_file_thread = file_thread;
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

    /**
     * Ajoute un Channel à la liste
     *
     * @param server
     * @param channel
     */
    @Override
    public void accepted(Server server, Channel channel) {
        this.getM_channels().put(channel.getRemoteAddress(), channel);
        // Incrémenter le nombre de acks pour tous les messages non délivrés
        for (Message msg : m_messages) {
            msg.increaseNumAck(0);
        }
        if (m_engine.thread_launched) {
            m_engine.broadcast_thread.m_interrupted = false;
        }

        m_new_peer_channel = channel;
    }

    @Override
    public void closed(Channel channel) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Ajoute un Channel à la liste
     *
     * @param channel
     */
    @Override
    public void connected(Channel channel) {
        this.getM_channels().put(channel.getRemoteAddress(), channel);

        /**
         * Si on vient de se connecter (on veut rentrer dans le groupe)
         */
        if (this.getM_channels().size() == 1) {
            /**
             * Il faut envoyer un message hello
             */
            /**
             * Ajout de la data
             */
            int length = 4;
            byte bytes[] = new byte[length];
            ByteBuffer b = ByteBuffer.allocate(4);
            b.putInt(this.m_engine.m_port_listening);
            bytes = b.array();

            /**
             * On ajoute en tête du message le type de message 0 = DATA, 1 =
             * ACK, 2 = HELLO1, 3 = HELLO2
             */
            byte finalBytes[] = new byte[bytes.length + 1];
            finalBytes[0] = 2;
            System.arraycopy(bytes, 0, finalBytes, 1, bytes.length);

            byte finalBytes2[] = headerMessage(finalBytes, this.m_timestamp, this.m_engine.m_port_listening);

            channel.send(finalBytes2, 0, finalBytes2.length);
        } else if (m_engine.thread_launched) {
            this.m_engine.connectCount++;
            m_engine.broadcast_thread.m_interrupted = false;
        }

    }

    /**
     * Il faut le premier élément du paramètre bytes soit le timestamp
     *
     * @param channel
     * @param bytes
     */
    @Override
    public void deliver(Channel channel, byte[] bytes) {
        byte type_message = bytes[8];

        /**
         * On a un message [timestamp (4) | id (4) | type (1) | data (?)] ou
         * [timestamp (4) | id (4) | type (1) | timestamp_ack (4) | IP (4) |
         * port (4)]
         */
        int port = 0;
        int indice_port = 4;
        if (type_message == 1) {
            indice_port = 13;
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
        int time_stamp_message = message.getM_timestamp();
        if (time_stamp_message > this.m_timestamp) {
            this.m_timestamp = time_stamp_message;
        }
        this.m_timestamp++;  // on a reçu un ACK ou un message de data

//        if (message.getM_id() != this.m_engine.m_port_listening) {
//            System.out.print("RECEIVED : \t");
//            System.out.print("\t" + message);
//            System.out.println();
//            System.out.flush();
//        }
        /**
         * Si c'est de la data
         */
        if (type_message != 1) {
            this.m_messages.add(message);
        } else {
            synchronized (this.m_messages) {
                Message final_message = this.getMessage(message);
                if (final_message != null) {
                    final_message.increaseNumAck(message.getM_id());
                }
            }
        }

        /**
         * On parcours les messages reçus en commençant par le premier pour voir
         * si on peut le délivrer
         */
        ArrayList<Message> listeMessages = new ArrayList<>(this.m_messages);
        int indice = 0;
        while (indice < listeMessages.size()) {
            Message msg_to_monitor = listeMessages.get(indice);
            /**
             * Peut-on délivrer msg_to_monitor ?
             */
            if (msg_to_monitor.isReadyToDeliver(this.getNbPeers(), m_engine.m_port_listening)) {
                byte[] byte_to_deliver = msg_to_monitor.getM_content();
//                System.out.print("DELIVERED : \t");
//                System.out.print("\t" + msg_to_monitor);
//                System.out.println();
//                System.out.flush();

                m_file_thread.addDeliveredMessage(byte_to_deliver);

                m_messages.remove(msg_to_monitor);

                /**
                 * Si c'est un type 3 alors il faut bloquer le système
                 */
                if (msg_to_monitor.getType() == 3) {
                    if (m_engine.thread_launched) {
//                        System.out.println("Thread de broadcast interrompu");

                        m_engine.broadcast_thread.m_interrupted = true;
                    }
                }

                if (msg_to_monitor.getType() == 3 && this.m_engine.m_port_listening == msg_to_monitor.getM_id()) {
                    // On bloque le système pour tout le monde
                    // Le port 2005 doit envoyer les peers et les messages non délivrés au petit nouveau

                    // Incrémenter le nombre de acks pour tous les messages non délivrés
                    for (Message msg : this.m_messages) {
                        msg.increaseNumAck(0);
                    }

                    //
                    // 1. On envoie les messages non délivrés par le groupe
                    //
                    int length = 0;
                    for (Message message_var : this.m_messages) {
                        byte[] content = message_var.getM_content();
                        length += content.length + 4 + 1;
                    }

                    /**
                     * On écrit d'abord la taille du message puis le nombre de
                     * ack puis le message
                     */
                    bytes = new byte[length];
                    int indice2 = 0;
                    for (Message message_var : this.m_messages) {
                        byte[] content = message_var.getM_content();

                        ByteBuffer b = ByteBuffer.allocate(4);
                        b.putInt(content.length + 1);
                        byte[] result = b.array();
                        System.arraycopy(result, 0, bytes, indice2, 4);
                        indice2 += 4;

                        bytes[indice2] = (byte) message_var.getM_num_ack();
                        indice2++;

                        System.arraycopy(content, 0, bytes, indice2, content.length);
                        indice2 += content.length;
                    }

                    /**
                     * On ajoute en tête du message le type de message 0 = DATA,
                     * 1 = ACK, 2 = HELLO1, 3 = HELLO2, 5 = EXISTING_PEERS, 4 =
                     * EXISTING_MESSAGES
                     */
                    byte[] finalBytes = new byte[bytes.length + 1];
                    finalBytes[0] = 4;
                    /**
                     * Si il y a de la data
                     */
                    if (finalBytes.length != 1) {
                        System.arraycopy(bytes, 0, finalBytes, 1, bytes.length);
                    }
                    byte[] finalBytes2 = headerMessage(finalBytes, this.m_timestamp, this.m_engine.m_port_listening);

//                    Message message_debug = new Message(null, finalBytes2);
                    this.m_new_peer_channel.send(finalBytes2, 0, finalBytes2.length);

                    // 2. On envoie les peers
                    /**
                     * On crée la data
                     */
                    length = 4 * this.m_channels.size();
                    bytes = new byte[length];
                    List<Integer> list_listening_ports = msg_to_monitor.getM_id_acks();
                    indice2 = 0;
                    for (Integer listening_port : list_listening_ports) {
                        if (listening_port != m_engine.m_port_listening) {
                            ByteBuffer b = ByteBuffer.allocate(4);
                            b.putInt(listening_port);
                            System.arraycopy(b.array(), 0, bytes, indice2, 4);
                            indice2 += 4;
                        }
                    }

                    /**
                     * On ajoute en tête du message le type de message 0 = DATA,
                     * 1 = ACK, 2 = HELLO1, 3 = HELLO2, 4 = EXISTING_PEERS
                     */
                    finalBytes = new byte[bytes.length + 1];
                    finalBytes[0] = 5;
                    System.arraycopy(bytes, 0, finalBytes, 1, bytes.length);

                    finalBytes2 = headerMessage(finalBytes, this.m_timestamp, this.m_engine.m_port_listening);

                    this.m_new_peer_channel.send(finalBytes2, 0, finalBytes2.length);

                    if (!this.m_engine.thread_launched) {
                        m_engine.broadcast_thread = new BroadcastThread(this, this.m_engine, this.m_engine.paquet_size);
                        m_engine.broadcast_thread.start();
                        this.m_engine.thread_launched = true;
                    } else {
                        m_engine.broadcast_thread.m_interrupted = false;
                    }

                }
                indice++;

            } else {
                /**
                 * Si on n'a pas pu le délivrer alors on arrête
                 */
                break;
            }
        }
    }

    /**
     * Permet d'appeller un Channel qui va lire du contenu
     *
     * @param isa
     * @return
     */
    boolean read(InetSocketAddress isa
    ) {
        Channel channel = this.getM_channels().get(isa);
        if (channel == null) {
            return false;
        } else {
            channel.read();
            return true;
        }

    }

    /**
     * Permet de construire le message avec le timestamp et l'id afin qu'il soit
     * envoyé
     */
    void send() {
        if (!this.m_message_to_send.isEmpty()) {
            byte[] msg_to_send = this.m_message_to_send.removeFirst();

            byte finalBytes[] = headerMessage(msg_to_send, this.m_timestamp, this.m_engine.m_port_listening);

            Message msg = new Message(null, finalBytes);
//            System.out.print("SENT : \t\t");
//            System.out.print("\t" + msg);
//            System.out.println();
//            System.out.flush();

            /**
             * On a créé un message [timestamp | id | type | data]
             */
            List<Channel> channels = new ArrayList<>(this.getM_channels().values());
            for (Channel channel : channels) {
                channel.send(finalBytes, 0, finalBytes.length);
            }

            /**
             * Puis on envoie un ACK si nécessaire
             */
            byte type_message_sent = msg_to_send[0];
            if (type_message_sent == 0 || type_message_sent == 3) {
                byte bytes2[] = new byte[9];
                bytes2[0] = 1;
                System.arraycopy(finalBytes, 0, bytes2, 1, 4); // On copie le timestamp
                System.arraycopy(finalBytes, 4, bytes2, 5, 4); // On copie le port à la fin

                this.addMessageToSend(bytes2);
                this.send();
            }

            this.m_timestamp++;
        }
    }

    /**
     * @return the m_channels
     */
    public HashMap<InetSocketAddress, Channel> getM_channels() {
        return m_channels;
    }

    /**
     * Ajoute un message à envoyer dans la liste
     *
     * @param bytes
     */
    public void addMessageToSend(byte[] bytes) {
        this.m_message_to_send.addLast(bytes);
    }

    /**
     * Permet de récupérer dans la liste le message dans le timestamp, l'IP et
     * le port correspondent à celui de msg
     *
     * @param msg
     * @return
     */
    public Message getMessage(Message msg) {
        Message result = null;
        synchronized (this.m_messages) {
            ArrayList<Message> listeMessages = new ArrayList<>(this.m_messages);
            for (Message message : listeMessages) {
                if (message.getM_id() == msg.m_remote_adress.getPort() && message.getM_timestamp() == msg.getM_timestamp()) {
                    result = message;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Supprime un Channel de la liste
     *
     * @param aThis
     */
    void imDead(NioChannel aThis) {
        m_channels.remove(aThis.getRemoteAddress());
    }

    public static byte[] headerMessage(byte[] bytes, int timestamp, int id) {
        /**
         * On rajoute 8 cases pour le timestamp et l'id
         */
        byte finalBytes2[] = new byte[bytes.length + 8];

        /**
         * Ajout du timestamp
         */
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(timestamp);
        byte[] result = b.array();
        System.arraycopy(result, 0, finalBytes2, 0, result.length);

        /**
         * Ajout de l'id
         */
        b = ByteBuffer.allocate(4);
        b.putInt(id);
        result = b.array();
        System.arraycopy(result, 0, finalBytes2, 4, result.length);
        System.arraycopy(bytes, 0, finalBytes2, 8, bytes.length);

        return finalBytes2;
    }

}
