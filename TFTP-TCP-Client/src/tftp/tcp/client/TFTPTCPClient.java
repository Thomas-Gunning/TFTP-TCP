package tftp.tcp.client;

import java.io.BufferedInputStream;
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
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 *
 * 135124
 */
public class TFTPTCPClient {

    private int TFTP_DEFAULT_PORT = 9000;
    private static final byte OP_RRQ = 1;
    private static final byte OP_WRQ = 2;
    private static final byte OP_DATAPACKET = 3;
    private static final byte OP_ACK = 4;
    private static final byte OP_ERROR = 5;
    private static String fileName;
    private final static int PACKET_SIZE = 516;
    private Socket socket;
    private static final int DATALENGTH = 512;
    Random rand = new Random();

    /**
     * The main method checks the amount of arguments and creates a new client
     *
     * @param args the command line arguments
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 2) {
            System.out.println("the request and file name needed");
            return;
        }

        TFTPTCPClient client = new TFTPTCPClient();
        if ("read".equals(args[0])) {
            TFTPTCPClient.fileName = args[1];
            client.sendReadRequest();

        } else if ("write".equals(args[0])) {
            TFTPTCPClient.fileName = args[1];
            client.sendWriteRequest();
        } else {
            System.out.println("wrong input");
        }

    }

    /**
     * This sends a new write request to the server from the client It gets the
     * address and ports numbers to create a new DatagramSocket object
     *
     * @throws SocketException
     * @throws IOException
     */
    public void sendWriteRequest() throws SocketException, IOException {
        InetAddress address = InetAddress.getByName("localhost");
        socket = new Socket(address, 9000);

        byte[] readByteArray = new byte[516];
        byte[] ackArray = new byte[4];
        byte[] requestByteArray = createRequest(OP_WRQ, fileName, "octet");

        OutputStream out = socket.getOutputStream();
        DataOutputStream dos = new DataOutputStream(out);
        dos.write(requestByteArray);

        InputStream in = socket.getInputStream();
        DataInputStream dis = new DataInputStream(in);
        dis.readFully(ackArray);

        receivedFirstAck(ackArray);
        socket.close();
    }

    /**
     *
     * This sends a new read request to the server from the client It gets the
     * address and port numbers to create a new Datagram Socket object and then
     * calls receiveFile()
     *
     * @throws UnknownHostException
     * @throws SocketException
     * @throws IOException
     */
    public void sendReadRequest() throws UnknownHostException, SocketException, IOException {
        InetAddress address = InetAddress.getByName("localhost");
        socket = new Socket(address, 9000);
        socket.setSoTimeout(10000);
        byte[] readByteArray = new byte[516];
        byte[] requestByteArray = createRequest(OP_RRQ, fileName, "octet");

        OutputStream out = socket.getOutputStream();
        DataOutputStream dos = new DataOutputStream(out);
        dos.write(requestByteArray);
        receiveFile();
        socket.close();
    }

    /**
     * This creates the request into bytes to be sent off, it uses the opcode
     * the filename with a string and the mode.
     *
     * @param opCode the opCode of the request
     * @param filename the filename of the file
     * @param mode the mode of the packet - octet
     * @return
     */
    public byte[] createRequest(byte opCode, String filename, String mode) {
        byte zeroByte = 0;
        int rrqByteLength = 2 + filename.length() + 1 + mode.length() + 1;
        byte[] rrqByteArray = new byte[rrqByteLength];

        int position = 0;
        rrqByteArray[position] = zeroByte;
        position++;
        rrqByteArray[position] = opCode;
        position++;
        for (int i = 0; i < filename.length(); i++) {
            rrqByteArray[position] = (byte) filename.charAt(i);
            position++;
        }
        rrqByteArray[position] = zeroByte;
        position++;
        for (int i = 0; i < mode.length(); i++) {
            rrqByteArray[position] = (byte) mode.charAt(i);
            position++;

        }
        rrqByteArray[position] = zeroByte;
        return rrqByteArray;
    }

    /**
     *
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
        while (endOfFile) {
            InputStream in = socket.getInputStream();
            DataInputStream buf = new DataInputStream(in);
            byte[] readByteArray = new byte[516];
            int sizeOfBuf = buf.read(readByteArray);
            int size = sizeOfBuf;
            byte[] packetInput = new byte[sizeOfBuf];
            if (sizeOfBuf < 516) {
                for (int i = 0; i < sizeOfBuf; i++) {
                    packetInput[i] = readByteArray[i];
                }
            } else {
                packetInput = readByteArray;
            }
            //this waits till the end of file, we know when its the end of the file 
            //as the packet.length < 516 bytes 
            //this also send all of the acks
            if (packetInput[1] == OP_ERROR) {
                error(packetInput);
                break;
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
     * this is called when the write request is called and the first ack from
     * the server
     *
     * @param ack data from the server
     * @return boolean
     * @throws IOException
     */
    public boolean receivedFirstAck(byte[] ack) throws IOException {
        byte[] firstAck = ack;
        if (firstAck[0] == 0 && (int) firstAck[1] == OP_ACK && firstAck[2] == 0 && firstAck[3] == 0) {
            readFileName();
            return true;
        } else {
            return false;
        }

    }

    /**
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
        int firstBlockNumber = -1;
        int secondBlockNumber = 0;
        byte[] ack = new byte[4];
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
            System.out.println(dataPacket.length);
            OutputStream out = socket.getOutputStream();
            DataOutputStream dos = new DataOutputStream(out);
            dos.write(dataPacket);
            k++;
        } while (k < amountOfPackets);
    }

    /**
     * This creates a packet to be send, we make sure we sent the data we need
     * to put the data opcode first, the blockNo and then the data
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
     * this reads the file name and then retrieves the data from the file and
     * then calls sendFile
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    public void readFileName() throws FileNotFoundException, IOException {

        File file = new File(fileName);
        byte[] fileByte = new byte[(int) file.length()];
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            fileInputStream.read(fileByte);
        } catch (FileNotFoundException e) {
            System.out.println("File Not Found.");
            e.printStackTrace();
        } catch (IOException e1) {
            System.out.println("Error Reading The File.");
            e1.printStackTrace();
        }
        sendFile(fileByte);
    }
}
