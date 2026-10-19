# Pubblicare un'app su Google Play — procedura completa

Procedura seguita per Nav2Pilot il 19 ottobre 2026, scritta per rifarla su un'altra app (ad esempio GrooveJam).
Le risposte indicate sono quelle di Nav2Pilot: per un'altra app vanno ricontrollate.

Questa cartella `docs/` è pubblicata su GitHub Pages: qui non vanno mai chiavi, token o password.

## Il percorso in breve

1. Account di servizio per gli upload automatici (una volta sola per tutto l'account sviluppatore).
2. Creazione dell'app in Play Console e primo upload a mano.
3. Test interno: release subito disponibile per massimo 100 tester, senza revisione.
4. Configurazione dell'app: privacy, dichiarazioni, scheda dello Store.
5. Test chiuso: richiede la revisione di Google.
6. Produzione: per un account personale nuovo serve prima un test chiuso con almeno 12 tester per 14 giorni.

## 1. Account di servizio (upload automatico)

Lo script `scripts/build-release.sh` carica l'AAB su Play con un account di servizio Google Cloud.
Va configurato una volta e vale per tutte le app dell'account sviluppatore.

1. In Google Cloud crea un progetto (oggi è `cristianku-play`) e abilita **Google Play Android Developer API**.
2. In **IAM e amministrazione → Account di servizio** crea l'account, poi in **Chiavi** crea una chiave JSON.
3. Salva la chiave in `.signing/play-service-account.json`. Il file è ignorato da git: non va mai committato.
4. In Play Console apri **Utenti e autorizzazioni → Invita nuovi utenti** e inserisci l'email dell'account di servizio
   (`...@<progetto>.iam.gserviceaccount.com`). In **Autorizzazioni app** aggiungi le app e dai almeno
   **Rilascia app nei canali di test** e **Gestisci canali di test**, oppure **Amministrazione**.
   Non arriva nessuna email da accettare: l'accesso è subito attivo, ma può servire qualche minuto.

Attenzione: **eliminare il progetto Google Cloud invalida subito tutte le sue chiavi**.
Un progetto eliminato si può ripristinare entro 30 giorni da **IAM e amministrazione → Gestisci risorse → Risorse in attesa di eliminazione**.

Errori visti:

| Errore dello script | Causa | Rimedio |
|---|---|---|
| `403 ... Project #... has been deleted` | la chiave appartiene a un progetto eliminato | ripristina il progetto o crea una chiave nuova |
| `403 The caller does not have permission` | l'account di servizio non è invitato in Play Console | punto 4 qui sopra |

## 2. Creare l'app e primo upload

1. Play Console → **Crea app**: nome, lingua predefinita, app o gioco, gratuita o a pagamento.
2. **Il primo AAB va caricato a mano** (l'API non può creare la prima release):
   **Test e release → Test → Test interni → Crea nuova release → Carica**.
3. Dalle versioni successive basta `scripts/build-release.sh`: crea il tag `v<versione>`, lo pusha e carica l'AAB
   sul canale `internal`. Con `--track=alpha` va direttamente nel test chiuso.

Lo script usa JDK 17 (`/usr/libexec/java_home -v 17`). Il Java di sistema (IBM Semeru 26) manda in crash
Gradle: per lanciare i test a mano usa `JAVA_HOME="$(/usr/libexec/java_home -v 17)" ./gradlew testDebugUnitTest`.

## 3. Test interno (prova immediata sul telefono)

1. **Test interni → Tester → Crea una mailing list**. Dopo aver scritto ogni email **premi Invio**, finché
   non compare sotto "Indirizzi email aggiunti", poi salva.
2. Spunta la lista e clicca **Salva** in basso.
3. **Copia link** e aprilo sul telefono con lo stesso account Google della lista: **Accetta → Scarica su Google Play**.

Note:
- Le mailing list sono dell'account sviluppatore: si creano una volta e si riusano in ogni app e canale.
- Serve un account Google, non per forza Gmail. Deve essere quello usato sul Play Store del telefono.
- Finché la configurazione non è completa, l'app compare come `com.cristianku.<nome> (unreviewed)`.

Confronto con Apple: il test interno equivale ai tester interni di TestFlight, ma su Play non serve che il
tester faccia parte del team. Il test chiuso o aperto equivale ai tester esterni, con revisione.

## 4. Configurazione dell'app

Dalla dashboard, **Completa la configurazione dell'app → Visualizza attività**. Si fa una volta sola per app,
non a ogni release.

### Norme sulla privacy

Serve un URL pubblico e raggiungibile, in una pagina web (non un PDF).
Per Nav2Pilot la pagina è `docs/privacy/index.html`, pubblicata con GitHub Pages:

```bash
gh api -X POST repos/cristianku/<repo>/pages -f "source[branch]=main" -f "source[path]=/docs"
curl -s -o /dev/null -w "%{http_code}\n" https://cristianku.github.io/<repo>/privacy/   # deve dare 200
```

La prima build di Pages può restare in coda diversi minuti. Se Play ha già segnalato il 404, nel dettaglio del
problema spunta **Ho risolto i problemi relativi alla mia pagina → Salva e continua → Contrassegna come risolto**.

### Dettagli di accesso

Google deve poter provare tutta l'app. Se l'app richiede un login o un token:

- non dare mai un token personale reale (quello comma dà accesso al dispositivo e alla posizione dell'auto);
- serve una **modalità demo**. In Nav2Pilot il token `DEMO` crea un account e un'auto finti senza contattare
  comma (`transport/DemoAccount.kt`, dalla versione 1.0.15).

Nel modulo: **Sì, parti dell'app sono soggette a limitazioni → Aggiungi dettagli**:
- Nome: `Demo account`.
- Password: `DEMO`.
- Istruzioni (in inglese, massimo 500 caratteri): dove inserirlo (Devices → Add token) e cosa si può provare.
- Spunta la casella "le credenziali garantiscono l'accesso completo", obbligatoria: deve essere vera.

Il codice demo deve essere già nella release inviata in revisione, altrimenti i revisori trovano un errore.

### Altre dichiarazioni (risposte di Nav2Pilot)

| Voce | Risposta |
|---|---|
| Annunci | nessuna pubblicità |
| Classificazione contenuti | categoria "Tutti gli altri tipi di app", tutte le risposte "No" (anche contenuto online e condivisione della posizione) |
| Pubblico di destinazione | 18 o più, senza limitare l'accesso ai minorenni |
| Sicurezza dei dati | da compilare in base ai dati realmente raccolti e inviati; deve combaciare con la pagina della privacy |
| App governative, funzionalità finanziarie, salute | No |
| Categoria | App · Mappe e navigatori, più email di contatto |

### Scheda dello Store

- Nome: massimo 30 caratteri. Descrizione breve: massimo 80. Descrizione completa: massimo 4000.
- Se l'app nomina un marchio altrui, aggiungi una riga di non affiliazione
  ("not affiliated with or endorsed by comma.ai").

Immagini richieste:

| Asset | Misura | Come è stato fatto per Nav2Pilot |
|---|---|---|
| Icona | 512×512 PNG | `sips -z 512 512 AppIcon.png --out icon-512.png` dall'icona iOS 1024×1024 |
| Immagine in primo piano | 1024×500 | icona 500×500 centrata su sfondo `#010E30` con `sips -p 500 1024 --padColor 010E30` |
| Screenshot telefono | da 2 a 8, 16:9 o 9:16, lato tra 320 e 3840 px | screenshot del README portati a 1080×1920 (`--resampleHeight 1920`, poi `-p 1920 1080 --padColor 000000`) |

Per comparire nelle promozioni servono almeno 4 screenshot con almeno 1080 px per lato (facoltativo).

Come funziona il caricamento: **Aggiungi asset** apre la raccolta a destra. Si caricano tutti i file lì, poi si
seleziona quello giusto per il riquadro su cui si è cliccato e si conferma con **Avanti**. Gli asset con il
simbolo ⊘ hanno misure sbagliate per quel riquadro.

**Dichiarazione asset AI**: se icona o immagine in primo piano sono generate con l'AI, etichettale come tali.

## 5. Test chiuso

Si sblocca quando la configurazione dell'app è completa.

1. **Test chiusi → Alpha → Gestisci canale**:
   - **Paesi/regioni**: aggiungi i paesi dei tester.
   - **Tester**: scegli la mailing list.
2. **Crea nuova release → Aggiungi dalla raccolta** e scegli l'AAB già caricato dallo script (niente nuovo upload).
   **Avanti → Salva**.
3. **Panoramica della pubblicazione**: Google esegue controlli rapidi (fino a 14 minuti). Se ci sono problemi
   bloccanti compare un riquadro rosso: **Visualizza problemi**, correggi, poi **Invia N modifiche per la revisione**.
4. La revisione di solito richiede da qualche ora a 7 giorni.

Per sostituire una release già in revisione basta crearne una nuova sullo stesso canale e inviarla.

## 6. Accesso alla produzione

Per gli account sviluppatore personali nuovi, la Dashboard → **Produzione** richiede:

1. una release di test chiuso pubblicata;
2. almeno **12 tester** che hanno accettato l'invito al test chiuso;
3. il test chiuso attivo con quei 12 tester per **almeno 14 giorni consecutivi**.

Gli aggiornamenti della release durante i 14 giorni non azzerano il conteggio. Poi si usa
**Richiedi per la produzione** e si risponde ad alcune domande sul test.

## Checklist per una nuova app

- [ ] App creata in Play Console, primo AAB caricato a mano nel test interno
- [ ] Account di servizio invitato con i permessi per la nuova app
- [ ] Mailing list tester assegnata al test interno, link provato sul telefono
- [ ] Pagina della privacy pubblica (risposta 200) e URL inserito
- [ ] Dettagli di accesso: modalità demo nell'app, se c'è un login o un token
- [ ] Annunci, classificazione, pubblico, sicurezza dei dati, app governative, finanza, salute
- [ ] Categoria ed email di contatto
- [ ] Scheda dello Store: testi, icona, immagine in primo piano, screenshot, dichiarazione AI
- [ ] Test chiuso: paesi, tester, release dalla raccolta, invio in revisione
- [ ] 12 tester attivi per 14 giorni, poi richiesta di produzione
