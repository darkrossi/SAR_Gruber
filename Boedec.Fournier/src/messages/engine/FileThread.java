/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;

/**
 *
 * @author Mickaël
 */
public class FileThread extends Thread {

    LinkedList<byte[]> m_delivered_messages = new LinkedList<>();
    int m_listening_port;

    File f;
    PrintWriter pw;

    public FileThread(int m_listening_port) {
        this.m_listening_port = m_listening_port;
    }

    public void addDeliveredMessage(byte[] deliveredMessage) {
        synchronized (this.m_delivered_messages) {
            this.m_delivered_messages.addLast(deliveredMessage);
        }
    }

    @Override
    public void run() {
        super.run(); //To change body of generated methods, choose Tools | Templates.

        this.f = new File(String.valueOf(this.m_listening_port) + ".txt");
        try {
            this.pw = new PrintWriter(new BufferedWriter(new FileWriter(f)));
        } catch (IOException exception) {
            System.out.println("Erreur lors de la lecture : " + exception.getMessage());
        }

        while (true) {
            synchronized (this.m_delivered_messages) {
                if (!this.m_delivered_messages.isEmpty()) {
//                    System.out.print("On tente d'écrire dans le fichier : ");
                    byte[] data = m_delivered_messages.removeFirst();
                    Message message = new Message(null, data);

                    this.pw.print(message);

                    this.pw.println();
                }
            }
        }
    }

    @Override
    public void destroy() {
//        super.destroy();
        this.pw.close();
    }

}
