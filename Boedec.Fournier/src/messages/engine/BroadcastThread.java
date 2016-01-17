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

    boolean m_interrupted = false;
    int m_paquet_size;

    public BroadcastThread(Peer m_peer, NioEngine m_engine, int paquet_size) {
        this.m_peer = m_peer;
        this.m_engine = m_engine;
        m_port_listening = m_engine.m_port_listening;
        this.m_paquet_size = paquet_size;
    }

    @Override
    public void run() {
        super.run();
        System.out.println("Broadcast en cours. Appuyez sur ENTREE pour arreter et lancer la verification.");

        while (m_running) {

            if (!m_interrupted) {
                /*
                 * On crée un tableau de data qui sera broadcasté au groupe.
                 */
                int length = this.m_paquet_size;
                byte bytes[] = new byte[length];
                for (int i = 0; i < length; i++) {
                    bytes[i] = (byte) (i % 256);
                }

                /**
                 * On ajoute en tête du message le type de message 0 = DATA
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

                int random_delay = randInt(50, 100);
                try {
                    this.sleep(random_delay);
                } catch (InterruptedException ex) {
                    interrupt();
                }
            }
        }
    }

    public static int randInt(int min, int max) {
        Random rand = new Random(m_port_listening);
        int randomNum = rand.nextInt((max - min) + 1) + min;
        return randomNum;
    }
}
