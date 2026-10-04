# Svensk bolånekalkylator

En skrivbordsapplikation i Clojure för att beräkna kostnader för svenska
bolån. Kalkylatorn visar bland annat lånebelopp, belåningsgrad, ränta,
amortering, skattereduktion och total månadskostnad.

Varje klick på **Beräkna bolån** visar en läsbar sammanställning i ett
separat fönster, centrerat och mindre än huvudfönstret. Huvudfönstret är
låst tills sammanställningen stängs med **Stäng**, Escape eller fönstrets
stängningsknapp. Resultattexten i huvudfönstret finns kvar och uppdateras
som tidigare. Vid ogiltiga uppgifter visas ett fel i stället.
Med **Kopiera till urklipp** kopieras sammanställningens exakta text och
formaterade belopp som ren text. Bekräftelsen **Sparad till urklipp!**
visas i resultatfönstret, som förblir öppet.

Flera bostadsobjekt kan sparas lokalt med adress, kommentar, annonslänk,
inmatade värden och beräknat resultat. Sparade objekt kan sedan väljas och
öppnas från programmets objektmeny.
Ändringar i ett öppnat, sparat objekt sparas automatiskt var femte sekund.

## Köra programmet

Java och [Leiningen](https://leiningen.org/) krävs.

```shell
lein run
```

Kör testerna med:

```shell
lein test
```

Sparade objekt lagras utanför Git-arkivet i användarens lokala
applikationsdata.

## Licens

Copyright (C) 2026 Johan Andersson.

Projektet distribueras under [GNU General Public License version 3](LICENSE.md).
