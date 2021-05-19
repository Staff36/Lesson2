
import lombok.extern.log4j.Log4j;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Log4j
public class Server {
    private static int counter = 0;
    private final ByteBuffer buffer;
    private final ServerSocketChannel serverChannel;
    private final Selector selector;
    private Map<String, FileHandler> fileHandlerMap;

    public Server(int port) throws IOException {
        buffer = ByteBuffer.allocate(1024);
        serverChannel = ServerSocketChannel.open();
        log.debug("Server started");
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        fileHandlerMap = new HashMap<>();
        while (serverChannel.isOpen()) {
            selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept();
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
                iterator.remove();
            }
        }
    }

    private void handleRead(SelectionKey selectionKey) {
        try {
            if (selectionKey.isValid()) {
                SocketChannel channel = (SocketChannel) selectionKey.channel();
                String attachment = selectionKey.attachment().toString();
                FileHandler fileHandler = fileHandlerMap.get(attachment);
                StringBuilder sb = new StringBuilder();
                int currentByte = 0;
                while (true) {
                    currentByte = channel.read(buffer);
                    if (currentByte == 0) {
                        break;
                    }
                    if (currentByte == -1) {
                        channel.close();
                    }
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        sb.append((char) buffer.get());
                    }
                    buffer.clear();
                }
                String message = sb.toString();
                log.debug("Incoming command from "+ attachment + ": " + message);
                messageHandler(message, channel, fileHandler);
            }
        } catch (Exception e) {
            fileHandlerMap.remove(selectionKey.attachment().toString());
            log.debug("Client " + selectionKey.attachment()+ " disconnected!");
        }

    }

    private void handleAccept() throws IOException {
        counter++;
        log.debug("Client User" + counter + "was accepted");
        SocketChannel channel = serverChannel.accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ, "User" + counter);
        FileHandler fileHandler = new FileHandler();
        fileHandlerMap.put("User" + counter, fileHandler);
        sendString(channel, "[" + fileHandler.getCurrentDirectory().getName() + "]->");
    }

    private void messageHandler(String msg, SocketChannel channel, FileHandler fileHandler) throws IOException {
        String command = msg.trim();
        if (command.startsWith("ls")){
            sendDirectoryList(channel, fileHandler.getCurrentDirectory());
        } else if (command.startsWith("cat")){
            sendFileInfo(command.substring(4), fileHandler.getCurrentDirectory().toPath(), channel);
        } else if (command.startsWith("help")){
            log.debug("Client called help file");
            sendFileInfo("help.txt", Paths.get("src","main", "resources"),channel);
        } else if(command.startsWith("cd")){
            log.debug("cd command");
            changeDirectory(channel, fileHandler, command);
        }else if(command.equals("..")){
            log.debug(".. Command");
            log.debug("Moved to directory: " +fileHandler.getCurrentDirectory());
            moveToParentDirectory(channel, fileHandler);
        }else if(command.equals("")){
        } else {
            sendString(channel, "Unknown message: " + msg + " \n \r");
        }
        sendString(channel, "[" + fileHandler.getCurrentDirectory().getName() + "]->");
    }

    private void moveToParentDirectory(SocketChannel channel, FileHandler fileHandler) throws IOException {
        if (fileHandler.getCurrentDirectory().equals(fileHandler.getRootDirectory())){
            sendString(channel,"You are in a root directory \n \r" );
            log.debug("Client tried get up to upper his root directory");
        } else{
            fileHandler.setCurrentDirectory(fileHandler.getCurrentDirectory().getParentFile());
            sendString(channel, "Moved to directory: " + fileHandler.getCurrentDirectory().getName() + " \n \r");
            log.debug("Client was moved to directory " + fileHandler.getCurrentDirectory());
        }
    }

    private void changeDirectory(SocketChannel channel, FileHandler fileHandler, String command) throws IOException {
        File selectedFile = Paths.get(fileHandler.getCurrentDirectory().toString(), command.substring(3)).toFile();
        if (selectedFile.isDirectory()){
            fileHandler.setCurrentDirectory(selectedFile);
            sendString(channel, "Moved to directory: " + fileHandler.getCurrentDirectory().getName() + " \n \r");
            log.debug("Moved to directory: " + fileHandler.getCurrentDirectory());
        } else {
            sendString(channel, "Unknown directory: " + command.substring(3) + " \n \r");
            log.debug("Unknown directory: " + command.substring(3));
        }
    }

    private void sendString(SocketChannel channel, String msg) throws IOException {
        channel.write(ByteBuffer.wrap((msg)
                .getBytes(StandardCharsets.UTF_8)));
    }

    private void sendDirectoryList(SocketChannel channel, File path) throws IOException {
        log.debug("Current directory is: " + path.getAbsolutePath());
        StringBuilder sb = new StringBuilder();
        Arrays.stream(Objects.requireNonNull(path.list())).forEach(x -> sb.append(x).append(" \n \r"));
        channel.write(ByteBuffer.wrap(sb.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private void sendFileInfo(String filename, Path path, SocketChannel channel) throws IOException {
        File pathToFile = Paths.get(path.toString(), filename).toFile();
        sendString(channel, " \n \r ================================ Reading file " + filename + " ================================ \n \r");
        RandomAccessFile file = new RandomAccessFile(pathToFile.getAbsolutePath(), "r");
        FileChannel fileChannel = file.getChannel();
        while (fileChannel.read(buffer) > 0){
            buffer.flip();
            channel.write(buffer);
            buffer.clear();
        }
        fileChannel.close();
        file.close();
        sendString(channel, " \n \r ================================ End Reading ================================ \n \r");
    }
}