/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Mickaël
 */
public class BroadcastThread extends Thread {

    private Peer m_peer;
    private NioEngine m_engine;

    public BroadcastThread(Peer m_peer, NioEngine m_engine) {
        this.m_peer = m_peer;
        this.m_engine = m_engine;
    }

    @Override
    public void run() {
        super.run(); //To change body of generated methods, choose Tools | Templates.

        for (;;) {

            int length = 3;
            byte bytes[] = new byte[length];
            for (int i = 0; i < length; i++) {
                bytes[i] = (byte) (i + 8 * (m_engine.m_port_listening - 2004));
            }

            /**
             * On ajoute en tête du message le type de message
             *  0 = DATA, 1 = ACK
             */
            byte finalBytes[] = new byte[bytes.length + 1];
            finalBytes[0] = 0;
            System.arraycopy(bytes, 0, finalBytes, 1, bytes.length);

            m_peer.addMessageToSend(finalBytes);

            List<Channel> channels = new ArrayList<Channel>(m_peer.getM_channels().values());
            synchronized (channels) {
                for (Channel channel : channels) {
                    channel.sending();
                }
            }

            m_engine.getM_selector().wakeup();

            try {
                sleep(2000); // 3 secondes
            } catch (InterruptedException ex) {
                interrupt();
            }
        }
    }
}
