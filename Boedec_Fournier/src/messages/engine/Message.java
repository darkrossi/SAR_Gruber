/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.net.InetSocketAddress;

/**
 *
 * @author Mickaël
 */
public class Message {

    private InetSocketAddress m_remote_adress;
    private int m_type;
    private byte[] m_content;
    private byte m_timestamp;
    private int m_num_ack;

    /**
     * Le contenu du paramètre bytes est de cette forme [timestamp | type |
     * data]
     *
     * @param m_remote_adress
     * @param m_type
     * @param bytes
     */
    public Message(InetSocketAddress m_remote_adress, int m_type, byte[] bytes) {
        this.m_remote_adress = m_remote_adress;
        this.m_type = m_type;
        this.m_timestamp = bytes[0];
        this.m_content = new byte[bytes.length - 1];
        System.arraycopy(bytes, 1, this.m_content, 0, this.m_content.length);
        this.m_num_ack = 0;
    }

    public Message() {
    }

    public byte getType() {
        return this.getM_content()[0];
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
        return this.m_remote_adress == message.m_remote_adress && this.getM_timestamp() == message.getM_timestamp();

    }

    /**
     * @return the m_timestamp
     */
    public byte getM_timestamp() {
        return m_timestamp;
    }

    public int increaseNumAck() {
        this.m_num_ack++;
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

}
