/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 *
 * @author Mickaël
 */
public class Message implements Comparable<Message> {

    private InetSocketAddress m_remote_adress;
    private byte m_type; // Type 0 = data, Type 1 = ACK
    private byte[] m_content; // content of the message
    private int m_timestamp; // timestamp of the peer when it created this message
    private int m_num_ack; // number of ack's received for this message
    private int m_id; // id corresponding to the peer who created the message (it is peer's listening port)

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
        hash = 97 * hash + this.m_timestamp;
        return hash;
    }

    /**
     * @return the m_timestamp
     */
    public int getM_timestamp() {
        return m_timestamp;
    }

    public int increaseNumAck() {
        this.m_num_ack++; // when a peer receives an ack for this message, it increments this counter
        return this.m_num_ack;
    }

    public boolean isReadyToDeliver(int nb_peers) {
        return this.m_num_ack == nb_peers;
    }

    /**
     * @return the m_content
     */
    public byte[] getM_content() {
        return m_content;
    }

    @Override
    public int compareTo(Message o) {
        if (this.m_timestamp < o.m_timestamp) {
            return -1;
        } else if (this.m_timestamp > o.m_timestamp) {
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
        String rst = this.m_timestamp + " - " + this.m_id + " - " + this.m_type + " - ";
        String data = "";
        if (this.m_type == 1) {
            byte[] id_tab = new byte[4];
            System.arraycopy(this.m_content, 9, id_tab, 0, 4);
            int timestamp = ByteBuffer.wrap(id_tab).getInt();
            
            String ip = this.m_content[13] + "." + this.m_content[14] + "." + this.m_content[15] + "." + this.m_content[16];
            
            System.arraycopy(this.m_content, 17, id_tab, 0, 4);
            int port = ByteBuffer.wrap(id_tab).getInt();
            
            data = timestamp + " - " + ip + " - " + port;
        } else {
            for (int i = 9; i < this.m_content.length; i++) {
                byte b = this.m_content[i];
                data += b + " ";
            }
        }
        return rst + data;
    }

}
