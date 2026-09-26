package aggregator;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

/*
 * Rappresenta il server di rete dell'Aggregator (il "centralinista").
 * Questa classe viene eseguita in un thread separato in background.
 * Il suo unico scopo è rimanere costantemente in ascolto di nuove connessioni,
 * per poi delegare la comunicazione vera e propria a thread dedicati (ClientHandler).
 */

public class AggregatorServer implements Runnable {
    private int port;

    // Riferimenti alle strutture dati condivise tra i thread: registri risorse e log dei download.
    private ResourceRegistry registry;
    private DownloadLogManager logManager;
    
    //Lista per tenere traccia dei thread figli attivi
    private ArrayList<Thread> children = new ArrayList<>();
    private ArrayList<ClientHandler> handlers = new ArrayList<>();

    // Il costruttore riceve i parametri dal main e li salva nelle variabili di istanza.
    public AggregatorServer(int port, ResourceRegistry registry, DownloadLogManager logManager) {

        this.port = port;
        this.registry = registry;
        this.logManager = logManager;
    }
    
    @Override
    public void run() {

        // Costrutto "try-with-resources": dichiariamo il ServerSocket qui dentro
        // in modo che venga chiuso automaticamente e in modo sicuro quando il blocco termina,
        // anche in caso di eccezioni.
        try (ServerSocket serverSocket = new ServerSocket(port)) {

            // Imposta un timeout per non rendere l'accept() bloccante all'infinito
            serverSocket.setSoTimeout(5000);
            System.out.println("Server in ascolto sulla porta: " + port);


            // Condizione che permette di fermare il ciclo tramite interrupt
            while (!Thread.interrupted()) {
                try {
                // Il metodo accept() è BLOCCANTE: il thread rimane fermo qui finché
                // un  nodo remoto non si connette effettivamente a questa porta.
                Socket clientSocket = serverSocket.accept();
                if (!Thread.interrupted()) {
                // Appena un nodo si connette, creiamo un nuovo thread per gestire la comunicazione con quel nodo,
                // e gli passiamo il socket appena aperto e le memorie condivise (registry e logManager)
                ClientHandler handler = new ClientHandler(clientSocket, registry, logManager);

                // Avviamo il gestore del nodo remoto in un thread nuovo ("fire and forget").
                // Così questo ciclo while può ricominciare subito ad aspettare il prossimo nodo.
                Thread handlerThread = new Thread(handler);
                this.children.add(handlerThread);
                this.handlers.add(handler);
                handlerThread.start();
               
            } else {
                // Se il thread è stato interrotto, chiudiamo il socket appena accettato
                clientSocket.close();
                break; // Uscita dal ciclo while
            }
            } catch (SocketTimeoutException e) {
                // Il timeot scatta, ignoriamo l'errore e il ciclo while ricontrolla la condizione 
                continue;
            
            } catch (IOException e) {
            break; // Altre condizioni di I/O causano l'uscita dal ciclo while e la chiusura del server
        }
    }
} catch (IOException e) {
            System.err.println("Errore nel server di rete: " + e.getMessage());
        }

        System.out.println("Interruzione dei thread client ");
        for (ClientHandler handler : this.handlers) {
            handler.closeConnection();
        }

        for (Thread child : this.children) {
            child.interrupt();
            try {
                child.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
