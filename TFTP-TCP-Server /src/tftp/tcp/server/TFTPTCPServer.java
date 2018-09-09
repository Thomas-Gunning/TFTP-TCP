
package tftp.tcp.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 *
 * 135124
 */
public class TFTPTCPServer implements Runnable{

   @Override
    public void run() {
        
    }

     /**
     * 
     * This is the main server and creates threads when a client is trying to
     * connect to server
     *
     * @param args
     * @throws SocketException
     * @throws IOException
     */
    public static void main(String args[]) throws SocketException, IOException{  
         System.out.println("started the main server");
         
         ServerSocket socket = new ServerSocket(9000);
         
         Socket slaveSocket;
         while(true){
             //Create a new Server thread using the socket number and packet
             slaveSocket = socket.accept();
             InputStream in = slaveSocket.getInputStream();
             System.out.println("socket accepted");
             new TCPServerThread(slaveSocket, in).start();
             System.out.println("MADE THREAD");
             
         }
         
     } 
}
