/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    static final int MIN_PORT = 2005; // Représente le premier port à renseigner pour les tests

    public static void main(String args[]) throws Exception {
        /**
         * On demande le port de connexion à l'utilisateur du programme
         */
        Scanner sc = new Scanner(System.in);
        System.out.println("Port d'écoute local : ");
        int m_port_listening = sc.nextInt();

        // Le tout premier peer entrera un port distant = à son port local.
        System.out.println("Port d'écoute de l'hôte distant : ");
        int m_remote_port = sc.nextInt();

        System.out.println("Taille des paquets : ");
        int m_paquet_size = sc.nextInt();

        FileThread file_thread = new FileThread(m_port_listening);
        file_thread.start();

        NioEngine engine = new NioEngine(m_paquet_size);
        Peer peer = new Peer(engine, file_thread);

        MainThread main_thread = new MainThread(engine, m_port_listening, m_remote_port, peer);
        main_thread.start();

        /**
         * Il faut appuyer sur la touche entrée pour que le programme s'arrête>
         */
        while (true) {
            sc.nextLine();
            if (sc.hasNextLine()) {
                break;
            }
        }
        sc.close();

        file_thread.destroy();
        engine.m_running = false;
        engine.pw.close();
        
        file_thread.end(engine.m_has_accept, m_port_listening);

    }

    static void compareDeliveredMessagesFiles(int listening_port) throws IOException {
        /**
         * On va chercher tous les fichiers créés
         */
        HashMap<Integer, BufferedReader> files = new HashMap<>();
        HashMap<Integer, Boolean> has_started = new HashMap<>();
        for (int i = 2005; i < listening_port + 1; i++) {
            File file_temp = new File(String.valueOf(i) + ".txt");
            BufferedReader br = new BufferedReader(new FileReader(file_temp));
            files.put(i, br);
            has_started.put(i, false);
        }

        String message = "Le total ordonnancement a été respecté ! WELL DONE !";
        boolean has_finished = false;
        while (!has_finished) {
            String line = null, line_temp;
            int timestamp = 0, timestamp_temp;
            boolean has_just_entered = true;
            for (Map.Entry<Integer, BufferedReader> entry : files.entrySet()) {
                Integer port = entry.getKey();
                BufferedReader buffered_reader = entry.getValue();
                if ((line_temp = buffered_reader.readLine()) != null) {
//                    System.out.println("fichier 22" + port + " : " + line_temp);
                    // process the line.
                    if (has_just_entered) {
                        line = line_temp;
                        timestamp = Integer.parseInt(line_temp.split(" - ")[0]);
                        has_just_entered = false;
                    }

                    timestamp_temp = Integer.parseInt(line_temp.split(" - ")[0]);

                    if (!line.equals(line_temp) && timestamp_temp < timestamp) {
                        message = "Le total ordonancement n'a pas été respecté ! TOO BAD !";
                        has_finished = true;
                        break;
                    }
                } else {
                    has_finished = true;
                    message = "Le total ordonnancement a été respecté ! WELL DONE !";
                    break;
                }
            }
        }
        System.out.println(message);
    }

    static class MainThread extends Thread {

        NioEngine engine;
        int port_listening, remote_port;
        Peer peer;

        public MainThread(NioEngine engine, int port_listening, int remote_port, Peer peer) {
            this.engine = engine;
            this.port_listening = port_listening;
            this.peer = peer;
            this.remote_port = remote_port;
        }

        @Override
        public void run() {
            try {
                super.run(); //To change body of generated methods, choose Tools | Templates.

                /**
                 * On écoute des connexions
                 */
                engine.listen(port_listening, peer);

                /**
                 * On se connecte avec les autres peers présents
                 */
                InetAddress m_localhost = InetAddress.getByName("localhost");
                if (port_listening != remote_port) {
                    engine.connect(m_localhost, remote_port, peer);
                }

                engine.mainloop();
            } catch (UnknownHostException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SecurityException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

}
