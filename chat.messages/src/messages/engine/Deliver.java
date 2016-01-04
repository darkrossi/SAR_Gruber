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
public class Deliver implements DeliverCallback {

    @Override
    public void deliver(Channel channel, byte[] bytes) {
        System.out.println("J'ai deliver");
    }
    
}
