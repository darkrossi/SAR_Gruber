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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author Mickaël
 */
public class Message implements Comparable<Message> {

    InetSocketAddress m_remote_adress;
    byte m_type; // Type 0 = data, Type 1 = ACK
    byte[] m_content; // content of the message
    int m_timestamp; // timestamp of the peer when it created this message
    int m_num_ack; // number of ack's received for this message
    int m_id; // id corresponding to the peer who created the message (it is peer's listening port)

    List<Integer> m_id_acks = new ArrayList<>();

    /**
     * Le contenu du paramètre bytes est de cette forme [timestamp | type |
     * data]
     *
     * @param m_remote_adress
     * @param m_type
     * @param bytes
     */
    public Message(InetSocketAddress m_remote_adress, byte[] bytes) {
        this.m_remote_adress = m_remote_adress;
        this.m_content = bytes;
        /**
         * bytes de la forme [timestamp (4) | id (4) | type (1) | data (?)] si
         * data et [timestamp (4) | id (4) | type (1) | timestamp_ack (4) | IP
         * (4) | port (4)] si ACK
         */
        this.m_type = bytes[8];
        int indice_timestamp = 0;
        if (this.m_type == 1) {
            indice_timestamp = 9;
        }
        byte[] id_tab = new byte[4];
        System.arraycopy(bytes, indice_timestamp, id_tab, 0, 4);
        this.m_timestamp = ByteBuffer.wrap(id_tab).getInt();

        id_tab = new byte[4];
        System.arraycopy(bytes, 4, id_tab, 0, 4);
        m_id = ByteBuffer.wrap(id_tab).getInt();

        this.m_num_ack = 0;
    }

    public Message() {
    }

