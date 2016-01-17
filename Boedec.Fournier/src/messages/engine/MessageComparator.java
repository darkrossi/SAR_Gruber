/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.util.Comparator;

/**
 *
 * @author Mickaël
 */
public class MessageComparator implements Comparator<Message> {

    @Override
    public int compare(Message o1, Message o2) {
        if (o1.m_type != 1 && o2.m_type != 1) {
            if (o1.m_type == 3) {
                if (o2.m_type != 3) {
                    return -1;
                } else {
                    // On passe à la suite
                }
            } else if (o2.m_type == 3) {
                return 1;
            }
        }

        if (o1.getM_timestamp() < o2.getM_timestamp()) {
            return -1;
        } else if (o1.getM_timestamp() > o2.getM_timestamp()) {
            return 1;
        } else {
            byte[] m_address = o1.m_remote_adress.getAddress().getAddress();
            byte[] address = o2.m_remote_adress.getAddress().getAddress();
            for (int i = 0; i < m_address.length; i++) {
                byte m_addres = m_address[i];
                byte addres = address[i];
                if (m_addres != addres) {
                    return -1;
                }
            }
            if (o1.m_remote_adress.getPort() > o2.m_remote_adress.getPort()) {
                return -1;
            } else if (o1.m_remote_adress.getPort() < o2.m_remote_adress.getPort()) {
                return 1;
            } else {
                return 0;
            }

        }
    }

}
