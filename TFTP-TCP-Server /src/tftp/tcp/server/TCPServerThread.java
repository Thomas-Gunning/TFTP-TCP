package tftp.tcp.server;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * 135124
 */
class TCPServerThread extends Thread {

    protected Socket socket = null;
    private BufferedReader in;
    private boolean endFile = true;
    private static final byte OP_RRQ = 1;
    private static final byte OP_WRQ = 2;
    private static final byte OP_DATAPACKET = 3;
    private static final byte OP_ACK = 4;
    private static final byte OP_ERROR = 5;
    private static final int DATALENGTH = 512;
    private int defaultPort;
    private int socketNo;
    private InetAddress address;
    private DatagramPacket firstPacket;
    private String fileName;
    private InputStream inFirst;

   

    /**
     * this is the server thread that gets created when the server class creates
     * a new thread
     *
     * @param socket the incoming server
     * @param in the packet which will contain a received data
     * @throws SocketException
     * @throws FileNotFoundException
     */
    public TCPServerThread(Socket socket, InputStream in) throws SocketException, FileNotFoundException {
        this("TFTPUDPServer");
        this.socket = socket;
        inFirst = in;
    }

    /**
     * the super class of the thread
     *
     * @param name
     * @throws SocketException
     * @throws FileNotFoundException
     */
    public TCPServerThread(String name) throws SocketException, FileNotFoundException {
        super(name);

    }

