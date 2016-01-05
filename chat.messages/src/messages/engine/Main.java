/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.net.InetAddress;
import java.util.Scanner;

public class Main {

    static final int num_min_port = 2005;

    public static void main(String args[]) throws Exception {
        /**
         * On demande le port de connexion à l'utilisateur du programme
         */
        Scanner sc = new Scanner(System.in);
        int m_port_listening = sc.nextInt();

        NioEngine engine = new NioEngine();
        Acceptor acceptor = new Acceptor();
        Connector connector = new Connector();

        Server m_s = engine.listen(m_port_listening, acceptor);

        InetAddress m_localhost = InetAddress.getByName("localhost");

        for (int i = 0; i < m_port_listening - num_min_port; i++) {
            engine.connect(m_localhost, 2005 + i, connector);
        }

        engine.mainloop();
    }
}
