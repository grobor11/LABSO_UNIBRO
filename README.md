# LABSO_UNIBRO
# Sistema Distribuito per la Condivisione di Rilevazioni

Progetto per l'esame di **Laboratorio di Sistemi Operativi**  
Corso di Laurea in Informatica per il Management — **Università di Bologna**  
Anno Accademico 2025/2026

---

## Membri del Gruppo (UNIBRO)

* **Gregorio Volpato** (Referente) - Matricola: `0001162977`
* **Riccardo Coppola** - Matricola: `0001160337`
* **Luca Vagnoni** - Matricola: `0001175133`

---

## Panoramica del Progetto

Il sistema realizza una rete distribuita ibrida Client-Server / Peer-to-Peer per la condivisione di rilevazioni ambientali (es. temperature, sensori):
* **Aggregator (Master/Server):** funge da registro centrale delle risorse disponibili sulla rete, coordina i permessi di download e memorizza lo storico dei trasferimenti.
* **Nodi Sensore (Client/Peer):** mantengono un archivio locale di dati, interagiscono con l'Aggregator per registrarsi e scoprire risorse remote, e si connettono direttamente ad altri nodi (P2P) per scaricare i file in mutua esclusione.

---

## Struttura del Repository

```text
.
├── src/
│   ├── common/             # Classi e costanti condivise di protocollo e rete
│   │   ├── PeerInfo.java
│   │   └── Protocol.java
│   ├── aggregator/         # Applicazione Server centrale (Master)
│   │   ├── AggregatorMain.java
│   │   ├── AggregatorServer.java
│   │   ├── ClientHandler.java
│   │   ├── ResourceRegistry.java
│   │   └── DownloadLogManager.java
│   └── client/             # Applicazione Nodo Sensore (Client & Peer)
│       ├── ClientMain.java
│       ├── CommandHandler.java
│       ├── LocalStorage.java
│       ├── PeerServer.java
│       ├── PeerClientHandler.java
│       └── RobustDownloader.java
└── README.md
```
## Compilazione

Dalla cartella radice del progetto, eseguire il comando di compilazione a seconda del terminale utilizzato:

### Su Windows (PowerShell):
```powershell
$javaFiles = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac $javaFiles
```

### Su Linux / macOS / Bash:
```bash
javac src/*/*.java
```

I file bytecode compilati (`.class`) verranno generati all'interno delle rispettive cartelle dei package in `src/`

---

## Esecuzione

### 1. Avvio dell'Aggregatore (Server)
Aprire un terminale ed eseguire l'applicazione server specificando la porta TCP d'ascolto (es. `9000`)

```bash
java -cp src aggregator.AggregatorMain 9000
```

**Comandi disponibili da console (Aggregatore):**
* `listdata` : elenca tutte le rilevazioni registrate sulla rete e i rispettivi peer che le possiedono
* `log` : mostra lo storico di tutti i trasferimenti eseguiti (con esito `COMPLETATO` o `FALLITO`)
* `quit` : chiude le connessioni attive e arresta l'aggregatore

---

### 2. Avvio dei Nodi Sensore (Client)
Aprire uno o più terminali separati per avviare ciascun nodo sensore, indicando l'indirizzo IP e la porta dell'Aggregatore:

```bash
java -cp src client.ClientMain 127.0.0.1 9000
```
*(Se l'aggregatore si trova su un'altra macchina in rete locale, sostituire `127.0.0.1` con l'indirizzo IP effettivo dell'host su cui gira l'aggregatore).*

**Comandi disponibili da console (Nodo Sensore):**
* `listdata local` : elenca le rilevazioni presenti nell'archivio locale del nodo.
* `listdata remote` : interroga l'aggregatore e visualizza le rilevazioni disponibili sull'intera rete con i relativi peer.
* `add <nome_risorsa> <contenuto>` : salva una rilevazione localmente e ne notifica la disponibilità all'aggregatore.
* `download <nome_risorsa>` : avvia il protocollo robusto di download (richiesta del token, connessione diretta P2P con timeout, salvataggio locale e notifica dell'esito).
* `quit` : segnala la disconnessione volontaria all'aggregatore, arresta il server P2P locale e chiude il nodo.

---

## Documentazione

La relazione tecnica dettagliata, comprensiva dell'analisi architetturale, della gestione della concorrenza e dei thread, dei test eseguiti e delle scelte implementative, è consultabile nel file allegato alla consegna: **`DOCUMENTAZIONE.pdf`**.
