/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.Scanner;

public class Main {

    public static void main(String args[]) throws Exception {
        NioEngine engine = new NioEngine();

//        int m_port_listening = 2005;

        Scanner sc = new Scanner(System.in);
        int m_port_listening = sc.nextInt();
        
        Peer peer = new Peer(engine);

        Server m_s = engine.listen(m_port_listening, peer);

        InetAddress m_localhost = InetAddress.getByName("localhost");
        if (m_port_listening == 2006) engine.connect(m_localhost, 2005, peer);
        engine.mainloop();
    }
}
