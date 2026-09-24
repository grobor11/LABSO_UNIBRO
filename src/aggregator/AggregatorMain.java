package aggregator;

import java.util.Scanner;

/*
 * Punto di avvio principale dell'Aggregator (Master).
 * Questa classe funge da coordinatore del sistema: inizializza le strutture dati condivise,
 * avvia il server di rete su un thread separato per accettare le connessioni dei nodi in background
 * e mantiene attiva l'interfaccia a riga di comando (CLI) sul thread principale per l'amministratore.
 */
public class AggregatorMain{
    public static void main(String[] args) { 
        // Controllo argomenti della riga di comando: l'unico parametro atteso è la porta del server
        if (args.length != 1) {
            System.out.println("Devi usare: java AggregatorMain <porta>");
            System.exit(1); // Chiusura del programma con errore
        }
        
        // Parsing dell'argomento passato da terminale in un altro intero (la porta del server)
        int port = Integer.parseInt(args[0]);

        // Creazione del registro delle risorse e del gestore dei log 
        ResourceRegistry registry = new ResourceRegistry();
        DownloadLogManager logManager = new DownloadLogManager();

        // Creazione e avvio del server AggregatorServer in un thread separato
        // Fondamemtale per permettere al server di accettare connessioni in parallelo all'interfaccia CLI
        AggregatorServer server = new AggregatorServer(port, registry, logManager);
        Thread serverThread = new Thread(server);
        serverThread.start();

        // Avvio dell'interfaccia a riga di comando 
        runCLI(registry, logManager, serverThread);

    }

    private static void runCLI(ResourceRegistry registry, DownloadLogManager logManager, Thread serverThread) {
        Scanner scan = new Scanner(System.in);

        // Ciclo infinito per mantenere CLI interattiva e sempre in ascolto
        while (true) {
            System.out.println("Comandi disponibili: ");
            System.out.println("1. listdata");
            System.out.println("2. log");
            System.out.println("3. quit");

            // Lettura dell'input utente e rimozione eventuali spazi bianchi iniziali e finali
            String command = scan.nextLine().trim();

            // Switch case per gestire i comandi inseriti dall'utente
            switch(command) {
                case "listdata":

                    // Comando per visuallizzare tutte le risorse registrate 
                    // registry.getGlobalList() restituisce una Mappa di tutte le risorse 
                    // Usiamo "var" così il compilatore capisce in automatico che il tipo è Map<String, Set<PeerInfo>>.
                    var mappa = registry.getGlobalList();
                    if (mappa.isEmpty()) {
                        System.out.println("Nessuna risorsa registrata al momento.");
                    } else {

                        // Iteriamo sull'intero insieme (entryset) delle coppie chiave-valore della mappa
                        //entry.getKey() restituisce il nome della risorsa, entry.getValue() restituisce l'insieme dei nodi che la possiedono.
                        for (var entry : mappa.entrySet()) {
                            System.out.println("File: " + entry.getKey() + " -> Posseduto da: " + entry.getValue());
                        }
                    }
                    break;

                case "log":

                    // Comando per visualizzare lo storico dei download avvenuti tra i nodi sensore.
                    // logManager.getLogs() restituisce una List di stringhe formattate.
                    var logList = logManager.getLogs();

                    // Verifichiamo se lo storico è vuoto
                    if (logList.isEmpty()) {
                        System.out.println("Nessun download registrato.");
                    } else {

                        // Usiamo ciclo for-each per stampare ogni log registrato
                        for (String log : logList) {
                            System.out.println(log);
                        }
                    }
                    break;

                case "quit":

                    // Comando per spegnere in modo sicuro l'Aggregator.
                    System.out.println("Chiusura del server...");
                    serverThread.interrupt(); // Invia il segnale di spegnimento al ciclo while
                    
                    try{
                        serverThread.join(); //Attende che l'AggregatorServer finisca di chiudere tutte le connessioni e termini il thread
                    } catch (InterruptedException e) {
                        System.out.println("Chiusura forzata");
                    }
                    scan.close(); //Chiude lo scanner per liberare le risorse
                    return; // Esce dal main e termina il programma
                    
                default:

                    // Se l'utente digita una parola non prevista, il server non va in crash ma mostra un messaggio di errore.
                    System.out.println("Comando non riconosciuto. Usa uno dei comandi disponibili.");
                    break;
            }

        }
    }
}


    