    @Override
    public void run() {
        int counter = 0;

        try {

            System.out.println("recieved Packet");
            socket.setSoTimeout(10000);
            byte[] req = new byte[516];
            BufferedInputStream buf = new BufferedInputStream(inFirst);
            buf.read(req);
            firstReq(req);
        } catch (IOException e) {
            System.err.println(e);
            endFile = false;
        }
        try {
            socket.close();
        } catch (IOException ex) {
            Logger.getLogger(TCPServerThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * this receives the first request from the client and decides by its opcode
     * wherever its a read or write request.
     *
     * @param req the packet received
     * @throws IOException
     */

    public void firstReq(byte[] req) throws IOException {
        byte[] opcode = new byte[2];
        byte[] inDataStream = req;

        for (int i = 0; i < 2; i++) {
            //this gets the opcode 
            opcode[i] = inDataStream[i];
        }

        //if its a read request then read the file name from the packet 
        if (opcode[0] == 0 && opcode[1] == OP_RRQ) {
            readFileName(req);
            //if its a write request then send an ack 
        } else if (opcode[0] == 0 && opcode[1] == OP_WRQ) {
            readFileName(req);
            byte[] ack = sendFirstAck();
            OutputStream out = socket.getOutputStream();
            DataOutputStream dos = new DataOutputStream(out);
            dos.write(ack);
            receiveFile();
        }
    }

    /**
     *
     * this reads the file name and then retrieves the data from the file and
     * then calls sendFile
     *
     * @param packet the packet to that has been received
     * @throws FileNotFoundException
     * @throws IOException
     */
    public void readFileName(byte[] packet) throws FileNotFoundException, IOException {
        byte[] inDataStream = packet;
        int i = 2;
        int j = 0;
        while (inDataStream[i] != 0) {
            i++;
        }
        ByteBuffer fileNameBytes = ByteBuffer.allocate(i - 2);
        i = 2;
        while (inDataStream[i] != 0) {
            fileNameBytes.put(inDataStream[i]);
            i++;
        }
        fileName = new String(fileNameBytes.array());
        File file = new File(fileName);
        if (inDataStream[1] != OP_WRQ) {
            if (!file.exists()) {
                System.out.println("ERROR CODE 1 - FILE NOT FOUND");
                createError(1, "File not found");
            } else {
                byte[] fileByte = new byte[(int) file.length()];
                try {
                    FileInputStream fileInputStream = new FileInputStream(file);
                    fileInputStream.read(fileByte);
                    sendFile(fileByte);
                } catch (FileNotFoundException e) {
                    System.out.println("File Not Found.");
                    e.printStackTrace();
                } catch (IOException e1) {
                    System.out.println("Error Reading The File.");
                    e1.printStackTrace();
                }
            }
        }
    }

    /**
     *
     * this sends a file to the server when the write request has been called it
     * creates the file and splits it into packets and then sends the packets to
     * the server in a do while loop, incrementing the block numbers
     *
     * @param fileByte the file but in bytes
     * @throws IOException
     */
    public void sendFile(byte[] fileByte) throws IOException {
        int offset = 0;
        boolean ackReceived = true;
        ByteBuffer theFileBuffer = ByteBuffer.wrap(fileByte);
        int byteLength = theFileBuffer.remaining();
        int amountOfPackets = byteLength / DATALENGTH;
        int j = 0;
        int k = -1;
        int dataOffset = 0;
        int firstBlockNumber = 0;
        int secondBlockNumber = 0;
        byte[] byteAck = new byte[4];
        do {
            byte[] dst;
            if (fileByte.length - (dataOffset) >= 512) {

                dst = new byte[DATALENGTH];
            } else {
                dst = new byte[fileByte.length - (dataOffset)];
            }
            for (int i = dataOffset; i < 512 + dataOffset && i < fileByte.length; i++) {
                dst[j] = fileByte[i];
                j++;
            }
            j = 0;
            dataOffset += 512;
            secondBlockNumber++;
            if (secondBlockNumber == 128) {
                firstBlockNumber++;
                secondBlockNumber = 0;
            }
            byte[] dataPacket = createPacket(dst, firstBlockNumber, secondBlockNumber);
            OutputStream out = socket.getOutputStream();
            DataOutputStream dos = new DataOutputStream(out);
            dos.write(dataPacket);
            k++;
        } while (k < amountOfPackets);
    }

    /**
     * This creates a packet to be send, we make sure we sent the data, the
     * address and the port, we need to put the data opcode first, the blockNo
     * and then the data
     *
     * @param theFile the file to be sent in a byte array
     * @param firstBlockNumber the first block number
     * @param secondBlockNumber the second block number
     * @return the datagram packet created
     */
    public byte[] createPacket(byte[] theFile, int firstBlockNumber, int secondBlockNumber) {
        //Create the data packet to be sent
        //make sure we sent the data, the addess and the port
        //We need to put the data opcode first, Block# and then the data
        int position = 0;
        int offset = 0;

        byte[] dataPacket = new byte[theFile.length + 4];
        dataPacket[position] = (byte) 0;
        position++;
        dataPacket[position] = OP_DATAPACKET;
        position++;
        dataPacket[position] = (byte) firstBlockNumber;
        position++;
        dataPacket[position] = (byte) secondBlockNumber;
        position++;

        for (int i = 0; i < theFile.length; i++) {
            dataPacket[position] = theFile[i];
            position++;
        }
        return dataPacket;
    }

    /**
     * This sends the first ack back to client when a write request has been
     * sent to the server
     *
     * @return ack byte[]
     */
    public byte[] sendFirstAck() {
        byte[] ack = new byte[4];
        int position = 0;
        ack[position] = 0;
        position++;
        ack[position] = OP_ACK;
        position++;
        ack[position] = 0;
        position++;
        ack[position] = 0;
        return ack;

    }

    /**
     * This will receive the file from the server and is called when the receive
     * request is sent
     *
     * @throws UnknownHostException
     * @throws SocketException
     * @throws IOException
     */
    public void receiveFile() throws UnknownHostException, SocketException, IOException {
        InetAddress address = InetAddress.getByName("localhost");
        boolean endOfFile = true;
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        //this waits till the end of file, we know when its the end of the file 
        //as the packet.length < 516 bytes 
        //this also send all of the acks
        while (endOfFile) {
            InputStream in = socket.getInputStream();
            DataInputStream buf = new DataInputStream(in);
            byte[] readByteArray = new byte[516];
            int sizeOfBuf = buf.read(readByteArray);
            int size = sizeOfBuf;
            System.out.println(sizeOfBuf);
            byte[] packetInput;
     
                if (sizeOfBuf < 516) {
                    packetInput = new byte[sizeOfBuf];
                    for (int i = 0; i < sizeOfBuf; i++) {
                        packetInput[i] = readByteArray[i];
                    }
                } else {
                     packetInput = new byte[sizeOfBuf];
                    packetInput = readByteArray;
                }
                if (packetInput[1] == OP_ERROR) {
                    error(packetInput);
                } else {
                    if (packetInput[1] == OP_DATAPACKET && packetInput.length == 516) {

                        file.write(packetInput, 4, packetInput.length - 4);
                    } else if (packetInput[1] == OP_DATAPACKET && packetInput.length < 516) {
                        int j = 0;
                        for (int i = 4; i < packetInput.length; i++) {
                            if (packetInput[i] == (byte) 0) {
                                j++;
                            }
                        }
                        file.write(packetInput, 4, (packetInput.length - 4) - j);
                        endOfFile = false;
                        writeFile(file);
                }
            }
        }
 
    }

    /**
     * This writes the file using the filename given and the data that has been
     * sent from the server
     *
     * @param file this is the file from the server
     * @throws FileNotFoundException
     * @throws IOException
     */
    public void writeFile(ByteArrayOutputStream file) throws FileNotFoundException, IOException {

        try (OutputStream outputStream = new FileOutputStream(fileName)) {
            file.writeTo(outputStream);
            System.out.println(fileName);
            System.out.println("wrote the file");
        }
    }

    /**
     * this creates an error message
     *
     * @param byteArray the error array
     */
    public void error(byte[] byteArray) {
        String errorCode = new String(byteArray, 3, 1);
        String errorText = new String(byteArray, 4, byteArray.length - 4);
        System.err.println("Error: " + errorCode + " " + errorText);
    }

    /**
     * this creates an error message to be sent
     *
     * @param errorCode the error code
     * @param errMessage the message which will be sent
     * @throws IOException
     */
    public void createError(int errorCode, String errMessage) throws IOException {
        byte[] error = new byte[512];
        int position = 0;
        error[position] = 0;
        position++;
        error[position] = OP_ERROR;
        position++;
        error[position] = 0;
        position++;
        error[position] = (byte) errorCode;
        position++;
        for (int i = 0; i < errMessage.length(); i++) {
            error[position] = (byte) errMessage.charAt(i);
            position++;
        }
        error[position] = 0;
        OutputStream out = socket.getOutputStream();
        DataOutputStream dos = new DataOutputStream(out);
        dos.write(error);
    }
}
