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

## Kvar att leva på (KALP)

Fliken **Kvar att leva på** gör en tillfällig hushållsbudget och ett
räntestresstest. Tryck **Beräkna Kvar att leva på** för en sammanfattning
av inkomster, utgifter, boendekostnader och återstående marginal vid både
avtalad ränta och kalkylränta. Överskott betyder inte att banken beviljar
ett lån; underskott visas som ett negativt belopp.

När ett sparat objekt öppnas hämtas bolån, ränta, amortering, månadsavgift,
driftskostnad och antal låntagare från objektets bostadsuppgifter.
Även aktuella ändringar i Bolån-fliken hämtas när KALP-fliken öppnas.
KALP-fälten kan ändras för att prova olika scenarier. Oförändrade
bostadsuppgifter skriver inte över dessa ändringar vid nästa flikbyte.
Byte av bostadsobjekt återställer KALP-fälten och rensar sammanfattningen.
Ändrade KALP-fält rensar också ett tidigare resultat så att det inte
misstas för en aktuell beräkning.

Nettoinkomst och levnadskostnader måste anges av användaren. Sparad
bruttoinkomst kan inte användas som nettoinkomst: skatt beror bland annat
på kommun, ålder och inkomstslag. Lägg bidrag och andra nettoinkomster
separat och räkna inte samma inkomst två gånger.

### Svenska utgångspunkter och källor

Kontrollerade den 4 oktober 2026:

- [Konsumenternas: kreditprövning och KALP](https://www.konsumenternas.se/lan--betalningar/lan/sa-fungerar-ett-lan/kreditprovning-och-kreditupplysning/):
  KALP omfattar hela hushållets inkomster, lånekostnader, andra skulder,
  drift och levnadskostnader. Bankerna bestämmer själva kalkylränta och
  schabloner. Det finns alltså inte en enda svensk standardtabell.
  Sidans räntesiffra 6,4 % hänvisar till en äldre FI-rapport, inte till
  en gemensam regel för 2026. Appens **7 % är endast ett ändringsbart
  stresstestexempel**; använd bankens aktuella ränta för jämförelse.
- [Konsumentverket: hushållskostnader 2026](https://www.konsumentverket.se/ekonomi/vilka-kostnader-har-ett-hushall/):
  referensvärden beror på ålder och hushållsstorlek och omfattar cirka
  40 % av hushållens utgifter. De är uppskattningar, inte faktisk
  konsumtionsstatistik eller bankens kreditvillkor. Använd egna kostnader
  när de finns, annars aktuella referensvärden för alla vuxna och barn.
  Flikens **Konsumentverkets kostnader** öppnar denna källa.
- [Konsumentverket: förändringar inför 2026](https://www.konsumentverket.se/nyhet/konsumentverkets-hushallskostnader-for-2026-klara/):
  matbudgeten ändrades huvudsakligen genom nya metoder och matsedlar.
  Exemplet två vuxna och barn på 5 och 9 år har matkostnad **8 440 kr/mån**.
  Det är **enbart mat**, inte familjens fullständiga levnadskostnad.
  Därför förifyller appen inte ett godtyckligt hushållsschablonbelopp.
- [Konsumentverket: budget](https://www.konsumentverket.se/ekonomi/budgetkalkylen-att-gora-en-budget/):
  inkomster anges efter skatt. Ta med transport, bil, barnomsorg,
  underhåll, vård, andra lån inklusive CSN och önskat sparande/buffert.
  Konsumentverkets gemensamma belopp inkluderar bland annat el, vatten
  och hemförsäkring: dra bort överlapp om dessa redan ingår i drift eller
  månadsavgift. Komplettera med kostnader som schablonerna inte täcker.
- [Lag (2026:226), särskilt 7 §](https://www.riksdagen.se/sv/dokument-och-lagar/dokument/svensk-forfattningssamling/lag-2026226-om-begransning-av-bostadskrediter_sfs-2026-226/):
  från 1 april 2026 kvarstår minst 1 % årlig amortering över 50 % och
  högst 70 % belåningsgrad, samt 2 % över 70 %. Det tidigare extra
  skuldkvotsbaserade lagkravet ingår inte i lagen. Bolånetaket är 90 %
  vid förvärv och 80 % vid utökning. KALP är inte en kontroll av dessa tak.
  [Konsumenternas om amortering](https://www.konsumenternas.se/lan--betalningar/lan/bolan/amorteringskrav/)
  beskriver även undantag och att banken kan kräva högre amortering.
  Appens befintliga Bolån-flik lämnas oförändrad: KALP importerar dess
  amortering, inklusive eventuell extra amortering, men beloppet är
  redigerbart. Kontrollera det mot bankens faktiska avtal och
  amorteringsunderlag, inte bara nuvarande skuld eller köpeskilling.
- [Inkomstskattelagen, 67 kap. 10 §](https://www.riksdagen.se/sv/dokument-och-lagar/dokument/svensk-forfattningssamling/inkomstskattelag-19991229_sfs-1999-1229/):
  skattereduktion för underskott av kapital är normalt 30 % upp till
  100 000 kr per person och år och 21 % däröver.
  KALP kan valfritt räkna med detta för bostadssäkrat bolån med lika
  räntefördelning och tillräcklig skatt. Andra kapitalposter och
  låneavdrag modelleras inte. Funktionen är avstängd som standard.
  Årsreduktionen delas med tolv; det är inte automatiskt ett månatligt
  kassaflöde. Räkna inte reduktionen igen om den redan ingår i nettoinkomsten
  genom jämkning.
- [Finansinspektionens bolånerapport om 2024, publicerad 2025](https://www.fi.se/sv/publicerat/rapporter/bolanerapport/den-svenska-bolanemarknaden-2024-rapport/):
  hushåll behöver marginaler för fortsatt höga boende- och
  levnadsomkostnader. Avgifter och andra kostnader kan öka även om
  den egna bolåneräntan inte gör det.

### Beräkningsmodell

Alla belopp är per månad utom bolån och årliga räntesatser:

```text
Ränta = bolån × räntesats / 100 / 12
Boendekostnad = ränta - eventuell skattereduktion
               + amortering + månadsavgift + driftskostnad
Kvar = nettoinkomst + bidrag - boendekostnad - levnadskostnader
       - andra lån - transport - barnomsorg/underhåll
       - övriga kostnader - önskat sparande/buffert
```

Samma modell körs med avtalad ränta och kalkylränta. Lånebelopp och
amortering hålls oförändrade för ett konservativt månadsscenario;
Bolån-flikens resultat använder i stället första årets sjunkande skuld.
Resultaten kan därför skilja sig något. KALP tar inte hänsyn till
arbetslöshet, framtida skatteändringar, olika lånedelar eller bankens
individuella kreditprövning.

**KALP-fält och KALP-resultat sparas inte i EDN**, vare sig vid manuell
sparning eller autosparning, och ändrar inte bostadsobjektets uppgifter.

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
