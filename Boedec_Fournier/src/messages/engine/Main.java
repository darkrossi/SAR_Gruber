/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.net.InetAddress;
import java.util.Scanner;

public class Main {

    static final int num_min_port = 2005; // Représente le premier port à renseigner pour les tests

    public static void main(String args[]) throws Exception {
        /**
         * On demande le port de connexion à l'utilisateur du programme
         */
        Scanner sc = new Scanner(System.in);
        int m_port_listening = sc.nextInt();
        sc.close();

        NioEngine engine = new NioEngine();
        Peer peer = new Peer(engine);

        /**
         * On écoute des connexions
         */
        engine.listen(m_port_listening, peer);

        /**
         * On se connecte avec les autres peers présents
         */
        InetAddress m_localhost = InetAddress.getByName("localhost");
        for (int i = 0; i < m_port_listening - num_min_port; i++) {
            engine.connect(m_localhost, 2005 + i, peer);
        }

        engine.mainloop();

    }
}
