/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Mickaël
 */
public class BroadcastThread extends Thread {

    private List<SelectionKey> m_sk_l;

    public BroadcastThread(List<SelectionKey> m_sk_l) {
        this.m_sk_l = m_sk_l;
    }

    @Override
    public void run() {
        super.run(); //To change body of generated methods, choose Tools | Templates.

        for (;;) {
            for (SelectionKey key : m_sk_l) {
                if (!key.isValid()) {
                    continue;
                } else if (key.isAcceptable() || key.isConnectable()) {
                    // Nothing
                } else if (key.isReadable() && key.isWritable()) {
                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    System.out.println("On passe dans le else if voulu");
                }
            }
            try {
                sleep(3000); // 3 secondes
            } catch (InterruptedException ex) {
                interrupt();
            }
        }

    }
}
