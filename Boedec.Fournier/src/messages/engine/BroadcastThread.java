/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 *
 * @author Mickaël
 */
public class BroadcastThread extends Thread {

    private Peer m_peer;
    private NioEngine m_engine;
    boolean m_running = true;
    static int m_port_listening;

    public BroadcastThread(Peer m_peer, NioEngine m_engine) {
        this.m_peer = m_peer;
        this.m_engine = m_engine;
        m_port_listening = m_engine.m_port_listening;
    }

    @Override
    public void run() {
        super.run();

        while (m_running) {

            /*
        	 * On crée un tableau de data propre au Peer, qui sera broadcasté au groupe.
             */
            int length = 3;
            byte bytes[] = new byte[length];
            for (int i = 0; i < length; i++) {
                bytes[i] = (byte) (i + 8 * (m_port_listening - 2004));
            }

            /**
             * On ajoute en tête du message le type de message 0 = DATA, 1 = ACK
             */
            byte finalBytes[] = new byte[bytes.length + 1];
            finalBytes[0] = 0;
            System.arraycopy(bytes, 0, finalBytes, 1, bytes.length);

            m_peer.addMessageToSend(finalBytes); // On ajoute ce message à la file d'attente d'émission de messages du Peer

            List<Channel> channels = new ArrayList<Channel>(m_peer.getM_channels().values());
            synchronized (channels) {
                for (Channel channel : channels) {
                    channel.sending();
                }
            }

            m_engine.getM_selector().wakeup();

            int random_delay = randInt(1, 6000);
            try {
                this.sleep(random_delay); // 2 secondes d'attente. Facilite les tests, mais devra être retiré lors du test en burst mode.
            } catch (InterruptedException ex) {
                interrupt();
            }
        }
    }

    public static int randInt(int min, int max) {
        Random rand = new Random(m_port_listening);
        int randomNum = rand.nextInt((max - min) + 1) + min;
        return randomNum;
    }
}
