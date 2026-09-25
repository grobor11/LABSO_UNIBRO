package client;

import common.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

/*
  La classe PeerClientHandler gestisce il trasferimento di una rilevazione verso un altro nodo della rete.
  Implementa l'interfaccia Runnable per soddisfare il requisito di utilizzo del multithreading, 
  permettendo al nodo di gestire molteplici connessioni simultaneamente senza bloccare il server in ascolto.
 */
public class PeerClientHandler implements Runnable {

    // Socket dedicato alla comunicazione punto-a-punto con il nodo richiedente.
    private final Socket peerSocket;
    
    // Oggetto condiviso utilizzato come monitor per il costrutto di sincronizzazione.
    // Garantisce che l'accesso alle risorse locali avvenga in mutua esclusione.
    private final Object downloadLock; 
    
    // Archivio locale delle rilevazioni del nodo.
    private final LocalStorage localStorage;

    public PeerClientHandler(Socket peerSocket, Object downloadLock, LocalStorage localStorage) {
        this.peerSocket = peerSocket;
        this.downloadLock = downloadLock;
        this.localStorage = localStorage;
    }

    @Override
    public void run() {
        // Utilizzo del try-with-resources per garantire la chiusura sicura degli stream di I/O 
        // e prevenire memory leak anche in caso di disconnessioni anomale.
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
            // L'autoflush (true) è abilitato per forzare l'invio immediato dei messaggi del protocollo testuale
            PrintWriter out = new PrintWriter(new OutputStreamWriter(peerSocket.getOutputStream()), true)
        ) {
            
            // Il protocollo P2P usa una singola riga: DOWNLOAD <nome-risorsa>.
            String request = in.readLine();
            String[] tokens = request == null ? new String[0] : request.trim().split("\\s+");

            if (tokens.length == 2 && tokens[0].equals(Protocol.PEER_DOWNLOAD_REQUEST)) {
                String resourceName = tokens[1];
                System.out.println("[PeerHandler] Ricevuta richiesta di download per: " + resourceName);

                // SEZIONE CRITICA E MUTUA ESCLUSIONE
                // Come da specifiche, le richieste verso un nodo devono avvenire in modo mutualmente esclusivo.
                // Il blocco synchronized mette automaticamente in attesa i thread concorrenti, 
                // garantendo che il nodo serva una sola richiesta per volta senza generare errori.
                synchronized (downloadLock) {
                    
                    String content = localStorage.getData(resourceName);
                    if (content != null) {
                        out.println(Protocol.PEER_DATA + " " + content);
                        System.out.println("[PeerHandler] Download completato per: " + resourceName);
                    } else {
                        out.println(Protocol.NOT_FOUND);
                        System.out.println("[PeerHandler] Risorsa non trovata: " + resourceName);
                    }
                } // Rilascio del lock: il thread successivo in coda può procedere
                
            } else {
                // Gestione di comandi malformati o non aderenti al protocollo
                out.println(Protocol.ERROR);
            }
            
        } catch (IOException e) {
            System.err.println("[PeerHandler] Errore di comunicazione I/O con il peer: " + e.getMessage());
        } finally {
            // Chiusura garantita del Socket per rilasciare le risorse di rete del sistema operativo
            try {
                if (!peerSocket.isClosed()) {
                    peerSocket.close();
                }
            } catch (IOException e) {
                System.err.println("[PeerHandler] Errore durante la chiusura del Socket: " + e.getMessage());
            }
        }
    }
}
