/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Comparator;
import java.util.Objects;

/**
 *
 * @author Mickaël
 */
public class Message implements Comparable<Message> {

    private InetSocketAddress m_remote_adress;
    private byte m_type;
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
    public Message(InetSocketAddress m_remote_adress, byte[] bytes) {
        this.m_remote_adress = m_remote_adress;
        this.m_content = bytes;
//        this.m_content = new byte[bytes.length - 1];
//        System.arraycopy(bytes, 1, this.m_content, 0, this.m_content.length);
        this.m_type = bytes[1];
        if (this.m_type == 0) {
            this.m_timestamp = bytes[0];
        } else {
            this.m_timestamp = bytes[2];
        }

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
        return this.m_remote_adress.equals(message.m_remote_adress) && this.getM_timestamp() == message.getM_timestamp();

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
//
//    public class MyInetSOcketAdress implements Comparable<MyInetSOcketAdress> {
//
//        private InetSocketAddress isa;
//
//        public MyInetSOcketAdress(InetSocketAddress isa) {
//            this.isa = isa;
//        }
//
//        @Override
//        public int compareTo(MyInetSOcketAdress o) {
//            InetAddress m_ia = this.isa.getAddress();
//            InetAddress ia = o.isa.getAddress();
//            if(m_ia.getAddress() > ia.getAddress()){
//                
//            }
//        }
//
//    }

}
