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
public class NioServer extends Server {

    private int m_port;
    
    /**
     * Constructor
     * @param port 
     */
    public NioServer(int port) {
        this.m_port = port;
    }

    @Override
    public int getPort() {
        return this.m_port;
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
