/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages.engine;

/**
 *
 * @author Mickaël
 */
public class Acceptor implements AcceptCallback {

    @Override
    public void accepted(Server server, Channel channel) {
        System.out.println("I accepted");
//        add(server, channel); // On ajoute le nouvel arrivant
    }

    @Override
    public void closed(Channel channel) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