    public byte getType() {
        return this.m_type;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof Message)) {
            return false;
        }
        Message message = (Message) other;

        if (this.getM_timestamp() != message.getM_timestamp()) {
            return false;
        }

        byte[] m_address = this.m_remote_adress.getAddress().getAddress();
        byte[] address = message.m_remote_adress.getAddress().getAddress();
        for (int i = 0; i < m_address.length; i++) {
            byte m_addres = m_address[i];
            byte addres = address[i];
            if (m_addres != addres) {
                return false;
            }
        }
        return this.m_remote_adress.getPort() == message.m_remote_adress.getPort();

    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + Objects.hashCode(this.m_remote_adress);
        hash = 97 * hash + this.getM_timestamp();
        return hash;
    }

    /**
     * @return the m_timestamp
     */
    public int getM_timestamp() {
        return m_timestamp;
    }

    public int increaseNumAck(int remote_port) {
        this.m_num_ack++; // when a peer receives an ack for this message, it increments this counter
        this.getM_id_acks().add(remote_port);
        return this.getM_num_ack();
    }

    public boolean isReadyToDeliver(int nb_peers, int port_listening) {
//        System.out.println(this.m_num_ack + " ==? " + nb_peers);
        if (this.getM_id() == port_listening) {
            return this.getM_num_ack() >= 2 * nb_peers;
        } else {

            return this.getM_num_ack() >= nb_peers + 1;
        }

    }

    /**
     * @return the m_content
     */
    public byte[] getM_content() {
        return m_content;
    }

    @Override
    public int compareTo(Message o) {
        if (this.m_type != 1 && o.m_type != 1) {
            if (this.m_type == 3) {
                if (o.m_type != 3) {
                    return -1;
                } else {
                    // On passe à la suite
                }
            } else if (o.m_type == 3) {
                return 1;
            }
        }

        if (this.getM_timestamp() < o.getM_timestamp()) {
            return -1;
        } else if (this.getM_timestamp() > o.getM_timestamp()) {
            return 1;
        } else {
            byte[] m_address = this.m_remote_adress.getAddress().getAddress();
            byte[] address = o.m_remote_adress.getAddress().getAddress();
            for (int i = 0; i < m_address.length; i++) {
                byte m_addres = m_address[i];
                byte addres = address[i];
                if (m_addres != addres) {
                    return -1;
                }
            }
            if (this.m_remote_adress.getPort() > o.m_remote_adress.getPort()) {
                return -1;
            } else if (this.m_remote_adress.getPort() < o.m_remote_adress.getPort()) {
                return 1;
            } else {
                return 0;
            }

        }
    }

    @Override
    public String toString() {
        String rst = this.getM_timestamp() + " - " + this.getM_id() + " - " + this.m_type + " -- ";
        String data = this.getData();
        return rst + data;
    }

    public String getData() {
        String data = "";
        byte[] id_tab = new byte[4];
        switch (this.m_type) {
            case 0:
                for (int i = 9; i < this.m_content.length; i++) {
                    byte b = this.m_content[i];
                    data += String.valueOf(b) + " ";
                }
                break;
            case 1:
                System.arraycopy(this.m_content, 9, id_tab, 0, 4);
                int timestamp = ByteBuffer.wrap(id_tab).getInt();

                System.arraycopy(this.m_content, 13, id_tab, 0, 4);
                int port = ByteBuffer.wrap(id_tab).getInt();

                data = timestamp + " - " + port;
                break;
            case 2:
            case 3:
                System.arraycopy(this.m_content, 9, id_tab, 0, 4);
                int port_listen = ByteBuffer.wrap(id_tab).getInt();
                data = String.valueOf(port_listen);
                break;
            case 5: // Les peers existants
                int nb_id_peers = (this.m_content.length - 9) / 4;
                for (int i = 0; i < nb_id_peers; i++) {
                    System.arraycopy(this.m_content, 9 + i * 4, id_tab, 0, 4);
                    data += ByteBuffer.wrap(id_tab).getInt() + " - ";
                }
                break;
            case 4: // Les messages non délivrés existants
                if (this.m_content.length > 9) {
                    byte[] only_data = new byte[this.m_content.length - 9];
                    System.arraycopy(this.m_content, 9, only_data, 0, only_data.length);
                    int indice = 0;
                    while (only_data.length > indice) {
                        byte length = only_data[indice];
                        byte[] current_data = new byte[length];
                        System.arraycopy(only_data, indice + 1, current_data, 0, length);
                        for (int i = 0; i < current_data.length; i++) {
                            byte b = current_data[i];
                            data += String.valueOf(b) + " ";
                        }
                        data += " - ";
                        indice += length + 1;
                    }
                }
                break;

        }
        return data;
    }

    public List<Message> getExistingMessages() throws UnknownHostException {
        List<Message> rst = new ArrayList<>();
        if (this.m_type == 4 && this.m_content.length > 9) {
            byte[] only_data = new byte[this.m_content.length - 9];
            System.arraycopy(this.m_content, 9, only_data, 0, only_data.length);
            int indice = 0;
            while (only_data.length > indice) {
                byte length = only_data[indice];
                byte[] current_data = new byte[length - 1];
                System.arraycopy(only_data, indice + 2, current_data, 0, length - 1);
                InetSocketAddress isa2 = new InetSocketAddress(InetAddress.getByName("localhost"), this.getM_id());
                Message msg = new Message(isa2, current_data);
                msg.m_num_ack = only_data[indice + 1];
                msg.increaseNumAck(0);
                rst.add(msg);
                indice += length + 1;
            }
            return rst;
        } else {
            return null;
        }
    }

    /**
     * @return the m_id
     */
    public int getM_id() {
        return m_id;
    }

    /**
     * @return the m_id_acks
     */
    public List<Integer> getM_id_acks() {
        return m_id_acks;
    }

    /**
     * @param m_timestamp the m_timestamp to set
     */
    public void setM_timestamp(int m_timestamp) {
        this.m_timestamp = m_timestamp;
    }

    /**
     * @return the m_num_ack
     */
    public int getM_num_ack() {
        return m_num_ack;
    }

    /**
     * @param m_type the m_type to set
     */
    public void setM_type(byte m_type) {
        this.m_type = m_type;
    }

}
