package client;

import common.Protocol;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

/*
 * Punto di avvio del Nodo Sensore.
 * Questa classe si occupa dell'inizializzazione del nodo, stabilendo la connessione
 * con l'Aggregator centrale e avviando due flussi paralleli (multithreading):
 * 1. Il CommandHandler (CLI) per far inserire i comandi all'utente.
 * 2. Il ServerSocket P2P per accettare le richieste di download dagli altri nodi.
 */
public class ClientMain {
    public static void main(String[] args) {

        // Lettura degli argomenti da riga di comando
        // Le specifiche richiedono di passare IP e porta dell'Aggregator all'avvio.
        if (args.length != 2) {
            System.out.println("Errore di sintassi, devi usare: java ClientMain <ip_aggregator> <porta_aggregator>");
            System.exit(1);
        }

        String aggregatorIP = args[0];
        int aggregatorPort = Integer.parseInt(args[1]);

        // Creazione della memoria condivisa per le risorse locali del nodo
        // Essendo thread-safe (synchronized), può essere letta dalla CLI e dal server P2P senza conflitti
        LocalStorage localStorage = new LocalStorage();

        try {
            // Creazione del server P2P del nodo. PeerServer condivide il lock
            // tra tutti gli handler delle richieste in ingresso.
            PeerServer peerServer = new PeerServer(0, localStorage);
            int myP2pPort = peerServer.getPort();
            String myIp = InetAddress.getLocalHost().getHostAddress(); // Recuperiamo l'IP locale del nodo

            // Connessione all'aggregator centrale
            Socket aggregatorSocket = new Socket(aggregatorIP, aggregatorPort);
            BufferedReader aggIn = new BufferedReader(new InputStreamReader(aggregatorSocket.getInputStream()));
            PrintWriter aggOut = new PrintWriter(aggregatorSocket.getOutputStream(), true);

            // Registrazione del nodo sull'aggregator
            // Inviamo il comando REGISTER seguito dal nostro IP e dalla porta del nostro mini server
            aggOut.println(Protocol.REGISTER_NODE + " " + myIp + " " + myP2pPort);
            String response = aggIn.readLine();

            if (response == null || !response.startsWith(Protocol.SUCCESS)) {
                System.err.println("Errore: Registazione rifiutata dall'aggregator.");
                System.exit(1);
            }

            System.out.println("Connessione all'aggregator avvenuta con successo!");
            System.out.println("La tua porta P2P locale e': " + myP2pPort);

            Thread peerThread = new Thread(peerServer, "peer-server");
            peerThread.start();

            //Inizializzazione ed avvio del CommandHandler (Interfaccia utente)
            RobustDownloader downloader = new RobustDownloader(localStorage, aggIn, aggOut);
            CommandHandler cliHandler = new CommandHandler(localStorage, aggregatorSocket, aggIn, aggOut, downloader, peerServer);

            //Avviamo la CLI in un thread separato così non blocca l'ascolto delle connessioni in arrivo
            Thread cliThread = new Thread(cliHandler);
            cliThread.start();

        } catch (IOException e) {
            System.err.println("Impossibile connettersi all'Aggregator (" + aggregatorIP + ":" + aggregatorPort + "). Verificare che sia acceso.");
            System.exit(1);
        }

        
    }

}
