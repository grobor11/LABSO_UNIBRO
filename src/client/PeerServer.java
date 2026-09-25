package client;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/*
 * Il PeerServer gestisce la ricezione delle connessioni in entrata da parte di altri nodi sensore.
 * Implementa Runnable per essere eseguito su un thread autonomo in background, 
 * evitando di bloccare la sessione interattiva da riga di comando (CLI).
 */
public class PeerServer implements Runnable {

    // Socket di ascolto per le connessioni in ingresso
    private final ServerSocket serverSocket;
    
    // Porta effettiva su cui il server è rimasto in ascolto
    private final int port;
    
    // Monitor unico condiviso con tutti gli handler per forzare il servizio sequenziale dei download
    private final Object downloadLock = new Object();
    
    // Archivio locale condiviso con la CLI e con gli handler P2P
    private final LocalStorage localStorage;
    
    // Flag volatile per gestire l'arresto pulito del thread
    private volatile boolean running = true;

    /**
     * Costruttore: inizializza il ServerSocket e memorizza la porta assegnata.
     * Se requestedPort è 0, il sistema operativo assegna automaticamente una porta libera.
     */
    public PeerServer(int requestedPort, LocalStorage localStorage) throws IOException {
        this.localStorage = localStorage;
        this.serverSocket = new ServerSocket(requestedPort);
        this.port = this.serverSocket.getLocalPort();
    }

    /**
     * Restituisce la porta su cui il nodo è in ascolto, necessaria per la registrazione all'Aggregator.
     */
    public int getPort() {
        return this.port;
    }

    @Override
    public void run() {
        System.out.println("[PeerServer] Server P2P attivo e in ascolto sulla porta: " + port);

        try {
            while (running) {
                // Chiamata bloccante in attesa di connessioni da altri nodi sensore
                Socket clientSocket = serverSocket.accept();

                // Delega la gestione a un thread worker dedicato passando archivio e lock condivisi
                PeerClientHandler handler = new PeerClientHandler(clientSocket, downloadLock, localStorage);
                Thread handlerThread = new Thread(handler);
                handlerThread.start();
            }
        } catch (IOException e) {
            // Se running è false, l'eccezione è causata dalla chiusura intenzionale del ServerSocket tramite stop()
            if (running) {
                System.err.println("[PeerServer] Errore nel ciclo di ascolto: " + e.getMessage());
            }
        }
    }

    /**
     * Metodo per la terminazione controllata del server e il rilascio del ServerSocket.
     */
    public void stop() {
        this.running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[PeerServer] Errore durante la chiusura del ServerSocket: " + e.getMessage());
        }
    }
}
