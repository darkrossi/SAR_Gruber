/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import static java.lang.Thread.sleep;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Mickaël
 */
public class MonitorMessagesToSend extends Thread {

    private Peer m_peer;
    private NioEngine m_engine;

    public MonitorMessagesToSend(Peer m_peer, NioEngine m_engine) {
        this.m_peer = m_peer;
        this.m_engine = m_engine;
    }

    @Override
    public void run() {
        super.run(); //To change body of generated methods, choose Tools | Templates.

        for (;;) {
            synchronized (System.out) {
                m_peer.send();
            }
            m_engine.getM_selector().wakeup();
        }
    }
}
